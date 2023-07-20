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
import java.util.stream.StreamSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.header.internals.RecordHeader;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
public class KafkaConsumerAuthIT {
    public static final String TOPIC = "test-auth-full";

    @Order(1)
    @RegisterExtension
    public static ContainerLocalAuthKafkaService service = new ContainerLocalAuthKafkaService("/kafka-jaas.config");

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    protected static AdminClient kafkaAdminClient;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerAuthIT.class);

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
            kafkaAdminClient = KafkaAdminUtil.createAdminClient(service);
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
                        = ContainerLocalAuthKafkaService.generateSimpleSaslJaasConfig("camel", "camel-secret");

                fromF("kafka:%s"
                      + "?brokers=%s&groupId=%s&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                      + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer&clientId=camel-kafka-auth-test"
                      + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true&interceptorClasses=%s"
                      + "&saslMechanism=PLAIN&securityProtocol=SASL_PLAINTEXT&saslJaasConfig=%s", TOPIC,
                        service.getBootstrapServers(),
                        "KafkaConsumerAuthIT", "org.apache.camel.component.kafka.MockConsumerInterceptor", simpleSaslJaasConfig)
                        .process(
                                exchange -> LOG.trace("Captured on the processor: {}", exchange.getMessage().getBody()))
                        .routeId("full-it").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @DisplayName("Tests that Camel can adequately connect and consume from an authenticated Kafka instance")
    @Timeout(30)
    @Order(1)
    @Test
    public void kafkaMessageIsConsumedByCamel() throws InterruptedException {
        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        String propagatedHeaderKey = "PropagatedCustomHeader";
        byte[] propagatedHeaderValue = "propagated header value".getBytes();
        String skippedHeaderKey = "CamelSkippedHeader";
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        // The LAST_RECORD_BEFORE_COMMIT header should not be configured on any
        // exchange because autoCommitEnable=true
        to.expectedHeaderValuesReceivedInAnyOrder(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, null, null, null, null, null);
        to.expectedHeaderReceived(propagatedHeaderKey, propagatedHeaderValue);

        populateKafkaTopic(propagatedHeaderKey, propagatedHeaderValue);

        to.assertIsSatisfied(3000);

        assertEquals(5, MockConsumerInterceptor.recordsCaptured.stream()
                .flatMap(i -> StreamSupport.stream(i.records(TOPIC).spliterator(), false)).count());

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertFalse(headers.containsKey(skippedHeaderKey), "Should not receive skipped header");
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
    }

    private void populateKafkaTopic(String propagatedHeaderKey, byte[] propagatedHeaderValue) {
        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            data.headers().add(new RecordHeader("CamelSkippedHeader", "skipped header value".getBytes()));
            data.headers().add(new RecordHeader(propagatedHeaderKey, propagatedHeaderValue));
            producer.send(data);
        }
    }

}
