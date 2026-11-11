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
package org.apache.camel.impl;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RouteTopologyDumper;
import org.apache.camel.spi.RouteTopologyDumper.TopologyEdge;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26823: an edge says whether the route waits for the answer of the route it sends to, as the send processor
 * decides the exchange pattern: the exchangePattern of the endpoint uri, else the pattern of the send, else the pattern
 * of the message the route consumed; a direct route always runs before the sender goes on. The shape of
 * connect-service/jms-request-reply, with seda in the place of the JMS broker.
 */
class DefaultRouteTopologyDumperReplyTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:orders?repeatCount=1").routeId("order-intake")
                        // a hand-off: the order goes on a queue
                        .to("seda:orders");

                from("seda:orders").routeId("reserve-order")
                        .errorHandler(deadLetterChannel("seda:orders-dead"))
                        // request-reply: the endpoint asks for the answer
                        .to("seda:stock?exchangePattern=InOut")
                        // the send asks for it
                        .to(ExchangePattern.InOut, "seda:pricing")
                        // a call
                        .to("direct:audit")
                        // a copy, never an answer
                        .wireTap("seda:metrics")
                        // a hand-off to the subscribers
                        .to("seda:reserved");

                // the request of a client: what it sends on is InOut too
                from("seda:quote?exchangePattern=InOut").routeId("quote")
                        .to("seda:pricing");

                from("seda:stock").routeId("stock-service").log("stock");
                from("seda:pricing").routeId("pricing").log("pricing");
                from("direct:audit").routeId("audit").log("audit");
                from("seda:metrics").routeId("metrics").log("metrics");
                from("seda:reserved").routeId("shipping").log("shipping");
                from("seda:orders-dead").routeId("dead-orders").log("dead");
            }
        };
    }

    @Test
    void anEdgeSaysWhetherTheRouteWaitsForTheAnswer() {
        List<TopologyEdge> edges = new DefaultRouteTopologyDumper().dumpTopology(context).edges();

        assertThat(edges).extracting(e -> e.fromRouteId() + "->" + e.toRouteId() + (e.reply() ? " waits" : ""))
                .contains(
                        "order-intake->reserve-order",
                        "reserve-order->stock-service waits",
                        "reserve-order->pricing waits",
                        "reserve-order->audit waits",
                        "reserve-order->metrics",
                        "reserve-order->shipping",
                        "quote->pricing waits");
        // the dead letter channel: the message ends there
        assertThat(edges).filteredOn(e -> "dead-orders".equals(e.toRouteId()))
                .isNotEmpty().allMatch(e -> e.isErrorPath() && !e.reply());
    }

    @Test
    void anEdgeOfBeforeWaitsForNoAnswer() {
        assertThat(new TopologyEdge("a", "b", "seda:b", "internal").reply()).isFalse();
        assertThat(new TopologyEdge("a", "b", "seda:b", "internal", RouteTopologyDumper.EDGE_CALL, null, null).reply())
                .isFalse();
    }
}
