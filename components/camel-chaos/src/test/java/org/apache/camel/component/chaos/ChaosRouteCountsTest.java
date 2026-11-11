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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The counts per route in the report: each exchange counts once in each route it goes through, also with seda,
 * multicast, recipient list, enrich, wire tap and direct sub-routes; handled means the error handler handled a failure
 * and the exchange completed.
 */
class ChaosRouteCountsTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:toSeda").routeId("toSeda").to("seda:queue");
                from("seda:queue").routeId("queue").to("mock:queue");
                from("direct:multicast").routeId("multicast").multicast().to("direct:sub", "direct:sub").end()
                        .to("mock:multicast");
                from("direct:recipientList").routeId("recipientList").recipientList(constant("direct:sub,mock:rl"));
                from("direct:enrich").routeId("enrich").enrich("direct:sub").to("mock:enrich");
                from("direct:wireTap").routeId("wireTap").wireTap("direct:tap").to("mock:wireTap");
                from("direct:tap").routeId("tap").to("mock:tap");
                from("direct:sub").routeId("sub").setHeader("sub", constant(true)).id("subStep");
                from("direct:loop").routeId("loop").loop(3).to("direct:sub").end();
                // the calling route redelivers the send to flaky, which has no error handler and fails twice
                from("direct:retry").routeId("retry")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .to("direct:flaky");
                from("direct:flaky").routeId("flaky").errorHandler(noErrorHandler())
                        .process(e -> {
                            int attempt = e.getProperty("attempt", 0, Integer.class) + 1;
                            e.setProperty("attempt", attempt);
                            if (attempt < 3) {
                                throw new IllegalStateException("attempt " + attempt);
                            }
                        });
                from("direct:notHandled").routeId("notHandled")
                        .onException(IllegalStateException.class).log("seen, not handled").end()
                        .throwException(new IllegalStateException("not handled"));
                from("direct:rollbackOnly").routeId("rollbackOnly").markRollbackOnly();
                from("direct:dead").routeId("dead")
                        .errorHandler(deadLetterChannel("mock:dead"))
                        .throwException(new IllegalStateException("to the dead letter channel"));
            }
        };
    }

    @BeforeEach
    void countWithARule() {
        // the routes are counted while there are rules
        chaos().addRule("endpoint=nothing:* error");
    }

    private long count(String route, String count) {
        return routeCount(route, count);
    }

    @Test
    void anExchangeThatGoesThroughARouteInALoopCountsOnce() {
        String id = chaos().addRule("endpoint=nothing2:* error expect=sub:2 within=1m");

        template.sendBody("direct:loop", "a");

        // three passes through sub, one exchange
        assertThat(count("sub", "completed")).isEqualTo(1);
        assertThat(count("loop", "completed")).isEqualTo(1);
        JsonObject expectation = (JsonObject) ruleReport(id).get("expectation");
        assertThat(expectation.getLong("completed")).isEqualTo(1);
        assertThat(expectation.getString("outcome")).isEqualTo("pending");

        template.sendBody("direct:loop", "b");
        assertThat(count("sub", "completed")).isEqualTo(2);
        assertThat(((JsonObject) ruleReport(id).get("expectation")).getString("outcome")).isEqualTo("met");
    }

    @Test
    void aRouteCalledAgainByARedeliveryCountsTheLastPass() {
        template.sendBody("direct:retry", "x");

        // two failed passes and a completed one: one completed exchange of flaky
        assertThat(count("flaky", "completed")).isEqualTo(1);
        assertThat(count("flaky", "failed")).isZero();
        assertThat(count("retry", "completed")).isEqualTo(1);
        assertThat(count("retry", "redeliveries")).isEqualTo(2);
    }

    @Test
    void theEntryFaultsOfARouteAreAskedOncePerExchange() {
        String id = chaos().addRule("route=sub corrupt mode=headers until=10");

        // a header for the corrupt mode to change
        template.sendBodyAndHeader("direct:loop", "a", "country", "DK");

        assertThat(ruleReport(id).getLong("matches")).isEqualTo(1);
        assertThat(fired(id, "entry")).isEqualTo(1);
    }

    @Test
    void aCopyOfAnExchangeCountsOnItsOwn() throws Exception {
        // the original goes through sub, and so do the two copies of the multicast
        template.sendBody("direct:sub", "a");
        template.sendBody("direct:multicast", "b");

        assertThat(count("sub", "completed")).isEqualTo(3);
    }

    @Test
    void anExchangeMarkedRollbackOnlyFailsAndDoesNotMeetAnExpectation() {
        String id = chaos().addRule("endpoint=nothing2:* error expect=rollbackOnly:1 within=1m");

        template.send("direct:rollbackOnly", e -> e.getMessage().setBody("x"));

        assertThat(count("rollbackOnly", "failed")).isEqualTo(1);
        assertThat(count("rollbackOnly", "completed")).isZero();
        assertThat(((JsonObject) ruleReport(id).get("expectation")).getLong("completed")).isZero();
    }

    @Test
    void sedaRoutesAreCounted() throws Exception {
        chaos().addRule("endpoint=nothing2:* error expect=queue:3 within=1m");
        getMockEndpoint("mock:queue").expectedMessageCount(3);

        template.sendBody("direct:toSeda", "a");
        template.sendBody("direct:toSeda", "b");
        template.sendBody("seda:queue", "c");

        MockEndpoint.assertIsSatisfied(context);
        await().atMost(10, TimeUnit.SECONDS).until(() -> count("queue", "completed") == 3);
        assertThat(count("toSeda", "completed")).isEqualTo(2);
        // the expectation is decided before the route count moves
        JsonObject expectation = (JsonObject) ruleReport("r2").get("expectation");
        assertThat(expectation.getString("outcome")).isEqualTo("met");
    }

    @Test
    void multicastRecipientListAndEnrichAreCountedOnceAndTheirSubRoutesToo() throws Exception {
        template.sendBody("direct:multicast", "a");
        template.sendBody("direct:recipientList", "b");
        template.sendBody("direct:enrich", "c");

        assertThat(count("multicast", "completed")).isEqualTo(1);
        assertThat(count("recipientList", "completed")).isEqualTo(1);
        assertThat(count("enrich", "completed")).isEqualTo(1);
        // two copies of the multicast, one of the recipient list, one of enrich
        assertThat(count("sub", "completed")).isEqualTo(4);
    }

    @Test
    void wireTapCountsTheCopyInTheTappedRouteOnly() throws Exception {
        getMockEndpoint("mock:tap").expectedMessageCount(3);
        for (int i = 0; i < 3; i++) {
            template.sendBody("direct:wireTap", "x");
        }
        MockEndpoint.assertIsSatisfied(context);
        await().atMost(10, TimeUnit.SECONDS).until(() -> count("tap", "completed") == 3);
        assertThat(count("wireTap", "completed")).isEqualTo(3);
    }

    @Test
    void anOnExceptionThatDoesNotHandleIsNotHandled() {
        template.send("direct:notHandled", e -> e.getMessage().setBody("x"));

        assertThat(count("notHandled", "failed")).isEqualTo(1);
        assertThat(count("notHandled", "handled")).isZero();
        assertThat(count("notHandled", "completed")).isZero();
    }

    @Test
    void theDeadLetterChannelHandlesAndTheExchangeCompletes() {
        template.send("direct:dead", e -> e.getMessage().setBody("x"));

        assertThat(count("dead", "completed")).isEqualTo(1);
        assertThat(count("dead", "handled")).isEqualTo(1);
        assertThat(count("dead", "failed")).isZero();
    }

    @Test
    void aSubRouteFailureFailsBothRoutes() {
        chaos().addRule("step=subStep error");
        template.send("direct:enrich", e -> e.getMessage().setBody("x"));

        assertThat(count("sub", "failed")).isEqualTo(1);
        assertThat(count("enrich", "failed")).isEqualTo(1);
    }
}
