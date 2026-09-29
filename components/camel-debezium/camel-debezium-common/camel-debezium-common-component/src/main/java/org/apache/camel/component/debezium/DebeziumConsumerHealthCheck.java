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
package org.apache.camel.component.debezium;

import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.util.URISupport;

/**
 * {@link HealthCheck} reporting the state of the embedded Debezium engine that backs a {@link DebeziumConsumer}.
 * <p>
 * The engine runs on its own thread and does not restart itself, so once it has stopped with an error the route no
 * longer receives change events even though it is still started. This check turns the route DOWN in that case.
 */
public class DebeziumConsumerHealthCheck implements HealthCheck {

    private final DebeziumConsumer consumer;
    private final String id;
    private final String sanitizedUri;
    private boolean enabled = true;

    public DebeziumConsumerHealthCheck(DebeziumConsumer consumer, String id) {
        this.consumer = consumer;
        this.id = id;
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
    public String getGroup() {
        return "camel";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Result call(Map<String, Object> options) {
        final HealthCheckResultBuilder builder = HealthCheckResultBuilder.on(this);

        // ensure to sanitize uri, so we do not show sensitive information such as passwords
        builder.detail(ENDPOINT_URI, sanitizedUri);

        if (!isEnabled()) {
            builder.message("Disabled");
            builder.detail(CHECK_ENABLED, false);
            return builder.unknown().build();
        }

        final Throwable failure = consumer.getEngineFailure();
        if (failure == null) {
            // the engine is either starting, running, or was stopped on request, none of which is a failure
            return builder.up().build();
        }

        builder.down();
        builder.message(String.format(
                "Debezium engine stopped after a failure, route: %s (%s) no longer consumes change events",
                consumer.getRouteId(), sanitizedUri));
        builder.error(failure);
        return builder.build();
    }
}
