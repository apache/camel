/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.semantic.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.semantic.SemanticMetricSource;
import org.apache.camel.semantic.SemanticMonitoringEvent;
import org.apache.camel.semantic.SemanticMonitoringSnapshot;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonObject;

/** Bounded read-only projection of supplied measurements, shared by all surfaces. */
public final class SemanticMonitoringService extends EventNotifierSupport {
    private static final Object CREATION_LOCK = new Object();
    private static final int CAPACITY = 100;
    private static final int MAX_SOURCES = 32;
    private final Map<String, SemanticMetricSource> sources = new LinkedHashMap<>();
    private final Map<String, Cached> latest = new LinkedHashMap<>();
    private final List<EventNotifier> ownedNotifiers = new ArrayList<>();
    private final List<ObjectName> mbeans = new ArrayList<>();
    private final AtomicLong sourceErrors = new AtomicLong();
    private final AtomicLong evicted = new AtomicLong();
    private final AtomicLong managementErrors = new AtomicLong();
    private ScheduledExecutorService sampler;
    private volatile String lastSample;
    private final Map<String, Map<String, Object>> sourceHealth = new ConcurrentHashMap<>();
    private volatile int omittedSources;

    private record Cached(String owner, SemanticMonitoringSnapshot snapshot) {
    }

    private SemanticMonitoringService(CamelContext context) {
        setupIgnore(true);
        setCamelContext(context);
    }

    public static SemanticMonitoringService get(CamelContext context) {
        synchronized (CREATION_LOCK) {
            var extension = context.getCamelContextExtension();
            var service = extension.getContextPlugin(SemanticMonitoringService.class);
            if (service == null) {
                service = new SemanticMonitoringService(context);
                extension.addContextPlugin(SemanticMonitoringService.class, service);
                try {
                    context.addService(service, true, true);
                } catch (Exception failure) {
                    throw new IllegalStateException("Cannot start semantic monitoring", failure);
                }
            }
            return service;
        }
    }

    @Override
    protected void doStart() throws Exception {
        CamelContext context = getCamelContext();
        sources.clear();
        sourceHealth.clear();
        synchronized (this) {
            latest.clear();
        }
        lastSample = null;
        var availableSources = context.getRegistry().findByTypeWithName(SemanticMetricSource.class);
        omittedSources = Math.max(0, availableSources.size() - MAX_SOURCES);
        availableSources.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(MAX_SOURCES)
                .forEach(entry -> sources.put(entry.getKey(), entry.getValue()));
        for (var source : sources.values()) {
            if (source instanceof EventNotifier notifier
                    && !context.getManagementStrategy().getEventNotifiers().contains(notifier)) {
                if (notifier instanceof CamelContextAware aware) {
                    aware.setCamelContext(context);
                }
                try {
                    ServiceHelper.startService(notifier);
                    context.getManagementStrategy().addEventNotifier(notifier);
                    ownedNotifiers.add(notifier);
                } catch (Exception | AssertionError | LinkageError failure) {
                    sourceErrors.incrementAndGet();
                }
            }
        }
        context.getManagementStrategy().addEventNotifier(this);
        sample();
        if (!sources.isEmpty()) {
            sampler = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "SemanticMetricSnapshots");
            sampler.scheduleWithFixedDelay(this::sample, 1, 1, TimeUnit.SECONDS);
        }
        context.addStartupListener((started, alreadyStarted) -> {
            manage("all", null);
            for (String source : sources.keySet()) {
                manage("Source(" + source + ")", source);
            }
        });
    }

    private void manage(String name, String source) {
        var agent = getCamelContext().getManagementStrategy().getManagementAgent();
        if (agent == null) {
            return;
        }
        try {
            ObjectName objectName = new ObjectName(
                    agent.getMBeanObjectDomainName() + ":context="
                                                   + ObjectName.quote(getCamelContext().getManagementName())
                                                   + ",type=semantic,name=" + ObjectName.quote(name));
            if (mbeans.contains(objectName)) {
                return;
            }
            SnapshotMBean facade = () -> new JsonObject(snapshot(source)).toJson();
            agent.register(new StandardMBean(facade, SnapshotMBean.class), objectName);
            mbeans.add(objectName);
        } catch (Exception failure) {
            managementErrors.incrementAndGet();
        }
    }

    /** JMX deliberately exposes attributes only: reading the cache never changes collection. */
    public interface SnapshotMBean {
        String getSnapshot();
    }

    private void sample() {
        for (var entry : sources.entrySet()) {
            try {
                List<SemanticMonitoringSnapshot> values = entry.getValue().snapshots();
                if (values.size() > 64) {
                    throw new IllegalArgumentException("Metric table limit");
                }
                values = List.copyOf(values);
                synchronized (this) {
                    // Each source supplies its complete current set. Retired series must not linger as live data.
                    latest.entrySet().removeIf(cached -> entry.getKey().equals(cached.getValue().owner));
                    for (var snapshot : values) {
                        accept(entry.getKey(), snapshot);
                    }
                }
                lastSample = Instant.now().toString();
                sourceState(entry.getKey(), values.isEmpty() ? "no_snapshots" : "available", "lastSuccess", lastSample);
            } catch (Exception | AssertionError | LinkageError failure) {
                sourceErrors.incrementAndGet();
                sourceState(entry.getKey(), "sample_failed", "lastFailure", Instant.now().toString());
            }
        }
    }

    private void sourceState(String name, String state, String field, String time) {
        updateHealth(sourceHealth, name, state, field, time);
    }

    private static void updateHealth(
            Map<String, Map<String, Object>> health, String name, String state, String field, String time) {
        health.compute(name, (key, previous) -> {
            Map<String, Object> value = new LinkedHashMap<>(previous == null ? Map.of() : previous);
            value.put("state", state);
            value.put(field, time);
            return Map.copyOf(value);
        });
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof SemanticMonitoringEvent;
    }

    @Override
    public void notify(CamelEvent event) {
        if (event instanceof SemanticMonitoringEvent monitoring) {
            accept(monitoring.getSnapshot().getSource(), monitoring.getSnapshot());
        }
    }

    private synchronized void accept(String owner, SemanticMonitoringSnapshot snapshot) {
        String key = owner + "\u0000" + snapshot.getSource() + "\u0000" + snapshot.getId();
        if (!latest.containsKey(key) && latest.size() == CAPACITY) {
            latest.remove(latest.keySet().iterator().next());
            evicted.incrementAndGet();
        }
        latest.put(key, new Cached(owner, snapshot));
    }

    /** Consistent transient read of the latest measurements; no historical query or backend I/O. */
    public Map<String, Object> snapshot(String owner) {
        List<Map<String, Object>> values;
        synchronized (this) {
            values = latest.values().stream()
                    .filter(value -> owner == null || owner.equals(value.owner) || owner.equals(value.snapshot.getSource()))
                    .map(value -> value.snapshot.toMap()).toList();
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("snapshots", values);
        answer.put("scope", "latest runtime measurements");
        answer.put("sources", Map.copyOf(sourceHealth));
        answer.put("omittedSources", omittedSources);
        answer.put("freshness", "See source lastSuccess/lastFailure and supplied observation times");
        answer.put("sourceErrors", sourceErrors.get());
        answer.put("evicted", evicted.get());
        answer.put("managementErrors", managementErrors.get());
        answer.put("capacity", CAPACITY);
        answer.put("sampleIntervalSeconds", 1);
        if (lastSample != null) {
            answer.put("lastSample", lastSample);
        }
        return answer;
    }

    @Override
    protected void doStop() throws Exception {
        CamelContext context = getCamelContext();
        if (sampler != null) {
            context.getExecutorServiceManager().shutdownNow(sampler);
            sampler = null;
        }
        context.getManagementStrategy().removeEventNotifier(this);
        for (var notifier : ownedNotifiers) {
            context.getManagementStrategy().removeEventNotifier(notifier);
            ServiceHelper.stopService(notifier);
        }
        ownedNotifiers.clear();
        var agent = context.getManagementStrategy().getManagementAgent();
        if (agent != null) {
            for (var objectName : mbeans) {
                try {
                    agent.unregister(objectName);
                } catch (Exception failure) {
                    managementErrors.incrementAndGet();
                }
            }
        }
        mbeans.clear();
        synchronized (this) {
            latest.clear();
        }
        sourceHealth.clear();
        lastSample = null;
    }
}
