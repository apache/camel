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
package org.apache.camel.component.google.pubsub;

import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.TimeUtils;

/**
 * Readiness health check of the Google PubSub consumer: DOWN with the failure while a subscriber loop of the current
 * start of the consumer waits to subscribe or pull again after a failure (it retries every retry delay until the cause
 * is gone), or has ended for good, and UP otherwise.
 */
public class GooglePubsubConsumerHealthCheck implements HealthCheck {

    private final GooglePubsubConsumer consumer;
    private final String routeId;
    private volatile boolean enabled = true;

    public GooglePubsubConsumerHealthCheck(GooglePubsubConsumer consumer, String routeId) {
        this.consumer = consumer;
        this.routeId = routeId;
    }

    @Override
    public String getGroup() {
        return "camel";
    }

    @Override
    public String getId() {
        return routeId != null ? "consumer:google-pubsub-" + routeId : "consumer:google-pubsub";
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
        HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(this);
        if (!enabled) {
            // as AbstractHealthCheck of camel-health does for a disabled check
            return builder.message("Disabled").unknown().build();
        }
        Throwable fatal = consumer.getFatalError();
        if (fatal != null) {
            return builder.down().error(fatal)
                    .message("The consumer stopped after a fatal error. Restart the route to start it again.")
                    .build();
        }
        // the failure first and the count second: a failure seen here always comes with its count
        Throwable failure = consumer.getLoopFailure();
        if (failure == null) {
            return builder.up().build();
        }
        int failingLoops = consumer.getFailingLoops();
        int failedAttempts = consumer.getFailedAttempts();
        builder.detail("failing.loops", failingLoops);
        builder.detail("failed.attempts", failedAttempts);
        String message = failedAttempts > 0
                ? "Cannot get messages from PubSub (" + failedAttempts + " failed attempt(s) in a row, trying again every "
                  + TimeUtils.printDuration(consumer.getRetryDelay()) + ")"
                : "Cannot get messages from PubSub: a subscriber loop has ended. Restart the route to start it again.";
        return builder.down().error(failure).message(message).build();
    }
}
