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
package org.apache.camel.component.kafka.share;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ShareConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.kafka.share.RecordingShareConsumer.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The pollOnError strategies and the health check, with share consumers that fail on demand instead of a broker.
 */
class KafkaShareConsumerErrorTest extends CamelTestSupport {

    private static final String TOPIC = "orders";

    // the share consumers created for each share group, in order
    private final Map<String, List<RecordingShareConsumer>> consumers = new ConcurrentHashMap<>();
    // how many times the share consumer of the "unreachable" group fails to be created
    private final AtomicInteger creationFailures = new AtomicInteger(3);
    // how many times the share consumer of the "never-created" group failed to be created
    private final AtomicInteger neverCreatedAttempts = new AtomicInteger();

    private final KafkaShareClientFactory factory = new KafkaShareClientFactory() {
        @Override
        public ShareConsumer<Object, Object> getShareConsumer(Properties kafkaProps) {
            String groupId = kafkaProps.getProperty(ConsumerConfig.GROUP_ID_CONFIG);
            if ("unreachable".equals(groupId) && creationFailures.getAndDecrement() > 0) {
                throw new KafkaException("Failed to construct kafka share consumer");
            }
            if ("never-created".equals(groupId)) {
                neverCreatedAttempts.incrementAndGet();
                throw new KafkaException("Failed to construct kafka share consumer");
            }
            RecordingShareConsumer consumer = new RecordingShareConsumer();
            List<RecordingShareConsumer> created = consumers.computeIfAbsent(groupId, g -> new CopyOnWriteArrayList<>());
            if ("subscribe-fails".equals(groupId) && created.isEmpty()) {
                // the first share consumer cannot subscribe
                consumer.failSubscribe(new KafkaException("Failed to subscribe"));
            }
            created.add(consumer);
            return consumer;
        }

        @Override
        public String getBrokers(KafkaShareConfiguration configuration) {
            return "localhost:9092";
        }
    };

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        KafkaShareComponent component = context.getComponent("kafka-share", KafkaShareComponent.class);
        component.setKafkaShareClientFactory(factory);
        // retry creating the share consumer quickly
        component.setCreateConsumerBackoffInterval(100);

        HealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setCamelContext(context);
        // report the checks that are UP as well
        registry.setExposureLevel("full");
        registry.register(registry.resolveById("consumers"));
        context.getCamelContextExtension().addContextPlugin(HealthCheckRegistry.class, registry);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String options = "?brokers=localhost:9092&pollTimeoutMs=100&groupId=";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share:" + TOPIC + options + "reconnect&pollOnError=RECONNECT").routeId("reconnect")
                        .to("mock:reconnect");

                from("kafka-share:" + TOPIC + options + "stop&pollOnError=STOP").routeId("stop")
                        .to("mock:stop");

                from("kafka-share:" + TOPIC + options + "error-handler").routeId("errorHandler")
                        .errorHandler(deadLetterChannel("mock:dead"))
                        .to("mock:result");

                from("kafka-share:" + TOPIC + options + "unreachable").routeId("unreachable")
                        .to("mock:unreachable");

                from("kafka-share:" + TOPIC + options + "subscribe-fails").routeId("subscribeFails")
                        .to("mock:subscribe-fails");

                from("kafka-share:" + TOPIC + options + "never-created").routeId("neverCreated")
                        .errorHandler(deadLetterChannel("mock:never-created-dead"))
                        .to("mock:never-created");
            }
        };
    }

    @Test
    void pollErrorWithReconnectCreatesANewShareConsumer() throws Exception {
        RecordingShareConsumer first = subscribedConsumer("reconnect", 0);
        first.failNextPoll(new KafkaException("Forced poll failure"));

        RecordingShareConsumer second = subscribedConsumer("reconnect", 1);
        assertThat(first.isClosed()).isTrue();

        MockEndpoint mock = getMockEndpoint("mock:reconnect");
        mock.expectedBodiesReceived("after reconnect");
        second.addRecord(record(TOPIC, 0, null, "after reconnect", (short) 1));
        mock.assertIsSatisfied();
        await().atMost(10, TimeUnit.SECONDS).until(() -> !second.getAcknowledgements().isEmpty());
        assertThat(second.getAcknowledgements()).extracting(RecordingShareConsumer.Acknowledgement::type)
                .containsExactly(AcknowledgeType.ACCEPT);
    }

    @Test
    void pollErrorWithStopStopsConsuming() {
        RecordingShareConsumer consumer = subscribedConsumer("stop", 0);
        consumer.failNextPoll(new KafkaException("Forced poll failure"));

        await().atMost(10, TimeUnit.SECONDS).until(consumer::isClosed);
        HealthCheck.Result result = readiness("stop");
        assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(result.getMessage()).hasValueSatisfying(message -> assertThat(message).contains("terminated"));
        assertThat(consumers.get("stop")).hasSize(1);
    }

    @Test
    void pollErrorWithErrorHandlerIsRoutedToTheErrorHandlerAndPollingContinues() throws Exception {
        RecordingShareConsumer consumer = subscribedConsumer("error-handler", 0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);
        dead.message(0).exchangeProperty(Exchange.EXCEPTION_CAUGHT).isInstanceOf(TimeoutException.class);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("after the error");

        consumer.failNextPoll(new TimeoutException("Forced poll timeout"));
        dead.assertIsSatisfied();
        consumer.addRecord(record(TOPIC, 0, null, "after the error", (short) 1));

        result.assertIsSatisfied();
        assertThat(consumers.get("error-handler")).hasSize(1);
    }

    @Test
    void healthCheckIsDownWhileTheShareConsumerCannotBeCreated() {
        // the first attempts to create the share consumer fail
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            HealthCheck.Result result = readiness("unreachable");
            assertThat(result.getState()).isEqualTo(HealthCheck.State.DOWN);
            assertThat(result.getError()).hasValueSatisfying(
                    error -> assertThat(error).hasMessageContaining("Failed to construct kafka share consumer"));
            assertThat(result.getDetails())
                    .containsEntry("group.id", "unreachable")
                    .containsEntry("topic", TOPIC)
                    .containsEntry("route.id", "unreachable")
                    .containsEntry("bootstrap.servers", "localhost:9092");
        });

        // then it is created and subscribed
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(readiness("unreachable").getState()).isEqualTo(HealthCheck.State.UP));
    }

    @Test
    void shareConsumerThatFailsToSubscribeIsClosedAndCreatedAgain() throws Exception {
        RecordingShareConsumer second = subscribedConsumer("subscribe-fails", 1);
        assertThat(consumers.get("subscribe-fails").get(0).isClosed()).isTrue();

        MockEndpoint mock = getMockEndpoint("mock:subscribe-fails");
        mock.expectedBodiesReceived("after subscribing");
        second.addRecord(record(TOPIC, 0, null, "after subscribing", (short) 1));
        mock.assertIsSatisfied();
    }

    @Test
    void stoppingWhileTheShareConsumerIsBeingCreatedDoesNotPoll() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:never-created-dead");
        dead.expectedMessageCount(0);
        // the share consumer keeps failing to be created, and the consumer retries in the background
        await().atMost(10, TimeUnit.SECONDS).until(() -> neverCreatedAttempts.get() >= 2);

        context.getRouteController().stopRoute("neverCreated");

        // no poll error (such as polling a share consumer that was never created) reaches the error handler
        dead.setAssertPeriod(1000);
        dead.assertIsSatisfied();
    }

    private HealthCheck.Result readiness(String routeId) {
        // the consumers health check repository exposes the health check of the consumer of each route
        return HealthCheckHelper.invokeReadiness(context).stream()
                .filter(result -> result.getCheck().getId().equals("consumer:" + routeId))
                .findFirst().orElseThrow(() -> new AssertionError("No health check for the consumer of route " + routeId));
    }

    private RecordingShareConsumer subscribedConsumer(String groupId, int index) {
        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            List<RecordingShareConsumer> created = consumers.get(groupId);
            return created != null && created.size() > index && created.get(index).subscription().contains(TOPIC);
        });
        return consumers.get(groupId).get(index);
    }
}
