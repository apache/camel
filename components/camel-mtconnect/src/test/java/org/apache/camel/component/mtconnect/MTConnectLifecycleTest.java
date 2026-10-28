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
package org.apache.camel.component.mtconnect;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.StatefulService;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/** Starting, stopping and identifying endpoints. */
class MTConnectLifecycleTest extends MTConnectTestSupport {

    @Test
    void stoppingTheRouteCompletesTheExchangeInFlightAndLeavesNoThread() throws Exception {
        List<Thread> threads = new CopyOnWriteArrayList<>();
        context.getExecutorServiceManager().addThreadFactoryListener((source, factory) -> runnable -> {
            Thread thread = factory.newThread(runnable);
            if (source instanceof MTConnectConsumer) {
                threads.add(thread);
            }
            return thread;
        });
        StubAgent agent = agent(StubDialect.V2_5);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Long> completed = new CopyOnWriteArrayList<>();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mtconnect:" + agent.url() + "?device=Trial&" + FAST).routeId("machine")
                        .process(exchange -> {
                            if (MTConnectConstants.DOCUMENT_SAMPLE.equals(
                                    exchange.getMessage().getHeader(MTConnectConstants.DOCUMENT))) {
                                entered.countDown();
                                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                            }
                            completed.add(exchange.getMessage().getHeader(MTConnectConstants.SEQUENCE, Long.class));
                        });
            }
        });
        await().untilAsserted(() -> assertThat(completed).hasSize(11));
        long inFlight = agent.set("x_load", "1");
        agent.set("y_load", "2");
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> stop = CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute("machine");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        StatefulService consumer = (StatefulService) context.getRoute("machine").getConsumer();
        await().until(consumer::isStopping);
        release.countDown();
        stop.get(30, TimeUnit.SECONDS);

        assertThat(completed).last().isEqualTo(inFlight);
        assertThat(threads).isNotEmpty();
        await().untilAsserted(() -> assertThat(threads).noneMatch(Thread::isAlive));
        assertThat(Thread.getAllStackTraces().keySet())
                .noneMatch(thread -> thread.getName().matches("HttpClient-\\d+-SelectorManager"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "polling consumer", "producer" })
    void stoppingThePollingConsumerOrTheProducerLeavesNoThread(String kind) throws Exception {
        boolean producer = "producer".equals(kind);
        List<Thread> threads = new CopyOnWriteArrayList<>();
        context.getExecutorServiceManager().addThreadFactoryListener((source, factory) -> runnable -> {
            Thread thread = factory.newThread(runnable);
            if (producer ? source instanceof MTConnectProducer : source instanceof MTConnectPollingConsumer) {
                threads.add(thread);
            }
            return thread;
        });
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = new ReceivedMessages();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                if (producer) {
                    from("direct:ask").routeId("machine").to("mtconnect:" + agent.url() + "?device=Trial&operation=current");
                } else {
                    from("mtconnect:" + agent.url() + "?device=Trial&streaming=false&delay=50").routeId("machine")
                            .process(received);
                }
            }
        });
        if (producer) {
            assertThat(template.requestBody("direct:ask", null, List.class)).hasSize(11);
        } else {
            await().untilAsserted(() -> assertThat(received.size()).isEqualTo(11));
        }

        context.getRouteController().stopRoute("machine");

        assertThat(threads).isNotEmpty();
        await().untilAsserted(() -> assertThat(threads).noneMatch(Thread::isAlive));
        assertThat(Thread.getAllStackTraces().keySet())
                .noneMatch(thread -> thread.getName().matches("HttpClient-\\d+-SelectorManager"));
    }

    @ParameterizedTest(name = "streaming={0}")
    @ValueSource(booleans = { true, false })
    void stoppingWhileTheAgentSendsNothingDoesNotWaitForTheTimeout(boolean streaming) throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.pause();
        long timeout = 8000;
        consume("mtconnect:" + agent.url() + "?device=Trial&streaming=" + streaming + "&delay=50&readTimeout=" + timeout,
                new ReceivedMessages());
        await().until(() -> !agent.requests().isEmpty());

        long start = System.nanoTime();
        context.getRouteController().stopRoute(context.getRoutes().get(0).getRouteId());

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(timeout / 2));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "http://localhost:1?count=0 | count must be at least 1",
            "http://localhost:1?heartbeat=0 | heartbeat must be at least 1",
            "http://localhost:1?readTimeout=0 | readTimeout must be at least 1",
            "http://localhost:1?interval=-1 | interval must be at least 0",
            "http://localhost:1?operation=read | read",
            "ftp://localhost:1 | must be an http or https URL" })
    void invalidOptionsFailTheRouteStartWithAClearMessage(String address, String message) {
        assertThatThrownBy(() -> consume("mtconnect:" + address))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    @Test
    void twoAgentsWithTheSameDeviceUuidStayApartByTheAgentHeader() throws Exception {
        StubAgent first = agent(StubDialect.V2_5);
        StubAgent second = agent(StubDialect.V2_5);
        ReceivedMessages received = new ReceivedMessages();
        consume("mtconnect:" + first.url() + "?device=Trial&" + FAST, received);
        consume("mtconnect:" + second.url() + "?device=Trial&" + FAST, received);
        await().untilAsserted(() -> assertThat(received.size()).isEqualTo(22));
        first.set("x_load", "11.5");
        second.set("x_load", "88.25");

        await().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample)).hasSize(2));
        assertThat(received.matching(ReceivedMessages.Received::sample))
                .allSatisfy(m -> assertThat(m.headers()).containsEntry(MTConnectConstants.DEVICE_UUID, "trial-001"))
                .extracting(m -> m.text(MTConnectConstants.AGENT), m -> m.body().toString())
                .containsExactlyInAnyOrder(
                        tuple(first.url(), "11.5"),
                        tuple(second.url(), "88.25"));
    }
}
