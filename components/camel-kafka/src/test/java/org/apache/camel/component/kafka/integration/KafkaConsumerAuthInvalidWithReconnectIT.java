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

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.common.config.SaslConfigs;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A KafkaContainer that supports JAAS+SASL based authentication
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kafka.instance.type", matches = "local-kafka3-container",
                                 disabledReason = "Requires Kafka 3.x"),
        @EnabledIfSystemProperty(named = "kafka.instance.type", matches = "kafka", disabledReason = "Requires Kafka 3.x")
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaConsumerAuthInvalidWithReconnectIT {
    public static final String TOPIC = "test-auth-invalid-with-reconnect";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerAuthInvalidWithReconnectIT.class);

    private static ContainerLocalAuthKafkaService service;

    @RegisterExtension
    private static final CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    static {
        service = new ContainerLocalAuthKafkaService(
                new ContainerLocalAuthKafkaService.StaticKafkaContainer("/kafka-jaas-invalid.config"));
    }

    @BeforeAll
    public static void beforeClass() {
        service.initialize();
        KafkaTestUtil.setServiceProperties(service);
    }

    protected static String getBootstrapServers() {
        return service.getBootstrapServers();
    }

    @BeforeEach
    public void before() {

        Properties props = KafkaTestUtil.getDefaultProperties(service);
        props.put(SaslConfigs.SASL_JAAS_CONFIG,
                ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig("camel", "camel-secret"));
        props.put("security.protocol", "SASL_PLAINTEXT");
        props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");

        try {
            producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        MockConsumerInterceptor.recordsCaptured.clear();
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
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
                        = ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig("camel", "camel-secret");

                fromF("kafka:%s?brokers=%s"
                      + "&groupId=%s&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true&pollOnError=RECONNECT"
                      + "&saslMechanism=PLAIN&securityProtocol=SASL_PLAINTEXT&saslJaasConfig=%s", TOPIC,
                        service.getBootstrapServers(),
                        "KafkaConsumerAuthInvalidWithReconnectIT", simpleSaslJaasConfig)
                        .process(
                                exchange -> LOG.trace("Captured on the processor: {}", exchange.getMessage().getBody()))
                        .routeId("full-it").to(KafkaTestUtil.MOCK_DLQ);
            }
        };
    }

    @Test
    @Order(1)
    void testIsDisconnected() {
        AdminClient adminClient = KafkaAdminUtil.createAuthAdminClient(service);

        final Map<String, ConsumerGroupDescription> allGroups
                = assertDoesNotThrow(
                        () -> KafkaAdminUtil.getConsumerGroupInfo("KafkaConsumerAuthInvalidWithReconnectIT", adminClient));
        final ConsumerGroupDescription groupInfo = allGroups.get("KafkaConsumerAuthInvalidWithReconnectIT");

        Assert.assertEquals("There should be no members in this group", 0, groupInfo.members().size());
        adminClient.close();
    }

    @Test
    @Order(2)
    void testReconnect() {
        // Shutdown the instance with the auth config that wouldn't allow it to login
        service.shutdown();

        // Create a new one with the matching login credentials
        service = new ContainerLocalAuthKafkaService(
                new ContainerLocalAuthKafkaService.StaticKafkaContainer("/kafka-jaas.config"));

        service.initialize();

        AdminClient adminClient = KafkaAdminUtil.createAuthAdminClient(service);
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertIsConnected(adminClient));
        adminClient.close();
    }

    private void assertIsConnected(AdminClient adminClient) {
        final Map<String, ConsumerGroupDescription> allGroups
                = assertDoesNotThrow(
                        () -> KafkaAdminUtil.getConsumerGroupInfo("KafkaConsumerAuthInvalidWithReconnectIT", adminClient));

        Assert.assertTrue("There should be at least one group named KafkaConsumerAuthInvalidWithReconnectIT",
                allGroups.size() >= 1);

        final ConsumerGroupDescription groupInfo = allGroups.get("KafkaConsumerAuthInvalidWithReconnectIT");
        Assert.assertNotNull("There should be at least one group named KafkaConsumerAuthInvalidWithReconnectIT", groupInfo);
    }
}
