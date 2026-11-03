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

import java.util.List;

import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** gzip on the answers that are not streams: asked for, decoded when the agent compresses, absent on streams. */
class MTConnectCompressionTest extends MTConnectTestSupport {

    @Test
    void compressedProbeAndCurrentAnswersAreDecodedAndTheStreamIsNotCompressed() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.compress();

        ReceivedMessages received = streaming(agent, "Trial", "");
        long next = agent.set("x_load", "1");

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(ReceivedMessages.Received::sequence).containsExactly(next));
        assertThat(agent.compressedRequests()).containsExactly("/Trial/probe", "/Trial/current");
        assertThat(agent.requests()).anyMatch(request -> request.startsWith("/Trial/sample"));
    }

    @Test
    void compressedProducerAnswersAreDecoded() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.compress();
        String uri = "mtconnect:" + agent.url() + "?device=Trial&operation=";

        Exchange probe = template.send(uri + "probe", exchange -> {
        });
        Exchange current = template.send(uri + "current", exchange -> {
        });
        Exchange sample = template.send(uri + "sample&count=3", exchange -> {
        });
        Exchange assets = template.send(uri + "assets", exchange -> {
        });

        assertThat(probe.getMessage().getBody(String.class)).startsWith("<?xml").contains("<MTConnectDevices");
        assertThat(current.getMessage().getBody(List.class)).hasSize(11);
        assertThat(sample.getMessage().getBody(List.class)).hasSize(3);
        assertThat(assets.getMessage().getBody(String.class)).contains("<CuttingTool ");
        assertThat(agent.compressedRequests()).hasSize(agent.requests().size());
    }

    @Test
    void uncompressedAnswersToARequestForGzipStillWork() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);

        Exchange current = template.send("mtconnect:" + agent.url() + "?device=Trial&operation=current", exchange -> {
        });

        assertThat(current.getException()).isNull();
        assertThat(current.getMessage().getBody(List.class)).hasSize(11);
        assertThat(agent.compressedRequests()).contains("/Trial/current");
    }

    @Test
    void pollingSampleAnswersAreCompressed() throws Exception {
        StubAgent agent = agent(StubDialect.V2_5);
        agent.compress();
        ReceivedMessages received = consume("mtconnect:" + agent.url() + "?device=Trial&streaming=false&delay=50");
        awaitAgent().untilAsserted(() -> assertThat(received.size()).isEqualTo(11));
        long next = agent.set("x_load", "2");

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(m -> m.headers().get(MTConnectConstants.SEQUENCE)).containsExactly(next));
        assertThat(agent.compressedRequests()).anyMatch(request -> request.startsWith("/Trial/sample?from="));
    }
}
