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

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.kafka.common.config.SaslConfigs;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "bootstrapServers", matches = ".*"),
        @EnabledIfSystemProperty(named = "kafka.manual.test.topic", matches = ".*"),
        @EnabledIfSystemProperty(named = "kafka.manual.test.username", matches = ".*"),
        @EnabledIfSystemProperty(named = "kafka.manual.test.password", matches = ".*"),
})
public class KafkaConsumerAuthManualTest {
    private static final String TOPIC = System.getProperty("kafka.manual.test.topic");
    private static final String BOOTSTRAP_SERVERS = System.getProperty("bootstrapServers");
    private static final String USERNAME = System.getProperty("kafka.manual.test.username");
    private static final String PASSWORD = System.getProperty("kafka.manual.test.password");
    private static final String SECURITY_PROTOCOL = System.getProperty("kafka.manual.test.security.protocol", "SASL_PLAINTEXT");
    private static final String SASL_MECHANISM = System.getProperty("kafka.manual.test.sasl.mechanism", "PLAIN");
    private static final int MESSAGE_COUNT = Integer.valueOf(System.getProperty("kafka.manual.test.message.count", "5"));

    @RegisterExtension
    private static final CamelContextExtension contextExtension = new DefaultCamelContextExtension();
    private static volatile int receivedMessages;
    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    protected Properties getDefaultProperties() {
        Properties properties = KafkaTestUtil.getDefaultProperties(BOOTSTRAP_SERVERS);

        properties.put(SaslConfigs.SASL_JAAS_CONFIG,
                ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig(USERNAME, PASSWORD));
        properties.put("security.protocol", SECURITY_PROTOCOL);
        properties.put(SaslConfigs.SASL_MECHANISM, SASL_MECHANISM);

        return properties;
    }

    @BeforeEach
    public void before() {
        Properties props = getDefaultProperties();

        try {
            producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                final String simpleSaslJaasConfig
                        = ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig(USERNAME, PASSWORD);

                fromF("kafka:%s?brokers=%s"
                      + "&groupId=%s&autoOffsetReset=earliest&clientId=camel-kafka-auth-test"
                      + "&pollOnError=RECONNECT"
                      + "&saslMechanism=%s&securityProtocol=%s&saslJaasConfig=%s", TOPIC, BOOTSTRAP_SERVERS,
                        "KafkaConsumerAuthManualTest", SASL_MECHANISM, SECURITY_PROTOCOL, simpleSaslJaasConfig)
                        .process(e -> receivedMessages++)
                        .routeId("full-it").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @DisplayName("Tests that Camel can adequately connect and consume from an authenticated remote Kafka instance")
    @Test
    public void kafkaMessageIsConsumedByCamel() throws InterruptedException {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedHeaderValuesReceivedInAnyOrder(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, null, null, null, null, null);

        Awaitility.await().atMost(1, TimeUnit.HOURS).untilAsserted(() -> Assert.assertEquals(MESSAGE_COUNT, receivedMessages));

        to.assertIsSatisfied();
    }
}
