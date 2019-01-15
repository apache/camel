/**
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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckConfiguration;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractHealthCheck implements HealthCheck {
    public static final String CHECK_ID = "check.id";
    public static final String CHECK_GROUP = "check.group";
    public static final String CHECK_ENABLED = "check.enabled";
    public static final String INVOCATION_COUNT = "invocation.count";
    public static final String INVOCATION_TIME = "invocation.time";
    public static final String INVOCATION_ATTEMPT_TIME = "invocation.attempt.time";
    public static final String FAILURE_COUNT = "failure.count";

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractHealthCheck.class);

    private final Object lock;
    private final String group;
    private final String id;
    private final ConcurrentMap<String, Object> meta;

    private HealthCheckConfiguration configuration;
    private HealthCheck.Result lastResult;
    private ZonedDateTime lastInvocation;

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
        this.configuration = new HealthCheckConfiguration();
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
    public String getId() {
        return id;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public Map<String, Object> getMetaData() {
        return Collections.unmodifiableMap(this.meta);
    }

    @Override
    public HealthCheckConfiguration getConfiguration() {
        return this.configuration;
    }

    public void setConfiguration(HealthCheckConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Result call() {
        return call(Collections.emptyMap());
    }

    @Override
    public Result call(Map<String, Object> options) {
        synchronized (lock) {
            final HealthCheckConfiguration conf = getConfiguration();
            final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(this);
            final ZonedDateTime now = ZonedDateTime.now();
            final boolean enabled = ObjectHelper.supplyIfEmpty(conf.isEnabled(), HealthCheckConfiguration::defaultValueEnabled);
            final Duration interval = ObjectHelper.supplyIfEmpty(conf.getInterval(), HealthCheckConfiguration::defaultValueInterval);
            final Integer threshold = ObjectHelper.supplyIfEmpty(conf.getFailureThreshold(), HealthCheckConfiguration::defaultValueFailureThreshold);

            // Extract relevant information from meta data.
            int invocationCount = (Integer)meta.getOrDefault(INVOCATION_COUNT, 0);
            int failureCount = (Integer)meta.getOrDefault(FAILURE_COUNT, 0);

            String invocationTime = now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
            boolean call = true;

            // Set common meta-data
            meta.put(INVOCATION_ATTEMPT_TIME, invocationTime);

            if (!enabled) {
                LOGGER.debug("health-check {}/{} won't be invoked as not enabled", getGroup(), getId());

                builder.message("Disabled");
                builder.detail(CHECK_ENABLED, false);

                return builder.unknown().build();
            }

            // check if the last invocation is far enough to have this check invoked
            // again without violating the interval configuration.
            if (lastResult != null && lastInvocation != null && !interval.isZero()) {
                Duration elapsed = Duration.between(lastInvocation, now);

                if (elapsed.compareTo(interval) < 0) {
                    LOGGER.debug("health-check {}/{} won't be invoked as interval ({}) is not yet expired (last-invocation={})",
                        getGroup(),
                        getId(),
                        elapsed,
                        lastInvocation);

                    call = false;
                }
            }

            // Invoke the check.
            if (call) {
                LOGGER.debug("Invoke health-check {}/{}", getGroup(), getId());

                doCall(builder, options);

                // State should be set here
                ObjectHelper.notNull(builder.state(), "Response State");

                if (builder.state() == State.DOWN) {
                    // If the service is un-healthy but the number of time it
                    // has been consecutively reported in this state is less
                    // than the threshold configured, mark it as UP. This is
                    // used to avoid false positive in case of glitches.
                    if (failureCount++ < threshold) {
                        LOGGER.debug("Health-check {}/{} has status DOWN but failure count ({}) is less than configured threshold ({})",
                            getGroup(),
                            getId(),
                            failureCount,
                            threshold);

                        builder.up();
                    }
                } else {
                    failureCount = 0;
                }

                meta.put(INVOCATION_TIME, invocationTime);
                meta.put(FAILURE_COUNT, failureCount);
                meta.put(INVOCATION_COUNT, ++invocationCount);

                // Copy some of the meta-data bits to the response attributes so the
                // response caches the health-check state at the time of the invocation.
                builder.detail(INVOCATION_TIME, meta.get(INVOCATION_TIME));
                builder.detail(INVOCATION_COUNT, meta.get(INVOCATION_COUNT));
                builder.detail(FAILURE_COUNT, meta.get(FAILURE_COUNT));

                // update last invocation time.
                lastInvocation = now;
            } else if (lastResult != null) {
                lastResult.getMessage().ifPresent(builder::message);
                lastResult.getError().ifPresent(builder::error);

                builder.state(lastResult.getState());
                builder.details(lastResult.getDetails());
            }

            lastResult = builder.build();

            return lastResult;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractHealthCheck check = (AbstractHealthCheck) o;

        return id != null ? id.equals(check.id) : check.id == null;
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
     * @see {@link HealthCheck#call(Map)}
     */
    protected abstract void doCall(HealthCheckResultBuilder builder, Map<String, Object> options);
}
