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

import java.util.Map;
import java.util.Properties;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.kafka.clients.consumer.ConsumerConfig;

/**
 * Kafka producer readiness health-check
 */
public class KafkaProducerHealthCheck extends AbstractHealthCheck {

    private final KafkaProducer kafkaProducer;
    private final String clientId;

    public KafkaProducerHealthCheck(KafkaProducer kafkaProducer, String clientId) {
        super("camel", "producer:kafka-" + clientId);
        this.kafkaProducer = kafkaProducer;
        this.clientId = clientId;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        if (!kafkaProducer.isReady()) {
            builder.down();
            builder.message("KafkaProducer is not ready");

            KafkaConfiguration cfg = kafkaProducer.getEndpoint().getConfiguration();
            Properties props = kafkaProducer.getProps();

            builder.detail("bootstrap.servers", props.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
            builder.detail("client.id", clientId);
            String gid = props.getProperty(ConsumerConfig.GROUP_ID_CONFIG);
            if (gid != null) {
                builder.detail("group.id", gid);
            }
            builder.detail("topic", cfg.getTopic());
        } else {
            builder.up();
        }
    }
}
