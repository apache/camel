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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.integration.common.KafkaAdminUtil;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.FeatureMetadata;
import org.apache.kafka.clients.admin.FinalizedVersionRange;
import org.apache.kafka.common.Uuid;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;

/**
 * Readiness health check coverage for {@code group.protocol=consumer} (KIP-848 / AsyncKafkaConsumer).
 */
@Timeout(60)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledIfSystemProperty(named = "kafka.instance.type", matches = "local-strimzi-container",
                          disabledReason = "Test infra Kafka runs the Strimzi containers in a way that conflicts with multiple concurrent images")
@Tags({ @Tag("health") })
@EnabledOnOs(value = { OS.LINUX, OS.MAC, OS.FREEBSD, OS.OPENBSD, OS.WINDOWS },
             architectures = { "amd64", "aarch64", "s390x" },
             disabledReason = "This test does not run reliably on ppc64le")
public class KafkaConsumerGroupProtocolHealthCheckIT extends KafkaHealthCheckTestSupport {

    public static final String TOPIC = "test-health-group-protocol-" + Uuid.randomUuid();

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

    @Override
    public void configureContext(CamelContext context) {
        // NO-OP
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String from = "kafka:" + TOPIC + "?brokers=" + service.getBootstrapServers()
                              + "&groupId=KafkaConsumerGroupProtocolHealthCheckIT&autoOffsetReset=earliest"
                              + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                              + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                              + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true"
                              + "&groupProtocol=consumer";

                from(from)
                        .routeId("test-health-group-protocol-it")
                        .to("mock:result");
            }
        };
    }

    @Order(1)
    @Test
    @DisplayName("Readiness reports UP with group.protocol=consumer when broker is healthy")
    public void testReportReadyWhenReady() {
        CamelContext context = contextExtension.getContext();
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Collection<HealthCheck.Result> results = HealthCheckHelper.invokeReadiness(context);
            boolean up = results.stream().allMatch(r -> r.getState().equals(HealthCheck.State.UP));
            Assertions.assertTrue(up, "readiness check with async consumer protocol");
        });
    }

    @Order(2)
    @Test
    @DisplayName("Readiness reports DOWN with group.protocol=consumer when broker is shut down")
    public void testReadinessWhenDown() {
        CamelContext context = contextExtension.getContext();
        service.shutdown();
        serviceShutdown = true;

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Collection<HealthCheck.Result> results = HealthCheckHelper.invokeReadiness(context);
            Optional<HealthCheck.Result> down
                    = results.stream().filter(r -> r.getState().equals(HealthCheck.State.DOWN)).findFirst();
            Assertions.assertTrue(down.isPresent());
            String msg = down.get().getMessage().get();
            Assertions.assertTrue(msg.contains("KafkaConsumer is not ready"));
            Map<String, Object> details = down.get().getDetails();
            Assertions.assertEquals(TOPIC, details.get("topic"));
            Assertions.assertEquals("test-health-group-protocol-it", details.get("route.id"));
        });
    }
}
