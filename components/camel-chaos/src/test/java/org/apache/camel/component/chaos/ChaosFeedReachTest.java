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
package org.apache.camel.component.chaos;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-26803: an experiment whose feed does not reach the routes of its expectations is refused before it begins, as
 * it would say nothing (in a run of chaos-explore the AI fed direct:parked for hypotheses about refunds).
 */
class ChaosFeedReachTest extends ChaosTestSupport {

    private static final int PORT = AvailablePortFinder.getNextAvailable();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // orders come by themselves, are charged over HTTP at a route of the same integration, and shipped
                from("timer:orders?delay=-1&repeatCount=1").routeId("orders")
                        .to("netty-http:http://localhost:" + PORT + "/payment")
                        .to("seda:shipping");
                from("netty-http:http://0.0.0.0:" + PORT + "/payment").routeId("payment").setBody(constant("paid"));
                from("seda:shipping").routeId("shipping").to("mock:shipped");
                // refunds come from outside, and a parked order goes nowhere else
                from("direct:refunds").routeId("refunds").to("direct:payback");
                from("direct:payback").routeId("payback").to("mock:paidBack");
                from("direct:parked").routeId("parked").to("mock:parked");
                // a send known only when it sends
                from("direct:router").routeId("router").toD("direct:${header.next}");
            }
        };
    }

    private Map<String, Object> begin(String rules, String feedRoute) {
        return chaos().beginExperiment("a hypothesis", rules, feedRoute);
    }

    @Test
    void aFeedThatMissesTheRoutesOfTheExpectationsIsRefused() {
        chaos().startExploration("10m", null);

        assertThatThrownBy(() -> begin("endpoint=mock:paidBack error expect=refunds:failed<=0 within=1m", "parked"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The feed goes to route parked, but not to route refunds of the expectations, so the"
                            + " experiment would say nothing: feed refunds instead (its id as the feed)");
        // what it reaches is named
        assertThatThrownBy(() -> begin("endpoint=mock:parked error expect=parked:failed<=0 within=1m", "refunds"))
                .hasMessageStartingWith("The feed goes to route refunds, which sends to payback, but not to route parked");
        // nothing was added
        assertThat(chaos().hasRule("r1")).isFalse();
    }

    @Test
    void aFeedThatReachesARouteOfTheExpectationsBegins() {
        chaos().startExploration("10m", null);

        // a direct call
        assertThat(begin("endpoint=mock:paidBack error expect=payback:failed<=0 within=1m", "refunds"))
                .containsEntry("verdict", "PENDING");
        chaos().endExperiment(null);
        // the routes that the timer feeds get messages whatever the feed: over HTTP and over seda
        assertThat(begin("route=shipping error expect=payment:failed<=0 within=1m", "parked"))
                .containsEntry("verdict", "PENDING");
        chaos().endExperiment(null);
        assertThat(begin("route=shipping error expect=shipping:failed<=0 within=1m", "refunds"))
                .containsEntry("verdict", "PENDING");
        chaos().endExperiment(null);
        // a send known only when it sends: where it goes is not known, so it is not refused
        assertThat(begin("endpoint=mock:parked error expect=parked:failed<=0 within=1m", "router"))
                .containsEntry("verdict", "PENDING");
        chaos().endExperiment(null);
        // no feed
        assertThat(begin("endpoint=mock:parked error expect=parked:failed<=0 within=1m", null))
                .containsEntry("verdict", "PENDING");
    }
}
