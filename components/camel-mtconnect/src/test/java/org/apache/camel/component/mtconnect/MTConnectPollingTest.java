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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/** streaming=false: the same protocol, one request per scheduled poll. */
class MTConnectPollingTest extends MTConnectTestSupport {

    private static final String POLLING = "streaming=false&delay=50&readTimeout=500";
    private static final int TRIAL_ITEMS = 11;

    @Test
    void observationsArriveOnceInSequenceOrderFromSampleRequestsWithoutAnInterval() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = polling(agent, "count=4");
        List<Long> added = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            added.add(agent.set(i % 2 == 0 ? "x_load" : "exec", Integer.toString(i)));
        }

        await().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(ReceivedMessages.Received::sequence).containsExactlyElementsOf(added));
        assertThat(agent.requests()).filteredOn(request -> request.contains("/sample"))
                .allSatisfy(request -> assertThat(request).startsWith("/Trial/sample?from=").endsWith("&count=4"));
    }

    @Test
    void anOutageTurnsEveryItemBadOnceAndRecoveryResumesFromTheCursorWithoutDuplicates() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = polling(agent, "");
        long before = agent.set("x_load", "1");
        await().untilAsserted(() -> assertThat(received.matching(m -> Objects.equals(m.sequence(), before))).hasSize(1));

        long paused = System.nanoTime();
        agent.pause();
        await().untilAsserted(() -> assertThat(outage(received)).hasSize(TRIAL_ITEMS));
        assertThat(Duration.ofNanos(System.nanoTime() - paused).toMillis()).isLessThan(500 + 50 + 1000);
        assertThat(outage(received)).allSatisfy(m -> assertThat(m.headers())
                .containsEntry(MTConnectConstants.REASON, MTConnectConstants.REASON_UNREACHABLE)
                .doesNotContainKey(MTConnectConstants.SEQUENCE));
        // polls while the agent stays silent report nothing more
        await().during(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(5))
                .until(() -> outage(received).size() == TRIAL_ITEMS);
        long missed = agent.set("x_load", "2");
        agent.resume();

        await().untilAsserted(() -> {
            Map<String, ReceivedMessages.Received> last = new HashMap<>();
            received.all().forEach(m -> last.put(m.dataItemId(), m));
            assertThat(last.values()).hasSize(TRIAL_ITEMS).noneMatch(m -> m.sequence() == null);
        });
        assertThat(received.of("x_load")).filteredOn(m -> m.sequence() != null && m.sequence() >= missed)
                .extracting(ReceivedMessages.Received::sequence, ReceivedMessages.Received::sample)
                .containsExactly(tuple(missed, true));
        assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(ReceivedMessages.Received::sequence).doesNotHaveDuplicates();
    }

    @Test
    void anOverrunResynchronisesFromCurrent() throws Exception {
        StubAgent agent = agent(StubDialect.V1_3);
        ReceivedMessages received = consume("mtconnect:" + agent.url() + "?device=MACHINE-01&" + POLLING);
        await().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::current)).isNotEmpty());
        long cursor = agent.nextSequence();

        agent.overrun("xl");
        long latest = agent.nextSequence() - 1;

        await().untilAsserted(() -> assertThat(received.of("xl")).last()
                .satisfies(m -> assertThat(m.sequence()).isEqualTo(latest))
                .satisfies(m -> assertThat(m.header(MTConnectConstants.RESYNC))
                        .isEqualTo(MTConnectConstants.RESYNC_OUT_OF_RANGE)));
        assertThat(received.all()).noneMatch(m -> m.sequence() != null && m.sequence() >= cursor
                && m.sequence() < latest);
    }

    @Test
    void anAgentRestartIsProbedAgainAndCarriesTheNewInstanceId() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = polling(agent, "");

        agent.restart();

        await().untilAsserted(() -> assertThat(received.matching(m -> MTConnectConstants.RESYNC_INSTANCE_CHANGED
                .equals(m.header(MTConnectConstants.RESYNC)))).hasSize(TRIAL_ITEMS)
                .allSatisfy(m -> assertThat(m.header(MTConnectConstants.INSTANCE_ID)).isEqualTo(agent.instanceId())));
        assertThat(agent.requests()).filteredOn(request -> request.endsWith("/probe")).hasSize(2);
        long next = agent.set("x_load", "3");
        await().untilAsserted(() -> assertThat(received.of("x_load")).last()
                .satisfies(m -> assertThat(m.sequence()).isEqualTo(next)));
    }

    /** Starts a polling route from the device Trial and waits for the current value of every item. */
    private ReceivedMessages polling(StubAgent agent, String options) throws Exception {
        ReceivedMessages received = consume("mtconnect:" + agent.url() + "?device=Trial&" + POLLING
                                            + (options.isEmpty() ? "" : "&" + options));
        await().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::current))
                .hasSize(TRIAL_ITEMS));
        return received;
    }

    private static List<ReceivedMessages.Received> outage(ReceivedMessages received) {
        return received.matching(m -> m.bad() && m.sequence() == null);
    }
}
