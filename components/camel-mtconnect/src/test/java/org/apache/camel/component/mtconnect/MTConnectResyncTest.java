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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Resynchronisation: a buffer overrun, an agent restart, and agent error documents. */
class MTConnectResyncTest extends MTConnectTestSupport {

    private final List<String> handled = new CopyOnWriteArrayList<>();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("collectingHandler", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(exception.getMessage());
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(message + ": " + exception.getMessage());
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(message);
            }
        });
    }

    @ParameterizedTest(name = "{0} answers OUT_OF_RANGE with {1}")
    @CsvSource({ "V2_5, 400, Trial, temp", "V1_3, 200, MACHINE-01, xl" })
    void anOverrunResynchronisesFromCurrentWithoutInventingObservations(
            StubDialect dialect, int status, String device, String item)
            throws Exception {
        assertThat(dialect.outOfRangeStatus).isEqualTo(status);
        StubAgent agent = agent(dialect);
        ReceivedMessages received = streaming(agent, device, "");
        long cursor = agent.nextSequence();

        agent.overrun(item);
        long latest = agent.nextSequence() - 1;

        awaitAgent().untilAsserted(() -> assertThat(received.matching(m -> m.header(MTConnectConstants.RESYNC) != null))
                .isNotEmpty()
                .allMatch(ReceivedMessages.Received::current)
                .allMatch(m -> MTConnectConstants.RESYNC_OUT_OF_RANGE.equals(m.header(MTConnectConstants.RESYNC))));
        // the resync current is delivered item by item; the overrun item may come after the first resync message
        awaitAgent().untilAsserted(() -> assertThat(received.of(item)).last()
                .satisfies(m -> assertThat(m.sequence()).isEqualTo(latest))
                .satisfies(m -> assertThat(m.current()).isTrue()));
        // the overwritten observations between the cursor and the current value are not invented
        assertThat(received.all()).noneMatch(m -> m.sequence() != null && m.sequence() >= cursor
                && m.sequence() < latest);
        long next = agent.set(item, "7");
        awaitAgent().untilAsserted(() -> assertThat(received.of(item)).last()
                .satisfies(m -> assertThat(m.sequence()).isEqualTo(next))
                .satisfies(m -> assertThat(m.sample()).isTrue()));
    }

    @Test
    void anAgentRestartIsProbedAgainAndCarriesTheNewInstanceId() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        long oldInstance = agent.instanceId();

        long restarted = System.nanoTime();
        agent.restart();

        awaitAgent().untilAsserted(() -> assertThat(received.matching(m -> MTConnectConstants.RESYNC_INSTANCE_CHANGED
                .equals(m.header(MTConnectConstants.RESYNC)))).hasSize(11));
        assertThat(Duration.ofNanos(System.nanoTime() - restarted).toMillis()).isLessThan(RECONNECT_DELAY + SLACK);
        assertThat(received.matching(m -> MTConnectConstants.RESYNC_INSTANCE_CHANGED
                .equals(m.header(MTConnectConstants.RESYNC))))
                .allMatch(ReceivedMessages.Received::current)
                .allSatisfy(m -> assertThat(m.header(MTConnectConstants.INSTANCE_ID)).isEqualTo(oldInstance + 1));
        assertThat(agent.requests()).filteredOn(request -> request.endsWith("/probe")).hasSize(2);
        long next = agent.set("x_load", "3");
        awaitAgent().untilAsserted(() -> assertThat(received.of("x_load")).last()
                .satisfies(m -> assertThat(m.sequence()).isEqualTo(next)));
    }

    @Test
    void aDeviceTheAgentNoLongerServesTurnsEveryItemBadOnceAndGoodWhenItReturns() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "exceptionHandler=#collectingHandler");
        List<StubDeviceModel.Item> items = agent.model().items().stream()
                .filter(item -> StubDeviceModel.matches(item, "Trial")).toList();
        for (StubDeviceModel.Item item : items) {
            if (item.condition()) {
                agent.condition(item.id(), "Normal", null, null, null, null);
            } else {
                agent.set(item.id(), "1");
            }
        }
        awaitAgent()
                .untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample)).hasSize(items.size()));

        for (StubAgent.Request request : StubAgent.Request.values()) {
            agent.answer(request, new StubAgent.Answer(404, "NO_DEVICE"));
        }
        agent.closeStreams();
        awaitAgent().untilAsserted(() -> assertThat(received.matching(m -> m.bad() && m.sequence() == null))
                .hasSize(items.size())
                .allSatisfy(m -> assertThat(m.headers())
                        .containsEntry(MTConnectConstants.REASON, MTConnectConstants.REASON_ERROR)
                        .containsEntry(MTConnectConstants.ERROR, "NO_DEVICE: Could not find the device 'Trial'")));
        // retries from the probe while the device is missing report nothing more
        await().during(Duration.ofMillis(3 * RECONNECT_DELAY)).atMost(Duration.ofSeconds(5))
                .until(() -> handled.size() == 1
                        && received.matching(m -> m.bad() && m.sequence() == null).size() == items.size());
        assertThat(handled.get(0)).contains("cannot be read", "NO_DEVICE");
        assertThat(agent.requests()).filteredOn(request -> request.endsWith("/probe")).hasSizeGreaterThan(2);
        agent.clearAnswers();

        awaitAgent().untilAsserted(() -> {
            Map<String, ReceivedMessages.Received> last = new HashMap<>();
            received.all().forEach(m -> last.put(m.dataItemId(), m));
            assertThat(last.values()).hasSize(items.size()).allMatch(ReceivedMessages.Received::good);
        });
        long next = agent.set("x_load", "4");
        awaitAgent().untilAsserted(() -> assertThat(received.of("x_load")).last()
                .satisfies(m -> assertThat(m.sequence()).isEqualTo(next)));
    }
}
