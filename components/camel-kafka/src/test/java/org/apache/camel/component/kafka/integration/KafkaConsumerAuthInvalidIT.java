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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on Github CI")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaConsumerAuthInvalidIT extends BaseEmbeddedKafkaAuthTestSupport {
    public static final String TOPIC = "test-auth-invalid-it";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerAuthInvalidIT.class);

    @EndpointInject("mock:result")
    private MockEndpoint to;

    @EndpointInject("mock:dlq")
    private MockEndpoint dlq;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

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
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC)).all();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                final String simpleSaslJaasConfig
                        = ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig("camel", "camel-invalid-secret");

                getCamelContext().adapt(ExtendedCamelContext.class)
                        .setErrorHandlerFactory(
                                deadLetterChannel(dlq));

                fromF("kafka:%s"
                      + "?groupId=%s&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true"
                      + "&saslMechanism=PLAIN&securityProtocol=SASL_PLAINTEXT&saslJaasConfig=%s", TOPIC,
                        "KafkaConsumerAuthInvalidIT", simpleSaslJaasConfig)
                                .process(
                                        exchange -> LOG.trace("Captured on the processor: {}", exchange.getMessage().getBody()))
                                .routeId("should-no-work").to(to);
            }
        };
    }

    @DisplayName("Tests that Camel can adequately handle invalid authorizations")
    @Timeout(30)
    @Test
    public void kafkaMessageIsConsumedByCamel() throws InterruptedException {
        dlq.expectedMessageCount(1);
        dlq.assertIsSatisfied(3000);

        to.expectedMessageCount(0);
        to.assertIsSatisfied(3000);

        final Map<String, ConsumerGroupDescription> allGroups
                = assertDoesNotThrow(() -> getConsumerGroupInfo("KafkaConsumerAuthInvalidIT"));
        final ConsumerGroupDescription groupInfo = allGroups.get("KafkaConsumerAuthInvalidIT");

        Assert.assertEquals("There should be no members in this group", 0, groupInfo.members().size());

        for (Exchange exchange : dlq.getExchanges()) {
            Assertions.assertEquals("should-no-work", exchange.getFromRouteId());
        }
    }

}
