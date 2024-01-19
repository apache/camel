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
package org.apache.camel.component.kafka.integration.health;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Timeout(30)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tags({ @Tag("health") })
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
             architectures = { "amd64", "aarch64", "s390x" },
             disabledReason = "This test does not run reliably on ppc64le")
public class KafkaConsumerHealthCheckIT extends KafkaHealthCheckTestSupport {
    public static final String TOPIC = "test-health";
    public static final String SKIPPED_HEADER_KEY = "CamelSkippedHeader";
    public static final String PROPAGATED_CUSTOM_HEADER = "PropagatedCustomHeader";
    public static final byte[] PROPAGATED_HEADER_VALUE = "propagated header value".getBytes();

    protected static AdminClient kafkaAdminClient;

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerHealthCheckIT.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                String from = "kafka:" + TOPIC + "?brokers=" + service.getBootstrapServers()
                              + "&groupId=KafkaConsumerHealthCheckIT&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                              + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                              + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor";

                from(from)
                        .process(exchange -> LOG.trace("Captured on the processor: {}", exchange.getMessage().getBody()))
                        .routeId("test-health-it").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @Override
    public void configureContext(CamelContext context) {
        // NO-OP
    }

    @Order(1)
    @Test
    @DisplayName("Tests that liveness reports UP when it's actually up")
    public void testReportUpWhenIsUp() {
        // health-check liveness should be UP
        CamelContext context = contextExtension.getContext();
        Collection<HealthCheck.Result> res = HealthCheckHelper.invokeLiveness(context);
        boolean up = res.stream().allMatch(r -> r.getState().equals(HealthCheck.State.UP));
        Assertions.assertTrue(up, "liveness check");
    }

    @Order(2)
    @Test
    @DisplayName("Tests that readiness reports UP when it's actually up")
    public void testReportReadyWhenReady() {
        CamelContext context = contextExtension.getContext();
        // health-check readiness should be ready
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Collection<HealthCheck.Result> res2 = HealthCheckHelper.invokeReadiness(context);
            boolean up = res2.stream().allMatch(r -> r.getState().equals(HealthCheck.State.UP));
            Assertions.assertTrue(up, "readiness check");
        });
    }

    @Order(3)
    @Test
    @DisplayName("I/O test to ensure everything is working as expected")
    public void testIO() throws InterruptedException {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedHeaderValuesReceivedInAnyOrder(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, null, null, null, null, null);
        to.expectedHeaderReceived(PROPAGATED_CUSTOM_HEADER, PROPAGATED_HEADER_VALUE);

        Properties props = KafkaTestUtil.getDefaultProperties(service);
        try (KafkaProducer<String, String> producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props)) {
            for (int k = 0; k < 5; k++) {
                String msg = "message-" + k;
                ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
                data.headers().add(new RecordHeader("CamelSkippedHeader", "skipped header value".getBytes()));
                data.headers().add(new RecordHeader(PROPAGATED_CUSTOM_HEADER, PROPAGATED_HEADER_VALUE));
                producer.send(data);
            }
        }

        to.assertIsSatisfied(3000);
        assertEquals(5, MockConsumerInterceptor.recordsCaptured.stream()
                .flatMap(i -> StreamSupport.stream(i.records(TOPIC).spliterator(), false)).count());

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertFalse(headers.containsKey(SKIPPED_HEADER_KEY), "Should not receive skipped header");
        assertTrue(headers.containsKey(PROPAGATED_CUSTOM_HEADER), "Should receive propagated header");
    }

    @Order(4)
    @Test
    @DisplayName("Tests that liveness reports UP when it's down")
    public void testLivenessWhenDown() {
        CamelContext context = contextExtension.getContext();
        // and shutdown Kafka which will make readiness report as DOWN
        service.shutdown();
        serviceShutdown = true;

        // health-check liveness should be UP
        final Collection<HealthCheck.Result> res = HealthCheckHelper.invokeLiveness(context);
        final boolean up = res.stream().allMatch(r -> r.getState().equals(HealthCheck.State.UP));
        Assertions.assertTrue(up, "liveness check");
    }

    @Order(5)
    @Test
    @DisplayName("Tests that readiness reports down when it's actually down")
    public void testReadinessWhenDown() {
        CamelContext context = contextExtension.getContext();
        // and shutdown Kafka which will make readiness report as DOWN
        service.shutdown();
        serviceShutdown = true;

        // health-check readiness should be DOWN
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Collection<HealthCheck.Result> res2 = HealthCheckHelper.invokeReadiness(context);
            Assertions.assertTrue(res2.size() > 0);
            Optional<HealthCheck.Result> down
                    = res2.stream().filter(r -> r.getState().equals(HealthCheck.State.DOWN)).findFirst();
            Assertions.assertTrue(down.isPresent());
            String msg = down.get().getMessage().get();
            Assertions.assertTrue(msg.contains("KafkaConsumer is not ready"));
            Map<String, Object> map = down.get().getDetails();
            Assertions.assertEquals(TOPIC, map.get("topic"));
            Assertions.assertEquals("test-health-it", map.get("route.id"));
        });
    }

}
