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
package org.apache.camel.component.kafka.integration;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test for the simplified saslAuthType authentication configuration for the Kafka producer.
 * <p>
 * This test validates that the new saslAuthType parameter works correctly when producing messages to an authenticated
 * Kafka instance using the simplified configuration approach.
 * </p>
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kafka.instance.type", matches = "local-kafka3-container",
                                 disabledReason = "Requires Kafka 3.x"),
        @EnabledIfSystemProperty(named = "kafka.instance.type", matches = "kafka", disabledReason = "Requires Kafka 3.x")
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaProducerSaslAuthTypeIT {
    public static final String TOPIC = "test-producer-sasl-auth-type";

    @Order(1)
    @RegisterExtension
    public static ContainerLocalAuthKafkaService service = new ContainerLocalAuthKafkaService("/kafka-jaas.config");

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    protected static AdminClient kafkaAdminClient;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerSaslAuthTypeIT.class);

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    public void before() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Consumer uses traditional config to receive test messages
        props.put(SaslConfigs.SASL_JAAS_CONFIG,
                ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig("camel", "camel-secret"));
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");

        try {
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList(TOPIC));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = KafkaAdminUtil.createAdminClient(service);
        }
    }

    @AfterEach
    public void after() {
        if (consumer != null) {
            consumer.close();
        }
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC)).all();
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                // Route that uses the Camel producer with saslAuthType
                from("direct:start")
                        .toF("kafka:%s"
                             + "?brokers=%s"
                             + "&saslAuthType=PLAIN"
                             + "&saslUsername=camel"
                             + "&saslPassword=camel-secret"
                             + "&securityProtocol=SASL_PLAINTEXT",
                                TOPIC,
                                service.getBootstrapServers())
                        .routeId("producer-sasl-auth-type-it");
            }
        };
    }

    @DisplayName("Tests that Camel producer can send messages to Kafka using the simplified saslAuthType configuration")
    @Timeout(30)
    @Order(1)
    @Test
    public void kafkaProducerWithSaslAuthType() throws InterruptedException {
        ProducerTemplate producerTemplate = contextExtension.getProducerTemplate();

        // Send messages using Camel producer with saslAuthType
        for (int i = 0; i < 5; i++) {
            producerTemplate.sendBodyAndHeader("direct:start", "test-message-" + i, KafkaConstants.KEY, "key-" + i);
        }

        // Allow some time for messages to be sent
        Thread.sleep(2000);

        // Consume messages with native Kafka consumer to verify they were sent correctly
        int messageCount = 0;
        int maxAttempts = 10;
        int attempt = 0;

        while (messageCount < 5 && attempt < maxAttempts) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                LOG.info("Received message: key={}, value={}", record.key(), record.value());
                assertNotNull(record.value());
                assertEquals("test-message-" + messageCount, record.value());
                assertEquals("key-" + messageCount, record.key());
                messageCount++;
            }
            attempt++;
        }

        assertEquals(5, messageCount, "Should have received 5 messages");
    }

    @DisplayName("Tests that Camel producer can send messages with headers using saslAuthType")
    @Timeout(30)
    @Order(2)
    @Test
    public void kafkaProducerWithSaslAuthTypeAndHeaders() throws InterruptedException {
        ProducerTemplate producerTemplate = contextExtension.getProducerTemplate();

        // Send a message with custom headers
        producerTemplate.sendBodyAndHeaders("direct:start", "message-with-headers",
                java.util.Map.of(
                        KafkaConstants.KEY, "header-test-key",
                        "CustomHeader", "custom-value"));

        // Allow some time for messages to be sent
        Thread.sleep(2000);

        // Consume the message
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(5000));

        assertEquals(1, records.count(), "Should have received 1 message");

        ConsumerRecord<String, String> record = records.iterator().next();
        assertEquals("message-with-headers", record.value());
        assertEquals("header-test-key", record.key());
    }
}
