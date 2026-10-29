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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The streaming sample request: order, continuity, and what the consumer asks the agent for. */
class MTConnectStreamTest extends MTConnectTestSupport {

    @ParameterizedTest
    @EnumSource(value = StubDialect.class, names = { "V2_5", "V1_3" })
    void observationsArriveOnceInSequenceOrderAcrossAPauseShorterThanTheBuffer(StubDialect dialect) throws Exception {
        StubAgent agent = agent(dialect);
        String device = dialect == StubDialect.V2_5 ? "Trial" : "MACHINE-01";
        String[] items = dialect == StubDialect.V2_5
                ? new String[] { "x_load", "y_load", "exec" }
                : new String[] { "xl", "yl", "exec" };
        ReceivedMessages received = streaming(agent, device, "");
        List<Long> added = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            added.add(agent.set(items[i % items.length], Integer.toString(i)));
        }
        agent.pause();
        awaitAgent().until(() -> !received.matching(m -> MTConnectConstants.REASON_UNREACHABLE
                .equals(m.header(MTConnectConstants.REASON))).isEmpty());
        for (int i = 0; i < 30; i++) {
            added.add(agent.set(items[i % items.length], Integer.toString(100 + i)));
        }
        agent.resume();

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample)
                .stream().map(ReceivedMessages.Received::sequence).toList()).containsExactlyElementsOf(added));
    }

    @Test
    void aStreamTheAgentClosesResumesFromTheCursorWithoutReadingCurrent() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        long first = agent.set("x_load", "1");
        awaitAgent().untilAsserted(() -> assertThat(received.of("x_load")).hasSize(2));
        agent.closeStreams();
        long second = agent.set("x_load", "2");

        awaitAgent().untilAsserted(() -> assertThat(received.of("x_load")).hasSize(3));
        assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(ReceivedMessages.Received::sequence).containsExactly(first, second);
        assertThat(agent.requests()).filteredOn(request -> request.contains("/current")).hasSize(1);
        assertThat(agent.requests()).filteredOn(request -> request.contains("/sample")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(received.matching(m -> m.bad() && m.sequence() == null)).isEmpty();
    }

    @Test
    void aStreamTheAgentEndsAtOnceIsRequestedAgainOnlyAfterTheReconnectDelay() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, "Trial", "");
        agent.endStreamsAtOnce();
        agent.closeStreams();
        int before = samples(agent);

        // without the delay the consumer would send hundreds of requests in this time
        await().during(Duration.ofMillis(5 * RECONNECT_DELAY)).atMost(Duration.ofSeconds(5))
                .until(() -> samples(agent) - before <= 7);
        assertThat(samples(agent) - before).isGreaterThanOrEqualTo(3);
        agent.clearAnswers();
        long next = agent.set("x_load", "1");
        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(ReceivedMessages.Received::sequence).containsExactly(next));
        // a clean end is not an outage
        assertThat(received.matching(m -> m.bad() && m.sequence() == null)).isEmpty();
    }

    private static int samples(StubAgent agent) {
        return (int) agent.requests().stream().filter(request -> request.contains("/sample")).count();
    }

    @Test
    void withoutADeviceEveryDeviceOfTheAgentIsStreamed() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        ReceivedMessages received = streaming(agent, null, "");
        agent.set("x_load", "5");
        agent.set("agent_avail", "AVAILABLE");

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample)).hasSize(2));
        assertThat(received.all()).extracting(m -> m.text(MTConnectConstants.DEVICE_UUID)).contains("trial-001",
                "31e6d9c6-9275-5559-b9e5-d02ba9db3555");
        assertThat(agent.requests()).allSatisfy(request -> assertThat(request).matches("/(probe|current|sample).*"));
    }

    @Test
    void thePathFilterReachesTheAgentUnchanged() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        String path = "//Axes//DataItem[@type='LOAD']";
        consume("mtconnect:" + agent.url() + "?device=Trial&" + FAST + "&path=RAW(" + path + ")");

        awaitAgent().untilAsserted(() -> assertThat(agent.requests()).anyMatch(request -> request.contains("/sample")));
        assertThat(agent.requests()).filteredOn(request -> !request.endsWith("/probe"))
                .allSatisfy(request -> assertThat(queryParameter(request, "path")).isEqualTo(path));
    }

    @Test
    void theCountIsLoweredToTheAgentBufferSize() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.bufferSize(64);
        consume("mtconnect:" + agent.url() + "?device=Trial&" + FAST);

        awaitAgent().untilAsserted(() -> assertThat(agent.requests()).anyMatch(request -> request.contains("/sample")));
        assertThat(agent.requests()).filteredOn(request -> request.contains("/sample"))
                .allSatisfy(request -> assertThat(queryParameter(request, "count")).isEqualTo("64"));
    }

    @Test
    void aCountBelowTheBufferSizeIsSentAsConfigured() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        consume("mtconnect:" + agent.url() + "?device=Trial&count=10&" + FAST);

        awaitAgent().untilAsserted(() -> assertThat(agent.requests()).anyMatch(request -> request.contains("/sample")));
        String sample = agent.requests().stream().filter(request -> request.contains("/sample")).findFirst()
                .orElseThrow();
        assertThat(queryParameter(sample, "count")).isEqualTo("10");
        assertThat(queryParameter(sample, "interval")).isEqualTo("50");
        assertThat(queryParameter(sample, "heartbeat")).isEqualTo("250");
    }

    private static String queryParameter(String request, String name) {
        String query = URI.create(request).getRawQuery();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
