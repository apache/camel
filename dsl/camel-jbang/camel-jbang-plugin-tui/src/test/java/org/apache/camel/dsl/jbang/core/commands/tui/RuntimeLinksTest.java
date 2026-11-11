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

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.ai.About;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * connect-service/jms-request-reply: order-intake puts each order on a queue, reserve-order asks stock-service over JMS
 * request-reply, publishes the reserved order on a topic for shipping and invoicing, and its dead letter channel moves
 * the order it cannot reserve to a dead letter queue. The sources name the links by their scheme only: every queue was
 * an In and an Out of the integration.
 */
class RuntimeLinksTest {

    private static About.Model sources() {
        List<About.In> in = List.of(
                new About.In("file", "file:orders", "order-intake"),
                new About.In("jms", "jms:queue:orders", "reserve-order"),
                new About.In("jms", "jms:queue:stock", "stock-service"),
                new About.In("jms", "jms:queue:orders.dead", "dead-orders"),
                new About.In("jms", "jms:topic:orders.reserved", "shipping"),
                new About.In("jms", "jms:topic:orders.reserved", "invoicing"));
        List<About.RouteLine> routes = List.of(
                new About.RouteLine("order-intake", 0, null, "file", 4),
                new About.RouteLine("reserve-order", 0, null, "jms", 6),
                new About.RouteLine("stock-service", 0, null, "jms", 5),
                new About.RouteLine("dead-orders", 0, null, "jms", 1),
                new About.RouteLine("shipping", 0, null, "jms", 1),
                new About.RouteLine("invoicing", 0, null, "jms", 1));
        List<About.Out> out = List.of(
                new About.Out("messaging", "jms:queue:orders", "out", "order-intake"),
                new About.Out("messaging", "jms:queue:stock", "in/out", "reserve-order"),
                new About.Out("messaging", "jms:topic:orders.reserved", "out", "reserve-order"),
                new About.Out("messaging", "jms:queue:orders.dead", "out", "reserve-order"));
        return new About.Model("Each order asks a stock service", "from the comment", in, routes, out);
    }

    private static JsonObject topology() {
        JsonArray edges = new JsonArray();
        edges.add(edge("order-intake", "reserve-order", "jms:queue:orders", "call", false));
        edges.add(edge("reserve-order", "stock-service", "jms:queue:stock", "call", true));
        edges.add(edge("reserve-order", "shipping", "jms:topic:orders.reserved", "call", false));
        edges.add(edge("reserve-order", "invoicing", "jms:topic:orders.reserved", "call", false));
        edges.add(edge("reserve-order", "dead-orders", "jms:queue:orders.dead", "error", false));
        JsonObject topology = new JsonObject();
        topology.put("edges", edges);
        return topology;
    }

    private static JsonObject edge(String from, String to, String endpoint, String kind, boolean reply) {
        JsonObject e = new JsonObject();
        e.put("fromRouteId", from);
        e.put("toRouteId", to);
        e.put("endpoint", endpoint);
        e.put("kind", kind);
        if (reply) {
            e.put("reply", true);
        }
        return e;
    }

    @Test
    void theRoutesAreUnderTheRouteThatReachesThem() {
        About.Model m = RuntimeLinks.of(topology()).apply(sources(), new IntegrationInfo());

        assertThat(m.routes()).extracting(r -> "  ".repeat(r.depth()) + (r.how() != null ? r.how() + " " : "") + r.routeId())
                .containsExactly(
                        "order-intake",
                        "  async reserve-order",
                        "    call stock-service",
                        "    async shipping",
                        "    async invoicing",
                        "    error dead-orders");
    }

    @Test
    void inAndOutAreWhatCrossesTheIntegration() {
        About.Model m = RuntimeLinks.of(topology()).apply(sources(), new IntegrationInfo());

        assertThat(m.in()).extracting(About.In::label).containsExactly("file:orders");
        assertThat(m.out()).isEmpty();
    }

    @Test
    void anOutTheRuntimeSawOnAQueueOfTheIntegrationIsNoSystemOutside() {
        RuntimeLinks links = RuntimeLinks.of(topology());

        // as the runtime names it, with // after the scheme
        assertThat(links.consumedHere(new About.Out("messaging", "jms://queue:orders", "out", "order-intake"))).isTrue();
        assertThat(links.consumedHere(new About.Out("messaging", "jms://queue:billing", "out", "order-intake"))).isFalse();
    }

    @Test
    void aQueueOthersSendToTooStaysAnInWithWhatTheySent() {
        // 3 orders from order-intake, and 5 more another application put on the queue
        IntegrationInfo info = new IntegrationInfo();
        info.endpoints.add(endpoint("order-intake", "out", "jms://queue:orders", 3));
        info.endpoints.add(endpoint("reserve-order", "in", "jms://queue:orders", 8));

        About.Model m = RuntimeLinks.of(topology()).apply(sources(), info);

        assertThat(m.in()).extracting(About.In::label).containsExactly("file:orders", "jms:queue:orders  +5 from outside");
    }

    @Test
    void withoutATopologyTheSourcesStay() {
        About.Model m = sources();

        assertThat(RuntimeLinks.of(null).apply(m, new IntegrationInfo())).isSameAs(m);
        assertThat(RuntimeLinks.of(topology()).howReached()).containsEntry("stock-service", "call")
                .containsEntry("shipping", "async").containsEntry("dead-orders", "error")
                .doesNotContainKey("order-intake");
    }

    private static EndpointInfo endpoint(String route, String direction, String uri, long hits) {
        EndpointInfo ep = new EndpointInfo();
        ep.routeId = route;
        ep.direction = direction;
        ep.uri = uri;
        ep.hits = hits;
        return ep;
    }
}
