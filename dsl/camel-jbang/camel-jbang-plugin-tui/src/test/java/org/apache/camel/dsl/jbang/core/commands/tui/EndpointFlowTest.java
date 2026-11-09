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
package org.apache.camel.dsl.jbang.core.commands.tui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message-size example: three timers each feed a seda queue another route of the integration consumes. 25 messages
 * come in, by the timers, and none goes out; the seda queues are hand-offs between its routes.
 */
class EndpointFlowTest {

    @Test
    void aHandOffBetweenItsRoutesIsNoInOrOut() {
        IntegrationInfo info = new IntegrationInfo();
        add(info, "small-producer", "in", "timer://small?period=2000", 12);
        add(info, "small-producer", "out", "seda://small", 12);
        add(info, "process-small", "in", "seda://small", 12);
        add(info, "large-producer", "in", "timer://large?period=5000", 5);
        add(info, "large-producer", "out", "seda://large", 5);
        add(info, "process-large", "in", "seda://large", 5);
        // a system it sends to
        add(info, "process-large", "out", "kafka:archive?brokers=localhost:9092", 5);

        assertThat(EndpointFlow.handOffs(info)).containsExactlyInAnyOrder("seda:small", "seda:large");
        assertThat(EndpointFlow.total(info, "in", ep -> true)).isEqualTo(17);
        assertThat(EndpointFlow.total(info, "out", ep -> true)).isEqualTo(5);
    }

    private static void add(IntegrationInfo info, String routeId, String direction, String uri, long hits) {
        EndpointInfo ep = new EndpointInfo();
        ep.routeId = routeId;
        ep.direction = direction;
        ep.uri = uri;
        ep.hits = hits;
        info.endpoints.add(ep);
    }
}
