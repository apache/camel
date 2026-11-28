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
package org.apache.camel.support;

import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;

/**
 * {@link HealthCheck} that uses the last polling state from {@link ScheduledPollConsumer} when reporting the health,
 * and the poll in progress when it runs longer than {@link ScheduledPollConsumer#getHealthCheckMaxPollDuration()}. It
 * is DOWN after a {@link VirtualMachineError} stopped the consumer (see {@link DefaultConsumer#getFatalError()}) until
 * the consumer is restarted.
 */
public class ScheduledPollConsumerHealthCheck implements HealthCheck {

    /**
     * Detail with how long the consumer has been polling (millis), when that is longer than the
     * healthCheckMaxPollDuration option of the consumer.
     *
     * @since 4.24
     */
    public static final String POLL_DURATION = "poll.duration";

    private final HealthCheckRegistry registry;
    private HealthCheck.State initialState;
    private final ScheduledPollConsumer consumer;
    private final String id;
    private final String sanitizedBaseUri;
    private final String sanitizedUri;
    private boolean enabled = true;

    public ScheduledPollConsumerHealthCheck(ScheduledPollConsumer consumer, String id) {
        this.registry = HealthCheckRegistry.get(consumer.getEndpoint().getCamelContext());
        this.initialState = registry != null ? registry.getInitialState() : State.DOWN;
        this.consumer = consumer;
        this.id = id;
        this.sanitizedBaseUri = URISupport.sanitizeUri(consumer.getEndpoint().getEndpointBaseUri());
        this.sanitizedUri = URISupport.sanitizeUri(consumer.getEndpoint().getEndpointUri());
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
    public Result call(Map<String, Object> options) {
        final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(this);

        // set initial state
        builder.state(initialState);

        // ensure to sanitize uri, so we do not show sensitive information such as passwords
        builder.detail(ENDPOINT_URI, sanitizedUri);

        // what kind of check is this
        HealthCheck.Kind kind;
        if (isLiveness() && isReadiness()) {
            // if we can do both then use kind from what type we were invoked as
            kind = (Kind) options.getOrDefault(CHECK_KIND, Kind.ALL);
        } else {
            // we can only be either live or ready so report that
            kind = isLiveness() ? Kind.LIVENESS : Kind.READINESS;
        }
        builder.detail(CHECK_KIND, kind);

        if (!isEnabled()) {
            builder.message("Disabled");
            builder.detail(CHECK_ENABLED, false);
            return builder.unknown().build();
        }

        // a VirtualMachineError stopped the consumer: it polls no more, also with scheduler.concurrentConsumers > 1, until it
        // is restarted (CAMEL-27515)
        Throwable fatalError = consumer.getFatalError();
        if (fatalError != null) {
            builder.down();
            builder.error(fatalError);
            String msg = "Consumer stopped polling due to a fatal error (restart the route to poll again) route: %s (%s)";
            builder.message(String.format(msg, consumer.getRouteId(), sanitizedBaseUri));
            return builder.build();
        }

        // a poll that hangs (such as in a read without timeout on a connection that went silent) is not done, so the
        // state below, which a poll updates when it is done, does not see it
        long maxPollDuration = consumer.getHealthCheckMaxPollDuration();
        if (maxPollDuration > 0) {
            long pollingDuration = consumer.getPollingDuration();
            if (pollingDuration > maxPollDuration) {
                builder.down();
                builder.detail(POLL_DURATION, pollingDuration);
                String msg = "Consumer has been polling for %s, longer than healthCheckMaxPollDuration (%s) route: %s (%s)";
                builder.message(String.format(msg, printDuration(pollingDuration), printDuration(maxPollDuration),
                        consumer.getRouteId(), sanitizedBaseUri));
                return builder.build();
            }
        }

        long ec = consumer.getErrorCounter();
        boolean ready = consumer.isConsumerReady();
        Throwable cause = consumer.getLastError();

        boolean healthy = ec == 0;
        boolean readiness = kind.equals(Kind.READINESS);
        if (readiness && !ready) {
            // special for readiness check before first poll is done or not yet ready
            // if initial state is UP or UNKNOWN then return that
            // otherwise we are DOWN
            boolean down = builder.state().equals(State.DOWN);
            if (!down) {
                return builder.build();
            } else {
                healthy = false;
            }
        }

        if (healthy) {
            builder.up();
        } else {
            builder.down();
            builder.detail(FAILURE_ERROR_COUNT, ec);
            String rid = consumer.getRouteId();
            if (ec > 0) {
                String msg = "Consumer failed polling %s times route: %s (%s)";
                builder.message(String.format(msg, ec, rid, sanitizedBaseUri));
            } else {
                String msg = "Consumer has not yet polled route: %s (%s)";
                builder.message(String.format(msg, rid, sanitizedBaseUri));
            }
            builder.error(cause);

            // include any additional details
            if (consumer.getLastErrorDetails() != null) {
                builder.details(consumer.getLastErrorDetails());
            }
        }

        return builder.build();
    }

    private static String printDuration(long millis) {
        // TimeUtils prints less than a second as 0s
        return millis < 1000 ? millis + "ms" : TimeUtils.printDuration(millis);
    }

    public State getInitialState() {
        return initialState;
    }

    /**
     * Used to allow special consumers to override the initial state of the health check (readiness check) during
     * startup.
     *
     * Consumers that are internal only such as camel-scheduler uses UP as initial state because the scheduler may be
     * configured to run only very in-frequently and therefore the overall health-check state would be affected and seen
     * as DOWN.
     */
    public void setInitialState(State initialState) {
        this.initialState = initialState;
    }

    @Override
    public String getGroup() {
        return "camel";
    }

    @Override
    public String getId() {
        return id;
    }
}
