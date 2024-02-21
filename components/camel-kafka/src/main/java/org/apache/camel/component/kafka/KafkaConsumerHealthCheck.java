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
package org.apache.camel.component.kafka;

import java.util.List;
import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

/**
 * Kafka consumer readiness health-check
 */
public class KafkaConsumerHealthCheck extends AbstractHealthCheck {

    private final KafkaConsumer kafkaConsumer;
    private final String routeId;

    public KafkaConsumerHealthCheck(KafkaConsumer kafkaConsumer, String routeId) {
        super("camel", "consumer:kafka-" + routeId);
        this.kafkaConsumer = kafkaConsumer;
        this.routeId = routeId;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        List<TaskHealthState> healthStates = kafkaConsumer.healthStates();

        for (TaskHealthState healthState : healthStates) {
            if (!healthState.isReady()) {
                builder.down();

                final String msg = healthState.buildStateMessage();

                builder.message(msg);

                // was this caused by consumer not able to connect then this is stored in last error
                builder.error(healthState.getLastError());

                KafkaConfiguration cfg = kafkaConsumer.getEndpoint().getConfiguration();

                if (healthState.getBootstrapServers() != null) {
                    builder.detail("bootstrap.servers", healthState.getBootstrapServers());
                }
                if (healthState.getClientId() != null) {
                    builder.detail("client.id", healthState.getClientId());
                }

                String gid = healthState.getGroupId();
                if (gid != null) {
                    builder.detail("group.id", gid);
                }

                if (routeId != null) {
                    // camel route id
                    builder.detail("route.id", routeId);
                }
                builder.detail("topic", cfg.getTopic());

                return; // break on first DOWN
            }
        }
        builder.up();
    }
}
