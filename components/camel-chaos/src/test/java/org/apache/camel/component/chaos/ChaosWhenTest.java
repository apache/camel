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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code when=}: a rule fires only for the messages that meet a condition in the simple language, checked before the
 * rate, so the rate and the until count count only those messages.
 */
class ChaosWhenTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .setHeader("seen", constant("yes")).id("mark")
                        .to("mock:stock").id("callStock");
                from("direct:reply").routeId("reply")
                        .to("mock:service").id("callService")
                        .to("mock:after").id("after");
            }
        };
    }

    private Exchange order(String country) {
        return template.send("direct:orders", e -> {
            e.getMessage().setHeader("country", country);
            e.getMessage().setBody("order");
        });
    }

    @Test
    void onlyTheMessagesThatMeetTheCondition() throws Exception {
        String id = chaos().addRule("endpoint=mock:stock error when=\"${header.country} == 'ES'\"");
        getMockEndpoint("mock:stock").expectedHeaderValuesReceivedInAnyOrder("country", "DK", "SE");

        assertThat(order("DK").getException()).isNull();
        assertThat(order("ES").getException()).isInstanceOf(ChaosException.class);
        assertThat(order("SE").getException()).isNull();

        MockEndpoint.assertIsSatisfied(context);
        // the messages that do not meet the condition are not matches
        assertThat(ruleReport(id).getLong("matches")).isEqualTo(1);
        assertThat(fired(id, "send")).isEqualTo(1);
        assertThat(ruleReport(id).getString("description"))
                .isEqualTo("fail every send to mock:stock, when ${header.country} == 'ES'");
    }

    @Test
    void doubleQuotesInsideEaseTheQuotingOnTheCommandLine() {
        // camel cmd chaos-add --rule='endpoint=mock:stock error when="${header.country} == \"ES\""'
        String id = chaos().addRule("endpoint=mock:stock error when=\"${header.country} == \\\"ES\\\"\"");

        assertThat(order("DK").getException()).isNull();
        assertThat(order("ES").getException()).isInstanceOf(ChaosException.class);

        assertThat(fired(id, "send")).isEqualTo(1);
        // a text needs quotes in the simple language, which says so
        assertThatThrownBy(() -> chaos().addRule("endpoint=mock:stock error when=\"${header.country} == ES\""))
                .hasMessageContaining("write it as a quoted literal 'x'");
    }

    @Test
    void theRateCountsOnlyTheMessagesThatMeetIt() {
        String id = chaos().addRule("endpoint=mock:stock error rate=every:2 when=\"${header.country} == 'ES'\"");

        order("DK");
        order("DK");
        assertThat(order("ES").getException()).isNull();
        order("DK");
        assertThat(order("ES").getException()).isInstanceOf(ChaosException.class);

        assertThat(ruleReport(id).getLong("matches")).isEqualTo(2);
    }

    @Test
    void untilCountsOnlyTheMessagesThatMeetIt() {
        String id = chaos().addRule("endpoint=mock:stock error until=1 when=\"${header.country} == 'ES'\"");

        order("DK");
        assertThat(order("ES").getException()).isInstanceOf(ChaosException.class);
        assertThat(order("ES").getException()).isNull();

        assertThat(ruleReport(id).getString("state")).isEqualTo("ended");
    }

    @Test
    void atTheEntryOfARouteAndAtAStep() {
        String entry = chaos().addRule("route=orders error when=\"${header.country} == 'ES'\"");
        String step = chaos().addRule("step=mark delay duration=1ms when=\"${header.country} == 'DK'\"");

        assertThat(order("ES").getException()).isInstanceOf(ChaosException.class);
        assertThat(order("DK").getException()).isNull();
        assertThat(order("SE").getException()).isNull();

        assertThat(fired(entry, "entry")).isEqualTo(1);
        assertThat(fired(step, "step")).isEqualTo(1);
    }

    @Test
    void aConditionOnAStreamBodyLeavesTheBodyToTheRoute() throws Exception {
        String id = chaos().addRule("endpoint=mock:stock error when=\"${body} contains 'boom'\"");
        getMockEndpoint("mock:stock").expectedBodiesReceived("all is well");

        Exchange boom = template.send("direct:orders",
                e -> e.getMessage().setBody(new ByteArrayInputStream("boom".getBytes(StandardCharsets.UTF_8))));
        template.send("direct:orders",
                e -> e.getMessage().setBody(new ByteArrayInputStream("all is well".getBytes(StandardCharsets.UTF_8))));

        assertThat(boom.getException()).isInstanceOf(ChaosException.class);
        // the stream cache was reset after the check, so the endpoint reads the whole body
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
    }

    @Test
    void aConditionOnAStreamReplyCachesItFirst() throws Exception {
        getMockEndpoint("mock:service").whenAnyExchangeReceived(e -> e.getMessage().setBody(
                new ByteArrayInputStream(e.getMessage().getBody(String.class).getBytes(StandardCharsets.UTF_8))));
        String id = chaos().addRule("endpoint=mock:service error at=reply when=\"${body} contains 'bad'\"");
        getMockEndpoint("mock:after").expectedBodiesReceived("good reply");

        assertThat(template.request("direct:reply", e -> e.getMessage().setBody("bad reply")).getException())
                .isInstanceOf(ChaosException.class);
        assertThat(template.request("direct:reply", e -> e.getMessage().setBody("good reply")).getException()).isNull();

        // the reply stream was cached for the check, and the next step reads it whole
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "reply")).isEqualTo(1);
    }

    @Test
    void aConditionThatFailsIsNotMetAndCountsAsSkipped() {
        String id = chaos().addRule("endpoint=mock:stock error when=\"${header.country.substring(10)} == 'x'\"");

        assertThat(order("ES").getException()).isNull();

        assertThat(ruleReport(id).getLong("matches")).isZero();
        assertThat(ruleReport(id).getLong("skipped")).isEqualTo(1);
        JsonObject reasons = (JsonObject) ruleReport(id).get("skippedReasons");
        assertThat(reasons.getLong(ChaosRule.CONDITION_FAILED)).isEqualTo(1);
    }

    @Test
    void aConditionThatIsNotSimpleIsRefused() {
        assertThatThrownBy(() -> chaos().addRule("endpoint=mock:stock error when=\"${header.country == 'ES'\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid chaos rule [endpoint=mock:stock error when=\"${header.country == 'ES'\"]:"
                                        + " when=${header.country == 'ES' is not a valid simple predicate: ");
        assertThat((JsonArray) chaos().report().toJson().get("rules")).isEmpty();
    }
}
