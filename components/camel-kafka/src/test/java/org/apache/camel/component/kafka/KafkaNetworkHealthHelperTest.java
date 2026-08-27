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

import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KafkaNetworkHealthHelperTest {

    @Test
    void consumerHasReadyNodesShouldFailOpenForCustomConsumer() {
        assertTrue(KafkaNetworkHealthHelper.consumerHasReadyNodes(mock(org.apache.kafka.clients.consumer.Consumer.class)));
    }

    @Test
    void consumerHasReadyNodesShouldFailOpenForNullConsumer() {
        assertTrue(KafkaNetworkHealthHelper.consumerHasReadyNodes(null));
    }

    @Test
    void producerHasReadyNodesShouldFailOpenForCustomProducer() {
        assertTrue(KafkaNetworkHealthHelper.producerHasReadyNodes(mock(org.apache.kafka.clients.producer.Producer.class)));
    }

    @Test
    void producerHasReadyNodesShouldFailOpenForNullProducer() {
        assertTrue(KafkaNetworkHealthHelper.producerHasReadyNodes(null));
    }

    @Test
    void consumerHasReadyNodesShouldResolveClassicAndAsyncLayoutsWithoutException() {
        Properties classicProps = consumerProps("classic");
        Properties asyncProps = consumerProps("consumer");

        try (KafkaConsumer<String, String> classic = new KafkaConsumer<>(classicProps);
             KafkaConsumer<String, String> async = new KafkaConsumer<>(asyncProps)) {
            assertDoesNotThrow(() -> KafkaNetworkHealthHelper.consumerHasReadyNodes(classic));
            assertDoesNotThrow(() -> KafkaNetworkHealthHelper.consumerHasReadyNodes(async));
        }
    }

    @Test
    void producerHasReadyNodesShouldResolveLayoutWithoutException() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            assertDoesNotThrow(() -> KafkaNetworkHealthHelper.producerHasReadyNodes(producer));
        }
    }

    private static Properties consumerProps(String groupProtocol) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "health-check-test");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, groupProtocol);
        return props;
    }
}
