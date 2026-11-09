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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stub.StubComponent;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What to try says how to feed each route with messages (CAMEL-26771): the route feeds itself, an HTTP request, a file,
 * or camel cmd send to the route, which goes to the real system unless its input is a stub.
 */
class ChaosFeedTest extends ChaosTestSupport {

    private final TestPollComponent poll = new TestPollComponent();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().setName("shop");
        context.addComponent("testpoll", poll);
        StubComponent stub = new StubComponent();
        stub.setShadow(true);
        // remotemq is remote by its JSON (in the test resources), served by a stub as camel run --stub=remote does
        context.addComponent("remotemq", stub);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:tick?period=100000").routeId("ticker").to("mock:tick");
                from("direct:orders").routeId("orders").to("mock:orders");
                from("file:target/chaos-inbox?initialDelay=100000").routeId("inbox").to("mock:inbox");
                from("remotemq:refunds?breakOnFirstError=true").routeId("refunds").to("mock:refunds");
                from("testpoll:payments?delay=100000&initialDelay=100000").routeId("payments").to("mock:payments");
            }
        };
    }

    @Test
    void howToFeedEachRoute() {
        assertThat(ChaosAdvisor.feed(context.getRoute("ticker")))
                .isEqualTo(new ChaosAdvisor.Feed("self", "the route feeds itself (timer)", false));
        assertThat(ChaosAdvisor.feed(context.getRoute("orders")))
                .isEqualTo(new ChaosAdvisor.Feed(
                        "send", "camel cmd send shop --endpoint=orders --body=<message> --repeat=20 --interval=200ms",
                        false));
        assertThat(ChaosAdvisor.feed(context.getRoute("inbox")))
                .isEqualTo(new ChaosAdvisor.Feed("file", "write a file into target/chaos-inbox", false));
        // a stub of a broker is fed with camel cmd send, and the messages stay in the stub
        assertThat(ChaosAdvisor.feed(context.getRoute("refunds")))
                .isEqualTo(new ChaosAdvisor.Feed(
                        "send", "camel cmd send shop --endpoint=refunds --body=<message> --repeat=20 --interval=200ms",
                        false));
        // a remote input that is not a stub: the messages go to the real system
        assertThat(ChaosAdvisor.feed(context.getRoute("payments")).real()).isTrue();
    }

    @Test
    void theSuggestionsHaveTheFeedOfTheirRoute() {
        JsonArray list = (JsonArray) chaos().suggest().get("suggestions");
        JsonObject refunds = list.stream().map(JsonObject.class::cast)
                .filter(s -> "refunds".equals(s.getString("route"))).findFirst().orElseThrow();
        JsonObject feed = refunds.getMap("feed");
        assertThat(feed.getString("kind")).isEqualTo("send");
        assertThat(feed.getString("hint")).contains("--endpoint=refunds");
        assertThat(feed.containsKey("real")).isFalse();
        JsonObject payments = list.stream().map(JsonObject.class::cast)
                .filter(s -> "payments".equals(s.getString("route"))).findFirst().orElseThrow();
        assertThat(payments.getMap("feed").get("real")).isEqualTo(true);
    }

    @Test
    void anHttpInputIsFedWithItsMethodAndPath() {
        assertThat(ChaosAdvisor.httpMethod("platform-http:///orders?httpMethodRestrict=GET,POST")).isEqualTo("GET");
        assertThat(ChaosAdvisor.httpMethod("platform-http:///orders")).isEqualTo("POST");
        assertThat(ChaosAdvisor.httpMethod("rest:put:/orders/{id}")).isEqualTo("PUT");
        assertThat(ChaosAdvisor.httpPath("platform-http:///orders?httpMethodRestrict=POST")).isEqualTo("/orders");
        assertThat(ChaosAdvisor.httpPath("platform-http:orders")).isEqualTo("/orders");
        assertThat(ChaosAdvisor.httpPath("rest:put:/orders/{id}")).isEqualTo("/orders/{id}");
    }
}
