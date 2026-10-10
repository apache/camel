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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.semantic.LoggingSemanticAuditSink;
import org.apache.camel.semantic.MemorySemanticAuditStore;
import org.apache.camel.semantic.SemanticAuditConfiguration;
import org.apache.camel.semantic.SemanticAuditReader;
import org.apache.camel.semantic.SemanticAuditRecord;
import org.apache.camel.semantic.SemanticAuditSink;
import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticObserver;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal context-owned dispatcher. This lifecycle plumbing is not a supported application or backend API. */
public final class SemanticAuditService extends ServiceSupport {
    public static final String REFERENCES = "CamelSemanticAuditReferences";
    public static final String REQUEST = "CamelSemanticAuditRequest";
    private static final Logger LOG = LoggerFactory.getLogger(SemanticAuditService.class);
    private static final Object CREATION_LOCK = new Object();
    private final CamelContext context;
    private volatile SemanticAuditConfiguration configuration = SemanticAuditConfiguration.DISABLED;
    private String configurationSource;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong observerErrors = new AtomicLong();
    private final Map<String, AtomicLong> sinkErrors = new LinkedHashMap<>();
    private final Map<String, SemanticAuditSink> sinks = new LinkedHashMap<>();
    private volatile List<SemanticObserver> observers = List.of();
    private volatile ExecutorService executor;
    private MemorySemanticAuditStore memory;
    private volatile SemanticAuditReader reader;
    private volatile boolean activated;
    private boolean frozen;
    private final Invocation noop = new Invocation(null, false);

    private SemanticAuditService(CamelContext context) {
        this.context = context;
    }

    public static SemanticAuditService get(CamelContext context) {
        var extension = context.getCamelContextExtension();
        SemanticAuditService audit = extension.getContextPlugin(SemanticAuditService.class);
        if (audit == null) {
            synchronized (CREATION_LOCK) {
                audit = extension.getContextPlugin(SemanticAuditService.class);
                if (audit == null) {
                    audit = new SemanticAuditService(context);
                    extension.addContextPlugin(SemanticAuditService.class, audit);
                }
            }
        }
        return audit;
    }

    public synchronized void validateConfiguration(String source, SemanticAuditConfiguration candidate) {
        if (configurationSource != null && !configurationSource.equals(source) && candidate != null) {
            throw new IllegalArgumentException("Declare semantic audit configuration in only one resource");
        }
        if (candidate == null && !source.equals(configurationSource)) {
            return;
        }
        SemanticAuditConfiguration next = candidate == null ? SemanticAuditConfiguration.DISABLED : candidate;
        if (frozen && !configuration.equals(next)) {
            throw new IllegalArgumentException("Changing semantic audit configuration requires a context restart");
        }
    }

    public synchronized void configure(String source, SemanticAuditConfiguration candidate) {
        validateConfiguration(source, candidate);
        if (candidate != null || source.equals(configurationSource)) {
            configuration = candidate == null ? SemanticAuditConfiguration.DISABLED : candidate;
            configurationSource = candidate == null ? null : source;
        }
    }

    public synchronized void removeConfigurationSource(String source) {
        if (source.equals(configurationSource)) {
            configurationSource = null;
            if (frozen) {
                LOG.warn("Semantic audit resource {} was deleted; retaining the active configuration until context restart",
                        source);
            } else {
                configuration = SemanticAuditConfiguration.DISABLED;
            }
        }
    }

    public void activate() {
        if (!activated) {
            synchronized (this) {
                if (!activated) {
                    try {
                        context.addService(this, true, true);
                        activated = true;
                    } catch (Exception e) {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        synchronized (this) {
            frozen = true;
        }
        observers = List.copyOf(context.getRegistry().findByType(SemanticObserver.class));
        if (memory == null || memory.getCapacity() != configuration.getCapacity()) {
            memory = new MemorySemanticAuditStore(configuration.getCapacity());
        }
        sinkErrors.clear();
        for (String name : configuration.getSinks()) {
            SemanticAuditSink sink = switch (name) {
                case "memory" -> memory;
                case "log" -> new LoggingSemanticAuditSink();
                default -> context.getRegistry().lookupByNameAndType(name, SemanticAuditSink.class);
            };
            if (sink == null) {
                throw new IllegalArgumentException("Unknown semantic audit sink: " + name);
            }
            sinks.put(name, sink);
            sinkErrors.putIfAbsent(name, new AtomicLong());
        }
        reader = "memory".equals(configuration.getReader())
                ? memory
                : context.getRegistry().lookupByNameAndType(configuration.getReader(), SemanticAuditReader.class);
        if (reader == null) {
            throw new IllegalArgumentException("Unknown semantic audit reader: " + configuration.getReader());
        }
        boolean capturing = configuration.isCapturing();
        if (!capturing) {
            return;
        }
        try {
            for (SemanticAuditSink sink : sinks.values()) {
                ServiceHelper.startService(sink);
            }
            ServiceHelper.startService(reader);
            executor = new ThreadPoolBuilder(context).poolSize(1).maxPoolSize(1)
                    .maxQueueSize(configuration.getQueueCapacity()).rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                    .build(this, "SemanticAuditService");
        } catch (Exception e) {
            ServiceHelper.stopService(sinks.values());
            ServiceHelper.stopService(reader);
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        ExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdown();
            try {
                if (!current.awaitTermination(5, TimeUnit.SECONDS)) {
                    dropped.addAndGet(current.shutdownNow().size());
                }
            } catch (InterruptedException e) {
                dropped.addAndGet(current.shutdownNow().size());
                Thread.currentThread().interrupt();
            } finally {
                context.getExecutorServiceManager().shutdownNow(current);
            }
        }
        try {
            ServiceHelper.stopService(sinks.values());
            ServiceHelper.stopService(reader);
        } finally {
            synchronized (this) {
                sinks.clear();
                frozen = false;
                if (configurationSource == null) {
                    configuration = SemanticAuditConfiguration.DISABLED;
                }
            }
        }
    }

    public synchronized SemanticAuditConfiguration getConfiguration() {
        return configuration;
    }

    public SemanticAuditReader getReader() {
        activate();
        if (isStarted()) {
            try {
                ServiceHelper.startService(reader);
            } catch (Exception failure) {
                throw RuntimeCamelException.wrapRuntimeCamelException(failure);
            }
        }
        return reader;
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", configuration.isEnabled());
        status.put("decisionsEnabled", configuration.isCapturing());
        status.put("experts", configuration.getExperts());
        status.put("reader", configuration.getReader());
        status.put("sinks", configuration.getSinks());
        status.put("capacity", configuration.getCapacity());
        status.put("retained", memory == null ? 0 : memory.size());
        status.put("dropped", dropped.get());
        status.put("observerErrors", observerErrors.get());
        Map<String, Long> errors = new LinkedHashMap<>();
        sinkErrors.forEach((name, count) -> errors.put(name, count.get()));
        status.put("sinkErrors", errors);
        status.put("state", getStatus().name());
        status.put("observerCount", observers.size());
        status.put("openTelemetry",
                ServiceHelper.isStarted(context.hasService(
                        service -> isOpenTelemetryTracer(service.getClass()) && ServiceHelper.isStarted(service)))
                                ? "active" : "inactive");
        return status;
    }

    private static boolean isOpenTelemetryTracer(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals("org.apache.camel.opentelemetry2.OpenTelemetryTracer")
                    || current.getName().equals("org.apache.camel.opentelemetry.OpenTelemetryTracer")) {
                return true;
            }
        }
        return false;
    }

    /** Capture parent observation contexts on the caller thread before dispatching a console request. */
    public Request request(String origin) {
        activate();
        Map<SemanticObserver, Object> parents = new IdentityHashMap<>();
        for (SemanticObserver observer : observers) {
            try {
                parents.put(observer, observer.captureContext());
            } catch (Exception | AssertionError | LinkageError failure) {
                observerErrors.incrementAndGet();
            }
        }
        return new Request(origin, parents);
    }

    /** A request identity is shared by cancellation observations and eventual provider completion. */
    public static final class Request {
        private final String id = UUID.randomUUID().toString();
        private final String origin;
        private final Map<SemanticObserver, Object> parents;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private volatile boolean audited;

        private Request(String origin, Map<SemanticObserver, Object> parents) {
            this.origin = origin;
            this.parents = parents;
        }
    }

    public Invocation begin(
            Exchange exchange, String name, SemanticEvaluation evaluation,
            SemanticCapabilities.Operation operation, String expert, String provider, String batchId, Request request) {
        activate();
        if (request == null && exchange != null) {
            request = exchange.getProperty(REQUEST, Request.class);
        }
        boolean first = request != null && request.claimed.compareAndSet(false, true);
        boolean enabled = expert != null && configuration.isEnabled(expert);
        if (request != null && enabled) {
            request.audited = true;
        }
        if (!enabled && observers.isEmpty()) {
            return noop;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", 1);
        fields.put("eventId", UUID.randomUUID().toString());
        fields.put("invocationId", first ? request.id : UUID.randomUUID().toString());
        fields.put("timestamp", Instant.now().toString());
        fields.put("startedAt", fields.get("timestamp"));
        fields.put("category", "evaluation");
        put(fields, "provider", provider);
        fields.put("origin", request == null ? exchange == null ? "direct" : "route" : request.origin);
        put(fields, "requestId", request == null ? null : request.id);
        put(fields, "batchId", batchId);
        put(fields, "definition", name);
        put(fields, "target", name);
        put(fields, "expert", expert);
        put(fields, "operation", evaluation == null ? null : evaluation.getOperation());
        put(fields, "contextId", context.getName());
        if (exchange != null) {
            put(fields, "exchangeId", exchange.getExchangeId());
            put(fields, "routeId", exchange.getFromRouteId());
        }
        if (operation != null) {
            Map<String, Object> semantics = new LinkedHashMap<>();
            put(semantics, "resultType", operation.getResultType().name());
            put(semantics, "meaning", operation.getResultMeaning());
            put(semantics, "probabilityMeaning", operation.getProbabilityMeaning());
            put(semantics, "confidenceMeaning", operation.getConfidenceMeaning());
            // Caller-authored score descriptions and arbitrary parameters are deliberately omitted.
            fields.put("semantics", Map.copyOf(semantics));
        }
        Invocation invocation = new Invocation(fields, enabled);
        SemanticAuditRecord started = SemanticAuditRecord.fromMap(fields);
        for (SemanticObserver observer : observers) {
            try {
                Object parent = request == null ? observer.captureContext() : request.parents.get(observer);
                SemanticObserver.Observation handle = observer.started(started, parent);
                if (handle != null) {
                    invocation.handles.add(handle);
                }
            } catch (Exception | AssertionError | LinkageError failure) {
                observerErrors.incrementAndGet();
            }
        }
        return invocation;
    }

    public final class Invocation {
        private final Map<String, Object> fields;
        private final boolean enabled;
        private final long start = System.nanoTime();
        private final List<SemanticObserver.Observation> handles = new ArrayList<>();
        // Confined to the invocation thread; cancellation emits a separate request record.
        private boolean completed;

        private Invocation(Map<String, Object> fields, boolean enabled) {
            this.fields = fields;
            this.enabled = enabled;
        }

        public String getEventId() {
            return fields == null ? null : (String) fields.get("eventId");
        }

        public void complete(String status, String reason, SemanticResult result) {
            if (fields == null || completed) {
                return;
            }
            completed = true;
            fields.put("status", status);
            fields.put("reasonCode", reason);
            fields.put("durationNanos", Math.max(0, System.nanoTime() - start));
            fields.put("timestamp", Instant.now().toString());
            if (result != null) {
                for (String key : List.of("model", "revision")) {
                    Object value = result.getMetadata().get(key);
                    if (value instanceof String text && !text.isBlank() && text.equals(safe(text))) {
                        fields.put(key, text);
                    }
                }
                Map<String, Object> captured = snapshot(result);
                if (captured == null) {
                    fields.put("resultOmitted", "snapshot_limit");
                } else {
                    fields.put("result", captured);
                }
            }
            SemanticAuditRecord record = SemanticAuditRecord.fromMap(fields);
            // Notify observers in reverse start-callback order; no ambient scope spans callbacks.
            for (int i = handles.size() - 1; i >= 0; i--) {
                try {
                    handles.get(i).completed(record);
                } catch (Exception | AssertionError | LinkageError failure) {
                    observerErrors.incrementAndGet();
                }
            }
            if (enabled) {
                publish(record);
            }
        }
    }

    public void requestOutcome(Request request, String reason) {
        // An unresolved request has no effective expert setting yet; do not bypass exclusions.
        if (!request.audited) {
            return;
        }
        Map<String, Object> fields = base("request");
        put(fields, "requestId", request.id);
        put(fields, "invocationId", request.id);
        put(fields, "origin", request.origin);
        put(fields, "status", reason);
        put(fields, "reasonCode", reason);
        publish(SemanticAuditRecord.fromMap(fields));
    }

    /**
     * Record an explicit application decision. Capture is active if the global default or any expert enables auditing.
     */
    public void decision(Exchange exchange, Map<String, String> decision, List<String> evidence) {
        activate();
        Set<String> allowed = Set.of("action", "operation", "target", "namespace", "reasonCode", "correlationId", "policyId",
                "policyVersion", "rule");
        if (!allowed.containsAll(decision.keySet()) || decision.get("action") == null || decision.get("action").isBlank()
                || evidence.size() > 100
                || evidence.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 256 || !id.equals(safe(id)))) {
            throw new IllegalArgumentException("Invalid semantic audit decision or evidence references");
        }
        Map<String, Object> fields = base("decision");
        decision.forEach((key, value) -> put(fields, key, value));
        fields.put("evidence", List.copyOf(evidence));
        if (exchange != null) {
            put(fields, "exchangeId", exchange.getExchangeId());
            put(fields, "routeId", exchange.getFromRouteId());
        }
        SemanticAuditRecord record = SemanticAuditRecord.fromMap(fields);
        for (SemanticObserver observer : observers) {
            try {
                observer.decision(record);
            } catch (Exception | AssertionError | LinkageError failure) {
                observerErrors.incrementAndGet();
            }
        }
        if (configuration.isCapturing()) {
            publish(record);
        }
    }

    private Map<String, Object> base(String category) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", 1);
        fields.put("eventId", UUID.randomUUID().toString());
        fields.put("timestamp", Instant.now().toString());
        fields.put("category", category);
        put(fields, "contextId", context.getName());
        return fields;
    }

    private void publish(SemanticAuditRecord record) {
        ExecutorService current = executor;
        if (current == null || !isStarted()) {
            dropped.incrementAndGet();
            return;
        }
        try {
            current.execute(() -> {
                for (var entry : sinks.entrySet()) {
                    try {
                        entry.getValue().append(record);
                    } catch (Exception | AssertionError | LinkageError failure) {
                        sinkErrors.get(entry.getKey()).incrementAndGet();
                    }
                }
            });
        } catch (RejectedExecutionException full) {
            dropped.incrementAndGet();
        }
    }

    private static Map<String, Object> snapshot(SemanticResult result) {
        Map<String, Object> values = new LinkedHashMap<>();
        Object value = result.getValue();
        if (value instanceof String text && !text.equals(safe(text))
                || value instanceof Set<?> labels && (labels.size() > 100
                        || labels.stream().anyMatch(label -> !(label instanceof String text) || !text.equals(safe(text))))
                || result.getProbabilities().size() > 100
                || result.getProbabilities().keySet().stream().anyMatch(key -> !key.equals(safe(key)))) {
            return null;
        }
        if (value instanceof Boolean) {
            values.put("value", value);
        } else if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            values.put("value", number.doubleValue());
        } else if (value instanceof String text) {
            put(values, "value", text);
        } else if (value instanceof Set<?> labels) {
            values.put("value", labels.stream().filter(String.class::isInstance).map(String.class::cast).limit(100)
                    .map(SemanticAuditService::safe).toList());
        }
        if (result.getProbability() != null) {
            values.put("probability", result.getProbability());
        }
        if (result.getConfidence() != null) {
            values.put("confidence", result.getConfidence());
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        result.getProbabilities().entrySet().stream().limit(100)
                .forEach(e -> probabilities.put(safe(e.getKey()), e.getValue()));
        if (!probabilities.isEmpty()) {
            values.put("probabilities", Map.copyOf(probabilities));
        }
        // Arbitrary provider metadata is not safe to export merely because it is named 'metadata'.
        return Collections.unmodifiableMap(values);
    }

    private static void put(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, safe(value));
        }
    }

    private static String safe(String value) {
        StringBuilder text = new StringBuilder();
        value.codePoints().filter(cp -> !Character.isISOControl(cp)).limit(257).forEach(text::appendCodePoint);
        if (text.codePointCount(0, text.length()) > 256) {
            text.setLength(text.offsetByCodePoints(0, 255));
            text.append('…');
        }
        return text.toString();
    }
}
