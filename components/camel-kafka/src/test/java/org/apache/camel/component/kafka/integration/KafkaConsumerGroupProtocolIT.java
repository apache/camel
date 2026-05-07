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
import java.util.stream.StreamSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.FeatureMetadata;
import org.apache.kafka.clients.admin.FinalizedVersionRange;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the KIP-848 consumer rebalance protocol (group.protocol=consumer). Verifies that Camel's Kafka
 * consumer works correctly with the new consumer protocol, which does not use classic-only properties like
 * heartbeat.interval.ms, session.timeout.ms, and partition.assignment.strategy.
 *
 * This test requires a Kafka 4.0+ broker with the new group coordinator enabled (group.version >= 1).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KafkaConsumerGroupProtocolIT extends BaseKafkaTestSupport {
    public static final String TOPIC = "test-group-protocol-" + Uuid.randomUuid();

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerGroupProtocolIT.class);

    private static final String FROM_URI = "kafka:" + TOPIC
                                           + "?groupId=KafkaConsumerGroupProtocolIT&autoOffsetReset=earliest"
                                           + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                                           + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                                           + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true"
                                           + "&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor"
                                           + "&groupProtocol=consumer";

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeAll
    static void checkConsumerProtocolSupport() {
        try (AdminClient adminClient = KafkaAdminUtil.createAdminClient(service)) {
            FeatureMetadata metadata = adminClient.describeFeatures().featureMetadata().get(10, TimeUnit.SECONDS);
            Map<String, FinalizedVersionRange> finalizedFeatures = metadata.finalizedFeatures();
            FinalizedVersionRange groupVersion = finalizedFeatures.get("group.version");
            Assumptions.assumeTrue(
                    groupVersion != null && groupVersion.maxVersionLevel() >= 1,
                    "Broker does not support the consumer group protocol (KIP-848), requires Kafka 4.0+ with group.version >= 1");
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                    "Could not determine broker feature support: " + e.getMessage());
        }
    }

    @BeforeEach
    public void before() {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        MockConsumerInterceptor.recordsCaptured.clear();
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(FROM_URI)
                        .process(exchange -> LOG.trace("Captured on the processor: {}", exchange.getMessage().getBody()))
                        .routeId("group-protocol-it").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @Test
    public void kafkaMessageIsConsumedWithConsumerProtocol() throws InterruptedException {
        String propagatedHeaderKey = "PropagatedCustomHeader";
        byte[] propagatedHeaderValue = "propagated header value".getBytes();
        String skippedHeaderKey = "CamelSkippedHeader";

        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);

        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedHeaderReceived(propagatedHeaderKey, propagatedHeaderValue);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            data.headers().add(new RecordHeader("CamelSkippedHeader", "skipped header value".getBytes()));
            data.headers().add(new RecordHeader(propagatedHeaderKey, propagatedHeaderValue));
            producer.send(data);
        }

        to.assertIsSatisfied(3000);

        assertEquals(5, MockConsumerInterceptor.recordsCaptured.stream()
                .flatMap(i -> StreamSupport.stream(i.records(TOPIC).spliterator(), false)).count());

        java.util.Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertFalse(headers.containsKey(skippedHeaderKey), "Should not receive skipped header");
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
    }
}
