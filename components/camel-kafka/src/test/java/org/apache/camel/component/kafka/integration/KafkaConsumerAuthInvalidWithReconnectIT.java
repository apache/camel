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

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.mock.MockEndpoint;
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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaConsumerAuthInvalidWithReconnectIT extends AbstractKafkaTestSupport {
    public static final String TOPIC = "test-auth-invalid-with-reconnect";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerAuthInvalidWithReconnectIT.class);

    private static ContainerLocalAuthKafkaService service;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    @EndpointInject("mock:dlq")
    private MockEndpoint dlq;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    static {
        service = new ContainerLocalAuthKafkaService(
                new ContainerLocalAuthKafkaService.StaticKafkaContainer("/kafka-jaas-invalid.config"));
    }

    @BeforeAll
    public static void beforeClass() {
        service.initialize();
        AbstractKafkaTestSupport.setServiceProperties(service);
    }

    protected Properties getDefaultProperties() {
        return getDefaultProperties(service);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createCamelContextFromService(service);
    }

    protected static String getBootstrapServers() {
        return service.getBootstrapServers();
    }

    @BeforeEach
    public void before() {

        Properties props = getDefaultProperties();
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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                final String simpleSaslJaasConfig
                        = ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig("camel", "camel-secret");

                fromF("kafka:%s"
                      + "?groupId=%s&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true&pollOnError=RECONNECT"
                      + "&saslMechanism=PLAIN&securityProtocol=SASL_PLAINTEXT&saslJaasConfig=%s", TOPIC,
                        "KafkaConsumerAuthInvalidWithReconnectIT", simpleSaslJaasConfig)
                                .process(
                                        exchange -> LOG.trace("Captured on the processor: {}", exchange.getMessage().getBody()))
                                .routeId("full-it").to(to);
            }
        };
    }

    private Map<String, ConsumerGroupDescription> getConsumerGroupInfo(AdminClient adminClient, String groupId)
            throws InterruptedException, ExecutionException, TimeoutException {
        return adminClient.describeConsumerGroups(Collections.singletonList(groupId)).all().get(30, TimeUnit.SECONDS);
    }

    @Test
    @Order(1)
    void testIsDisconnected() {
        AdminClient adminClient = BaseEmbeddedKafkaAuthTestSupport.createAuthAdminClient(service);

        final Map<String, ConsumerGroupDescription> allGroups
                = assertDoesNotThrow(() -> getConsumerGroupInfo(adminClient, "KafkaConsumerAuthInvalidWithReconnectIT"));
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

        AdminClient adminClient = BaseEmbeddedKafkaAuthTestSupport.createAuthAdminClient(service);
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertIsConnected(adminClient));
        adminClient.close();
    }

    private void assertIsConnected(AdminClient adminClient) {
        final Map<String, ConsumerGroupDescription> allGroups
                = assertDoesNotThrow(() -> getConsumerGroupInfo(adminClient, "KafkaConsumerAuthInvalidWithReconnectIT"));

        Assert.assertTrue("There should be at least one group named KafkaConsumerAuthInvalidWithReconnectIT",
                allGroups.size() >= 1);

        final ConsumerGroupDescription groupInfo = allGroups.get("KafkaConsumerAuthInvalidWithReconnectIT");
        Assert.assertNotNull("There should be at least one group named KafkaConsumerAuthInvalidWithReconnectIT", groupInfo);
    }
}
