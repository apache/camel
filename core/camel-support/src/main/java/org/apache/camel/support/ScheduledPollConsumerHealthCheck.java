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
import org.apache.camel.health.HealthCheckConfiguration;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.URISupport;

/**
 * {@link HealthCheck} that uses the last polling state from {@link ScheduledPollConsumer} when reporting the health.
 */
public class ScheduledPollConsumerHealthCheck implements HealthCheck {

    private final ScheduledPollConsumer consumer;
    private final String id;
    private final String sanitizedUri; // used for error message which should mask sensitive details

    public ScheduledPollConsumerHealthCheck(ScheduledPollConsumer consumer, String id) {
        this.consumer = consumer;
        this.id = id;
        this.sanitizedUri = URISupport.sanitizeUri(consumer.getEndpoint().getEndpointBaseUri());
    }

    @Override
    public HealthCheckConfiguration getConfiguration() {
        throw new UnsupportedOperationException("Configuration is not in use for this kind of health-check");
    }

    @Override
    public Result call(Map<String, Object> options) {
        final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(this);
        builder.detail(FAILURE_ENDPOINT_URI, consumer.getEndpoint().getEndpointUri());

        long ec = consumer.getErrorCounter();
        long cnt = consumer.getCounter();
        Throwable cause = consumer.getLastError();

        // can only be healthy if we have at least one poll and there are no errors
        boolean healthy = cnt > 0 && ec == 0;
        if (healthy) {
            builder.up();
        } else {
            builder.down();
            builder.detail(FAILURE_ERROR_COUNT, ec);
            String rid = consumer.getRouteId();
            if (ec > 0) {
                String msg = "Consumer failed polling %s times route: %s (%s)";
                builder.message(String.format(msg, ec, rid, sanitizedUri));
            } else {
                String msg = "Consumer has not yet polled route: %s (%s)";
                builder.message(String.format(msg, rid, sanitizedUri));
            }
            builder.error(cause);
        }

        return builder.build();
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
