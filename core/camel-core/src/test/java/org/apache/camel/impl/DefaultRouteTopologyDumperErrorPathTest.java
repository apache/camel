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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RouteTopologyDumper;
import org.apache.camel.spi.RouteTopologyDumper.TopologyEdge;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25429: a send from an onException clause or a dead letter channel is an error path, not a call, and only from a
 * route whose error handler acts: a route with no error handler leaves the failure to its caller.
 */
class DefaultRouteTopologyDumperErrorPathTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("direct:parked").maximumRedeliveries(2));

                onException(IllegalStateException.class).handled(true).to("direct:parked");

                from("direct:checkout").routeId("checkout")
                        .to("direct:charge");

                from("direct:charge").routeId("payment-provider").errorHandler(noErrorHandler())
                        .throwException(new IllegalStateException("card declined"));

                from("direct:parked").routeId("parked")
                        .to("mock:parked");
            }
        };
    }

    @Test
    void errorPathsAreNotCalls() {
        List<TopologyEdge> edges = new DefaultRouteTopologyDumper().dumpTopology(context).edges();

        assertThat(edges).filteredOn(e -> !e.isErrorPath())
                .extracting(e -> e.fromRouteId() + "->" + e.toRouteId())
                .containsExactly("checkout->payment-provider");
        // the error paths of checkout: its onException and its dead letter channel
        assertThat(edges).filteredOn(e -> "checkout".equals(e.fromRouteId()) && e.isErrorPath())
                .extracting(TopologyEdge::kind)
                .containsExactlyInAnyOrder(RouteTopologyDumper.EDGE_ON_EXCEPTION, RouteTopologyDumper.EDGE_DEAD_LETTER);
        // payment-provider has no error handler: its failure goes back to checkout
        assertThat(edges).noneMatch(e -> "payment-provider".equals(e.fromRouteId()) && "parked".equals(e.toRouteId()));
        // parked is handled by the same error handler: an error path to itself, never a call
        assertThat(edges).filteredOn(e -> "parked".equals(e.fromRouteId()) && "parked".equals(e.toRouteId()))
                .allMatch(TopologyEdge::isErrorPath);
    }

    @Test
    void anEdgeWithoutAKindIsACall() {
        TopologyEdge edge = new TopologyEdge("a", "b", "direct:b", "internal");

        assertThat(edge.kind()).isEqualTo(RouteTopologyDumper.EDGE_CALL);
        assertThat(edge.isErrorPath()).isFalse();
    }
}
