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
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/** What a route receives when the agent cannot be read, and after it answers again. */
class MTConnectOutageTest extends MTConnectTestSupport {

    private static final List<String> TRIAL_ITEMS = List.of(
            "avail", "x_load", "x_pos", "x_servo", "y_load", "y_pos", "y_servo", "sys_cond", "temp", "exec", "pcount");

    @Test
    void anUnreachableAgentTurnsEveryItemBadOnceThenEveryItemGoodWithoutDuplicates() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        setEveryItem(agent);
        await().untilAsserted(() -> assertThat(lastPerItem(received).values()).allMatch(ReceivedMessages.Received::good));
        int before = received.size();

        long paused = System.nanoTime();
        agent.pause();
        await().untilAsserted(() -> assertThat(unreachable(received)).hasSize(TRIAL_ITEMS.size()));
        long detected = Duration.ofNanos(System.nanoTime() - paused).toMillis();
        assertThat(detected).isLessThanOrEqualTo(2 * HEARTBEAT + CONNECT_TIMEOUT + 500);
        // retries while the agent stays silent report nothing more
        await().during(Duration.ofMillis(2 * (CONNECT_TIMEOUT + RECONNECT_DELAY)))
                .atMost(Duration.ofSeconds(5))
                .until(() -> unreachable(received).size() == TRIAL_ITEMS.size());
        long missed = agent.set("x_load", "42");
        agent.resume();

        await().untilAsserted(() -> assertThat(lastPerItem(received).values()).allMatch(ReceivedMessages.Received::good));
        List<ReceivedMessages.Received> after = received.all().subList(before, received.size());
        assertThat(after.stream().filter(ReceivedMessages.Received::bad))
                .allMatch(m -> MTConnectConstants.REASON_UNREACHABLE.equals(m.header(MTConnectConstants.REASON)))
                .allMatch(m -> m.body() == null && m.sequence() == null);
        assertThat(after).filteredOn(m -> "x_load".equals(m.dataItemId()) && m.good())
                .extracting(ReceivedMessages.Received::sequence, ReceivedMessages.Received::sample)
                .containsExactly(tuple(missed, true));
        // the refresh repeats the last observation of an unchanged item once, as a Current message; the catch-up and
        // the refresh never deliver the same observation twice
        assertThat(after.stream().map(ReceivedMessages.Received::sequence).filter(Objects::nonNull).toList())
                .doesNotHaveDuplicates();
        assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(ReceivedMessages.Received::sequence).doesNotHaveDuplicates();
    }

    @Test
    void aRefusedConnectionTurnsEveryItemBadAtOnce() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        setEveryItem(agent);
        await().untilAsserted(() -> assertThat(lastPerItem(received).values()).allMatch(ReceivedMessages.Received::good));

        long refused = System.nanoTime();
        agent.refuse();
        await().untilAsserted(() -> assertThat(unreachable(received)).hasSize(TRIAL_ITEMS.size()));
        assertThat(Duration.ofNanos(System.nanoTime() - refused).toMillis()).isLessThan(CONNECT_TIMEOUT + 500);
        agent.accept();

        await().untilAsserted(() -> assertThat(lastPerItem(received).values()).allMatch(ReceivedMessages.Received::good));
    }

    @Test
    void aProxyErrorPageIsReportedAsErrorWithItsStatusCode() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        setEveryItem(agent);
        await().untilAsserted(() -> assertThat(lastPerItem(received).values()).allMatch(ReceivedMessages.Received::good));

        agent.answer(StubAgent.Request.SAMPLE, StubAgent.Answer.html(502));
        agent.closeStreams();
        await().untilAsserted(() -> assertThat(received.matching(m -> m.bad() && m.sequence() == null))
                .hasSize(TRIAL_ITEMS.size())
                .allSatisfy(m -> assertThat(m.headers())
                        .containsEntry(MTConnectConstants.REASON, MTConnectConstants.REASON_ERROR)
                        .containsEntry(MTConnectConstants.ERROR, "HTTP 502")));
        agent.clearAnswers();

        await().untilAsserted(() -> assertThat(lastPerItem(received).values()).allMatch(ReceivedMessages.Received::good));
    }

    @Test
    void aSampleAnswerWithoutStreamingIsReportedAsErrorAndNotDelivered() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        setEveryItem(agent);
        await().untilAsserted(() -> assertThat(lastPerItem(received).values()).allMatch(ReceivedMessages.Received::good));

        agent.answerSampleWithoutStreaming();
        agent.closeStreams();
        long missed = agent.set("x_load", "42");
        await().untilAsserted(() -> assertThat(received.matching(m -> m.bad() && m.sequence() == null))
                .hasSize(TRIAL_ITEMS.size())
                .allSatisfy(m -> assertThat(m.headers())
                        .containsEntry(MTConnectConstants.REASON, MTConnectConstants.REASON_ERROR)
                        .containsEntry(MTConnectConstants.ERROR,
                                "The agent answered the sample request without streaming; read it with streaming=false")));
        assertThat(received.matching(m -> Objects.equals(m.sequence(), missed))).isEmpty();
        agent.clearAnswers();

        await().untilAsserted(() -> assertThat(received.matching(m -> Objects.equals(m.sequence(), missed)))
                .singleElement().matches(ReceivedMessages.Received::sample));
    }

    @Test
    void anOutageBeforeTheFirstProbeEmitsNothing() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.refuse();
        ReceivedMessages received = consume("mtconnect:" + agent.url() + "?device=Trial&" + FAST);

        await().during(Duration.ofMillis(3 * RECONNECT_DELAY)).atMost(Duration.ofSeconds(5))
                .until(() -> received.size() == 0);
        agent.accept();
        await().untilAsserted(() -> assertThat(received.size()).isEqualTo(TRIAL_ITEMS.size()));
    }

    private static void setEveryItem(StubAgent agent) {
        for (String id : TRIAL_ITEMS) {
            if (agent.model().item(id).condition()) {
                agent.condition(id, "Normal", null, null, null, null);
            } else {
                agent.set(id, id.equals("avail") ? "AVAILABLE" : "1");
            }
        }
    }

    private static List<ReceivedMessages.Received> unreachable(ReceivedMessages received) {
        return received.matching(
                m -> MTConnectConstants.REASON_UNREACHABLE.equals(m.header(MTConnectConstants.REASON)));
    }

    private static Map<String, ReceivedMessages.Received> lastPerItem(ReceivedMessages received) {
        Map<String, ReceivedMessages.Received> last = new HashMap<>();
        received.all().forEach(m -> last.put(m.dataItemId(), m));
        assertThat(last.keySet()).containsExactlyInAnyOrderElementsOf(TRIAL_ITEMS);
        return last;
    }
}
