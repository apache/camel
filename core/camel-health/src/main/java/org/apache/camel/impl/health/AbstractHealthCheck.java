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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.health.HealthCheckResultStrategy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for {@link HealthCheck}.
 */
public abstract class AbstractHealthCheck implements HealthCheck, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractHealthCheck.class);

    private CamelContext camelContext;
    private boolean enabled = true;
    private final Object lock;
    private final String group;
    private final String id;
    private final ConcurrentMap<String, Object> meta;

    protected AbstractHealthCheck(String id) {
        this(null, id, null);
    }

    protected AbstractHealthCheck(String group, String id) {
        this(group, id, null);
    }

    protected AbstractHealthCheck(String group, String id, Map<String, Object> meta) {
        this.lock = new Object();
        this.group = group;
        this.id = ObjectHelper.notNull(id, "HealthCheck ID");
        this.meta = new ConcurrentHashMap<>();

        if (meta != null) {
            this.meta.putAll(meta);
        }

        this.meta.put(CHECK_ID, id);
        if (group != null) {
            this.meta.putIfAbsent(CHECK_GROUP, group);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Map<String, Object> getMetaData() {
        return Collections.unmodifiableMap(this.meta);
    }

    @Override
    public Result call() {
        return call(Collections.emptyMap());
    }

    @Override
    public Result call(Map<String, Object> options) {
        HealthCheckResultBuilder builder;
        synchronized (lock) {
            builder = doCall(options);
        }

        HealthCheckResultStrategy strategy = customHealthCheckResponseStrategy();
        if (strategy != null) {
            strategy.processResult(this, options, builder);
        }

        return builder.build();
    }

    protected HealthCheckResultBuilder doCall(Map<String, Object> options) {
        final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(this);

        // Extract relevant information from meta data.
        int invocationCount = (Integer) meta.getOrDefault(INVOCATION_COUNT, 0);
        int failureCount = (Integer) meta.getOrDefault(FAILURE_COUNT, 0);
        int successCount = (Integer) meta.getOrDefault(SUCCESS_COUNT, 0);

        String invocationTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // Set common meta-data
        meta.put(INVOCATION_ATTEMPT_TIME, invocationTime);

        if (!isEnabled()) {
            LOG.debug("health-check {}/{} disabled", getGroup(), getId());
            builder.message("Disabled");
            builder.detail(CHECK_ENABLED, false);
            builder.unknown();
            return builder;
        }

        LOG.debug("Invoke health-check {}/{}", getGroup(), getId());
        doCall(builder, options);

        if (builder.state() == null) {
            builder.unknown();
        }

        if (builder.state() == State.DOWN) {
            // reset success since it failed
            successCount = 0;
            failureCount++;
        } else if (builder.state() == State.UP) {
            // reset failure since it ok
            failureCount = 0;
            successCount++;
        }

        meta.put(INVOCATION_TIME, invocationTime);
        meta.put(INVOCATION_COUNT, ++invocationCount);
        meta.put(FAILURE_COUNT, failureCount);
        meta.put(SUCCESS_COUNT, successCount);

        // Copy some meta-data bits to the response attributes so the
        // response caches the health-check state at the time of the invocation.
        builder.detail(INVOCATION_TIME, meta.get(INVOCATION_TIME));
        builder.detail(INVOCATION_COUNT, meta.get(INVOCATION_COUNT));
        builder.detail(FAILURE_COUNT, meta.get(FAILURE_COUNT));
        builder.detail(SUCCESS_COUNT, meta.get(SUCCESS_COUNT));

        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractHealthCheck)) {
            return false;
        }

        AbstractHealthCheck that = (AbstractHealthCheck) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    protected final void addMetaData(String key, Object value) {
        meta.put(key, value);
    }

    /**
     * Invoke the health check.
     *
     * @see HealthCheck#call(Map)
     */
    protected abstract void doCall(HealthCheckResultBuilder builder, Map<String, Object> options);

    private HealthCheckResultStrategy customHealthCheckResponseStrategy() {
        if (camelContext != null) {
            return camelContext.getRegistry().findSingleByType(HealthCheckResultStrategy.class);
        }
        return null;
    }

}
