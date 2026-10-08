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
package org.apache.camel.component.kafka.share;

import java.util.Map;

import org.apache.camel.component.kafka.TaskHealthState;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

/**
 * Kafka share consumer readiness health-check: ready when every share consumer is created and subscribed. A share
 * consumer has no partition assignment to report.
 */
public class KafkaShareConsumerHealthCheck extends AbstractHealthCheck {

    private final KafkaShareConsumer kafkaShareConsumer;
    private final String routeId;

    public KafkaShareConsumerHealthCheck(KafkaShareConsumer kafkaShareConsumer, String routeId) {
        super("camel", "consumer:kafka-share-" + routeId);
        this.kafkaShareConsumer = kafkaShareConsumer;
        this.routeId = routeId;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        for (TaskHealthState healthState : kafkaShareConsumer.healthStates()) {
            if (!healthState.isReady()) {
                builder.down();
                builder.message(healthState.buildStateMessage());
                // was this caused by consumer not able to connect then this is stored in last error
                builder.error(healthState.getLastError());

                if (healthState.getBootstrapServers() != null) {
                    builder.detail("bootstrap.servers", healthState.getBootstrapServers());
                }
                if (healthState.getClientId() != null) {
                    builder.detail("client.id", healthState.getClientId());
                }
                if (healthState.getGroupId() != null) {
                    builder.detail("group.id", healthState.getGroupId());
                }
                if (routeId != null) {
                    builder.detail("route.id", routeId);
                }
                builder.detail("topic", kafkaShareConsumer.getEndpoint().getConfiguration().getTopic());
                return; // break on first DOWN
            }
        }
        builder.up();
    }
}
