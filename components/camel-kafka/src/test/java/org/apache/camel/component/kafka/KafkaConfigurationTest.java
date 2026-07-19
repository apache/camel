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

import org.apache.camel.spi.StateRepository;
import org.apache.camel.util.SecurityUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KafkaConfigurationTest {

    @Test
    void sslEndpointAlgorithmNoneDisablesHostnameVerification() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setBrokers("localhost:9092");
        config.setSecurityProtocol("SSL");
        config.setSslEndpointAlgorithm("none");

        Properties props = config.createProducerProperties();
        assertEquals("", props.get(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG));
    }

    @Test
    void sslEndpointAlgorithmFalseDisablesHostnameVerification() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setBrokers("localhost:9092");
        config.setSecurityProtocol("SSL");
        config.setSslEndpointAlgorithm("false");

        Properties consumerProps = config.createConsumerProperties();
        assertEquals("", consumerProps.get(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG));
    }

    @Test
    void sslEndpointAlgorithmNoneIsInsecureConfiguration() {
        assertTrue(SecurityUtils.isInsecureValue("camel.component.kafka.sslEndpointAlgorithm", "none"));
        assertTrue(SecurityUtils.isInsecureValue("camel.component.kafka.sslEndpointAlgorithm", "false"));
        assertFalse(SecurityUtils.isInsecureValue("camel.component.kafka.sslEndpointAlgorithm", "https"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void offsetRepositoryDisablesAutoCommit() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setBrokers("localhost:9092");
        config.setOffsetRepository(mock(StateRepository.class));

        Properties props = config.createConsumerProperties();
        assertEquals(false, props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
    }

    @Test
    void batchingDisablesAutoCommit() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setBrokers("localhost:9092");
        config.setBatching(true);

        Properties props = config.createConsumerProperties();
        assertEquals(false, props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
    }

    @Test
    void sendBufferBytesAppliedToConsumerWithoutSsl() {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setBrokers("localhost:9092");
        config.setSendBufferBytes(131072);

        Properties props = config.createConsumerProperties();
        assertEquals(131072, props.get(ConsumerConfig.SEND_BUFFER_CONFIG));
    }
}
