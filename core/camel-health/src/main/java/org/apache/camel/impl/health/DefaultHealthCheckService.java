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
package org.apache.camel.impl.health;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckService;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.LockHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultHealthCheckService extends ServiceSupport implements HealthCheckService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHealthCheckService.class);

    private final ConcurrentMap<HealthCheck, HealthCheck.Result> checks;
    private final ConcurrentMap<String, Map<String, Object>> options;
    private final List<BiConsumer<HealthCheck.State, HealthCheck>> listeners;
    private final StampedLock lock;

    private CamelContext camelContext;
    private ScheduledExecutorService executorService;
    private long checkInterval;
    private TimeUnit checkIntervalUnit;
    private volatile HealthCheckRegistry registry;
    private volatile ScheduledFuture<?> future;

    public DefaultHealthCheckService() {
        this(null);
    }

    public DefaultHealthCheckService(CamelContext camelContext) {
        this.checks = new ConcurrentHashMap<>();
        this.options = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
        this.lock = new StampedLock();

        this.camelContext = camelContext;
        this.checkInterval = 30;
        this.checkIntervalUnit = TimeUnit.SECONDS;
    }

    // ************************************
    // Properties
    // ************************************

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public HealthCheckRegistry getHealthCheckRegistry() {
        return registry;
    }

    public void setHealthCheckRegistry(HealthCheckRegistry registry) {
        this.registry = registry;
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    public void setCheckInterval(long interval, TimeUnit intervalUnit) {
        setCheckInterval(interval);
        setCheckIntervalUnit(intervalUnit);
    }

    public TimeUnit getCheckIntervalUnit() {
        return checkIntervalUnit;
    }

    public void setCheckIntervalUnit(TimeUnit checkIntervalUnit) {
        this.checkIntervalUnit = checkIntervalUnit;
    }

    @Override
    public void addStateChangeListener(BiConsumer<HealthCheck.State, HealthCheck> consumer) {
        LockHelper.doWithWriteLock(
            lock,
            () -> listeners.add(consumer)
        );
    }

    @Override
    public void removeStateChangeListener(BiConsumer<HealthCheck.State, HealthCheck> consumer) {
        LockHelper.doWithWriteLock(
            lock,
            () -> listeners.removeIf(listener -> listener.equals(consumer))
        );
    }

    @Override
    public void setHealthCheckOptions(String id, Map<String, Object> options) {
        this.options.put(id, options);
    }

    @Override
    public Optional<HealthCheck.Result> call(String id) {
        return call(id, options.getOrDefault(id, Collections.emptyMap()));
    }

    @Override
    public Optional<HealthCheck.Result> call(String id, Map<String, Object> options) {
        return registry.getCheck(id).map(check -> invoke(check, options));
    }

    @Override
    public void notify(HealthCheck check, HealthCheck.Result result) {
        LockHelper.doWithWriteLock(
            lock,
            () -> processResult(check, result)
        );
    }

    @Override
    public Collection<HealthCheck.Result> getResults() {
        return new ArrayList<>(this.checks.values());
    }

    // ************************************
    // Lifecycle
    // ************************************

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");

        if (executorService == null) {
            executorService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "DefaultHealthCheckService");
        }
        if (future != null) {
            future.cancel(true);
        }
        if (registry == null) {
            registry = HealthCheckRegistry.get(camelContext);
        }

        if (ObjectHelper.isNotEmpty(registry) && ObjectHelper.isEmpty(future)) {
            // Start the health check task only if the health check registry
            // has been registered.
            LOGGER.debug("Schedule health-checks to be executed every {} ({})", checkInterval, checkIntervalUnit.name());
            future = executorService.scheduleAtFixedRate(
                () -> {
                    if (!isRunAllowed()) {
                        // do not invoke the check if the service is not yet
                        // properly started.
                        return;
                    }

                    LOGGER.debug("Invoke health-checks (scheduled)");

                    registry.stream()
                        .collect(Collectors.groupingBy(HealthCheckHelper::getGroup))
                        .entrySet().stream()
                            .map(Map.Entry::getValue)
                            .flatMap(Collection::stream)
                            .sorted(Comparator.comparingInt(HealthCheck::getOrder))
                            .forEach(this::invoke);
                },
                checkInterval,
                checkInterval,
                checkIntervalUnit);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(true);
            future = null;
        }
        if (executorService != null) {
            if (camelContext != null) {
                camelContext.getExecutorServiceManager().shutdownNow(executorService);
            } else {
                executorService.shutdownNow();
            }
            executorService = null;
        }
    }

    // ************************************
    // Helpers
    // ************************************

    private HealthCheck.Result processResult(HealthCheck check, HealthCheck.Result result) {
        final HealthCheck.Result cachedResult = checks.get(check);
    
        if (!isSameResult(result, cachedResult)) {
            // Maybe make the listener aware of the reason, i.e.
            // the service is still un-healthy but the message
            // or error has changed.
            listeners.forEach(listener -> listener.accept(result.getState(), check));
        }

        // replace the old result with the new one even if the
        // state has not changed but the reason/error may be
        // changed.
        checks.put(check, result);

        return result;
    }

    private HealthCheck.Result invoke(HealthCheck check) {
        return invoke(check, options.getOrDefault(check.getId(), Collections.emptyMap()));
    }

    private HealthCheck.Result invoke(HealthCheck check, Map<String, Object> options) {
        return LockHelper.supplyWithWriteLock(
            lock,
            () -> {
                LOGGER.debug("Invoke health-check {}", check.getId());
                return processResult(check, check.call(options));
            }
        );
    }

    /**
     * Check if two results are equals by checking only the state, this method
     * does not check if the result comes from the same health check, this should
     * be done by the caller.
     * <p>
     * A future implementation should check all the parameter of the result.
     */
    private boolean isSameResult(HealthCheck.Result r1, HealthCheck.Result r2) {
        if (Objects.equals(r1, r2)) {
            return true;
        }

        if (r1 != null && r2 != null) {
            return r1.getState() == r2.getState();
        }

        return false;
    }
}
