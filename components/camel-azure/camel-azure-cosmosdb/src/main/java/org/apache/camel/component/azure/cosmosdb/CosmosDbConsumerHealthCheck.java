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
package org.apache.camel.component.azure.cosmosdb;

import java.util.Map;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckResultBuilder;

/**
 * Readiness health check of the Azure Cosmos DB consumer: the initial state of the health checks (DOWN by default)
 * while the first attempt to start the change feed processor runs, DOWN with the last start failure and the number of
 * failed attempts while it cannot start (the consumer tries again with a backoff until it starts), and UP once it has
 * started.
 */
public class CosmosDbConsumerHealthCheck implements HealthCheck {

    private final CosmosDbConsumer consumer;
    private final String routeId;
    private final State initialState;
    private volatile boolean enabled = true;

    public CosmosDbConsumerHealthCheck(CosmosDbConsumer consumer, String routeId) {
        this.consumer = consumer;
        this.routeId = routeId;
        // camel.health.initial-state, as the Debezium consumer check does
        HealthCheckRegistry registry = HealthCheckRegistry.get(consumer.getEndpoint().getCamelContext());
        this.initialState = registry != null ? registry.getInitialState() : State.DOWN;
    }

    @Override
    public String getGroup() {
        return "camel";
    }

    @Override
    public String getId() {
        return routeId != null ? "consumer:azure-cosmosdb-" + routeId : "consumer:azure-cosmosdb";
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
        if (consumer.isChangeFeedProcessorStarted()) {
            return builder.up().build();
        }
        Throwable failure = consumer.getLastStartFailure();
        long failedStarts = consumer.getFailedStarts();
        builder.detail("failed.starts", failedStarts);
        if (failure == null) {
            // the first attempt to start runs
            return builder.state(initialState).message("The change feed processor is starting").build();
        }
        return builder.down().error(failure)
                .message("The change feed processor has not started yet (" + failedStarts + " failed attempt(s))")
                .build();
    }
}
