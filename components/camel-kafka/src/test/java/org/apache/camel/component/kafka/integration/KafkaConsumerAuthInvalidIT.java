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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
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
@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on Github CI")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaConsumerAuthInvalidIT {
    public static final String TOPIC = "test-auth-invalid-it";

    protected static AdminClient kafkaAdminClient;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerAuthInvalidIT.class);

    @Order(1)
    @RegisterExtension
    private static final KafkaService service = KafkaServiceFactory.createSingletonService();
    @Order(2)
    @RegisterExtension
    private static final CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

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

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = KafkaTestUtil.createAdminClient(service);
        }
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
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
                final String simpleSaslJaasConfig
                        = ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig("camel", "camel-invalid-secret");

                getCamelContext().getCamelContextExtension()
                        .setErrorHandlerFactory(
                                deadLetterChannel("mock:dlq"));

                fromF("kafka:%s"
                      + "?brokers=%s&groupId=%s&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true"
                      + "&saslMechanism=PLAIN&securityProtocol=SASL_PLAINTEXT&saslJaasConfig=%s", TOPIC,
                        service.getBootstrapServers(), "KafkaConsumerAuthInvalidIT", simpleSaslJaasConfig)
                        .process(
                                exchange -> LOG.trace("Captured on the processor: {}", exchange.getMessage().getBody()))
                        .routeId("should-no-work").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @DisplayName("Tests that Camel can adequately handle invalid authorizations")
    @Timeout(30)
    @Test
    public void kafkaMessageIsConsumedByCamel() throws InterruptedException {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);
        MockEndpoint dlq = contextExtension.getMockEndpoint("mock:dlq");

        dlq.expectedMessageCount(1);
        dlq.assertIsSatisfied(3000);

        to.expectedMessageCount(0);
        to.assertIsSatisfied(3000);

        final Map<String, ConsumerGroupDescription> allGroups
                = assertDoesNotThrow(() -> KafkaAdminUtil.getConsumerGroupInfo("KafkaConsumerAuthInvalidIT",
                        kafkaAdminClient));
        final ConsumerGroupDescription groupInfo = allGroups.get("KafkaConsumerAuthInvalidIT");

        Assert.assertEquals("There should be no members in this group", 0, groupInfo.members().size());

        for (Exchange exchange : dlq.getExchanges()) {
            Assertions.assertEquals("should-no-work", exchange.getFromRouteId());
        }
    }

}
