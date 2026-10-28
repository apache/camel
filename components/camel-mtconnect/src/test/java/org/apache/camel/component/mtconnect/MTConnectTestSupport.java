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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Routes from {@code mtconnect:} endpoints to a collector, against stub agents that live as long as one test. */
abstract class MTConnectTestSupport extends CamelTestSupport {

    /** Short protocol times so that outages and recoveries happen within a test. */
    static final String FAST = "interval=50&heartbeat=250&readTimeout=500&reconnectDelay=200";
    static final long HEARTBEAT = 250;
    static final long CONNECT_TIMEOUT = 500;
    static final long RECONNECT_DELAY = 200;

    private final List<StubAgent> agents = new ArrayList<>();

    StubAgent agent(StubDialect dialect) throws IOException {
        StubAgent agent = StubAgent.start(dialect);
        agents.add(agent);
        return agent;
    }

    StubAgent httpsAgent(StubDialect dialect) throws IOException {
        StubAgent agent = StubAgent.startHttps(dialect);
        agents.add(agent);
        return agent;
    }

    /** Starts a route from the endpoint URI that collects every message. */
    ReceivedMessages consume(String uri) throws Exception {
        ReceivedMessages received = new ReceivedMessages();
        consume(uri, received);
        return received;
    }

    /**
     * Starts a route from the agent and device with the fast protocol times and waits for the current values of every
     * data item of the device, so that observations added afterwards arrive in the sample stream.
     */
    ReceivedMessages streaming(StubAgent agent, String device, String options) throws Exception {
        String query = (device == null ? "" : "device=" + device + "&") + FAST + (options.isEmpty() ? "" : "&" + options);
        ReceivedMessages received = consume("mtconnect:" + agent.url() + "?" + query);
        long items = agent.model().items().stream().filter(item -> StubDeviceModel.matches(item, device)).count();
        await().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::current)).hasSize((int) items));
        return received;
    }

    void consume(String uri, ReceivedMessages received) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).process(received);
            }
        });
    }

    @AfterEach
    void stopRoutesThenAgents() throws Exception {
        context.stop();
        agents.forEach(StubAgent::close);
    }
}
