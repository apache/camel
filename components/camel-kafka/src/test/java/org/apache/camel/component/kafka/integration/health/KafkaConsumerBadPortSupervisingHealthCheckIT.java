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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.impl.engine.DefaultSupervisingRouteController;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
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
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisabledIfSystemProperty(named = "kafka.instance.type", matches = "local-strimzi-container",
                          disabledReason = "Test infra Kafka runs the Strimzi containers in a way that conflicts with multiple concurrent images")
@Tags({ @Tag("health") })
public class KafkaConsumerBadPortSupervisingHealthCheckIT extends KafkaHealthCheckTestSupport {
    public static final String TOPIC = "test-health";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerBadPortSupervisingHealthCheckIT.class);

    @ContextFixture
    @Override
    public void configureContext(CamelContext context) {
        context.getPropertiesComponent().setLocation("ref:prop");

        context.setRouteController(new DefaultSupervisingRouteController());
        SupervisingRouteController src = context.getRouteController().supervising();
        src.setBackOffDelay(3);
        src.setBackOffMaxAttempts(3);
        src.setInitialDelay(3);

        KafkaComponent kafka = new KafkaComponent(context);
        kafka.init();
        kafka.getConfiguration().setBrokers(service.getBootstrapServers() + 123);
        // turn of pre validation so we startup and let supervising route controller handle this
        // and we can see failure in health checks
        kafka.getConfiguration().setPreValidateHostAndPort(false);
        context.addComponent("kafka", kafka);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                String uri = "kafka:" + TOPIC
                             + "?groupId=KafkaConsumerBadPortSupervisingHealthCheckIT&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer&"
                             + "valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                             + "&autoCommitIntervalMs=1000&autoCommitEnable=true&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor";

                from(uri)
                        .process(exchange -> LOG.trace("Captured on the processor: {}", exchange.getMessage().getBody()))
                        .routeId("test-health-it").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
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
    @DisplayName("Tests that readiness reports down when it's actually down")
    public void testReportCorrectlyWhenDown() {
        CamelContext context = contextExtension.getContext();

        // health-check readiness should be down
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> readinessCheck(context));
    }

    private static void readinessCheck(CamelContext context) {
        Collection<HealthCheck.Result> res2 = HealthCheckHelper.invokeReadiness(context);
        boolean up2 = res2.stream().allMatch(r -> {
            return r.getState().equals(HealthCheck.State.DOWN) &&
                    r.getMessage().stream().allMatch(msg -> msg.contains("port"));
        });
        Assertions.assertTrue(up2, "readiness check");
    }

    @Order(3)
    @Test
    @DisplayName("I/O test to ensure everything is working as expected")
    public void kafkaConsumerHealthCheck() throws InterruptedException {
        String propagatedHeaderKey = "PropagatedCustomHeader";
        byte[] propagatedHeaderValue = "propagated header value".getBytes();

        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);
        to.expectedMessageCount(0);
        to.expectedMinimumMessageCount(0);
        to.expectedNoHeaderReceived();

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            data.headers().add(new RecordHeader("CamelSkippedHeader", "skipped header value".getBytes()));
            data.headers().add(new RecordHeader(propagatedHeaderKey, propagatedHeaderValue));
            producer.send(data);
        }

        to.assertIsSatisfied(3000);
    }
}
