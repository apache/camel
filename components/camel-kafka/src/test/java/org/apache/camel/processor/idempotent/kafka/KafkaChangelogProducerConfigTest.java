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
package org.apache.camel.processor.idempotent.kafka;

import java.util.Properties;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer that the Kafka idempotent and key-value repositories write their changes with uses the acks and
 * idempotence of the Kafka client (acks=all with idempotence), unless the producer config of the repository sets them.
 * No broker is needed: the client resolves the config without connecting.
 */
class KafkaChangelogProducerConfigTest {

    @Test
    void producerUsesTheAcksOfTheKafkaClient() {
        ProducerConfig config = new ProducerConfig(producerProperties(new Properties()));

        // acks=all is -1 for the client
        assertEquals("-1", config.getString(ProducerConfig.ACKS_CONFIG));
        assertTrue(config.getBoolean(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
    }

    @Test
    void producerConfigThatEnablesIdempotenceIsValid() {
        Properties user = new Properties();
        user.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        // with acks=1 forced, the client refuses idempotence (ConfigException)
        assertDoesNotThrow(() -> new ProducerConfig(producerProperties(user)));
    }

    @Test
    void acksOfTheProducerConfigWins() {
        Properties user = new Properties();
        user.put(ProducerConfig.ACKS_CONFIG, "1");

        ProducerConfig config = new ProducerConfig(producerProperties(user));

        assertEquals("1", config.getString(ProducerConfig.ACKS_CONFIG));
    }

    private static Properties producerProperties(Properties user) {
        Properties producerConfig = KafkaChangelog.producerConfig(null, "localhost:9092");
        producerConfig.putAll(user);
        KafkaChangelog<String> changelog = new KafkaChangelog<>(
                "test", "test-topic", new Properties(), producerConfig, StringDeserializer.class, StringSerializer.class,
                100, true, record -> {
                });
        return changelog.producerProperties();
    }
}
