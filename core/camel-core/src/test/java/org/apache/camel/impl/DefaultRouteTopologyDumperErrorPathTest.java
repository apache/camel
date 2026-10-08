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
 * CAMEL-25429: a send from an onException clause or the error handler is an error path, not a call, and only from a
 * route whose error handler acts: a route with no error handler leaves the failure to its caller. An error path says
 * what happens to the failure.
 */
class DefaultRouteTopologyDumperErrorPathTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("direct:parked").maximumRedeliveries(2));

                onException(IllegalStateException.class).handled(true).to("direct:parked");
                onException(IllegalArgumentException.class).continued(true).to("direct:audit");
                onException(UnsupportedOperationException.class).to("direct:alert");
                onException(ArithmeticException.class).handled(header("handle").isEqualTo("yes")).to("direct:review");

                from("direct:checkout").routeId("checkout")
                        .to("direct:charge");

                from("direct:charge").routeId("payment-provider").errorHandler(noErrorHandler())
                        .throwException(new IllegalStateException("card declined"));

                from("direct:parked").routeId("parked").to("mock:parked");
                from("direct:audit").routeId("audit").to("mock:audit");
                from("direct:alert").routeId("alert").to("mock:alert");
                from("direct:review").routeId("review").to("mock:review");
            }
        };
    }

    @Test
    void errorPathsAreNotCalls() {
        List<TopologyEdge> edges = edges();

        assertThat(edges).filteredOn(e -> !e.isErrorPath())
                .extracting(e -> e.fromRouteId() + "->" + e.toRouteId())
                .containsExactly("checkout->payment-provider");
        // payment-provider has no error handler: its failure goes back to checkout
        assertThat(edges).noneMatch(e -> "payment-provider".equals(e.fromRouteId()) && e.isErrorPath());
        // parked is handled by the same error handler: an error path to itself, never a call
        assertThat(edges).filteredOn(e -> "parked".equals(e.fromRouteId()) && "parked".equals(e.toRouteId()))
                .isNotEmpty().allMatch(TopologyEdge::isErrorPath);
    }

    @Test
    void anErrorPathSaysWhatHappensToTheFailure() {
        List<TopologyEdge> checkout = edges().stream()
                .filter(e -> "checkout".equals(e.fromRouteId()) && e.isErrorPath()).toList();

        assertThat(checkout).extracting(e -> e.toRouteId() + " " + e.via() + " " + e.handling())
                .containsExactlyInAnyOrder(
                        // the dead letter channel and an onException with handled(true): the message ends there
                        "parked errorHandler handled",
                        "parked onException handled",
                        "audit onException continued",
                        // a side trip: the failure goes on to the caller
                        "alert onException notHandled",
                        // a predicate decides at runtime
                        "review onException null");
    }

    @Test
    void anEdgeWithoutAKindIsACall() {
        TopologyEdge edge = new TopologyEdge("a", "b", "direct:b", "internal");

        assertThat(edge.kind()).isEqualTo(RouteTopologyDumper.EDGE_CALL);
        assertThat(edge.via()).isNull();
        assertThat(edge.handling()).isNull();
        assertThat(edge.isErrorPath()).isFalse();
    }

    private List<TopologyEdge> edges() {
        return new DefaultRouteTopologyDumper().dumpTopology(context).edges();
    }
}
