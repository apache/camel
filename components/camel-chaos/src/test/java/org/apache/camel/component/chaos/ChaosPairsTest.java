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

import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What to try offers two faults at once (CAMEL-26772): an outage while the dead letter channel fails, and a slow
 * service that also fails, after the single faults.
 */
class ChaosPairsTest extends ChaosTestSupport {

    @BindToRegistry("http")
    private final TestHttpComponent http = new TestHttpComponent();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .errorHandler(deadLetterChannel("seda:parked").maximumRedeliveries(2))
                        .to("http://payment/charge")
                        .to("mock:shipped");
                from("seda:parked?pollTimeout=10").routeId("parked").to("mock:parked");
                // a route that calls nothing remote has no pairs
                from("direct:audit").routeId("audit").to("mock:audit");
            }
        };
    }

    private ChaosAdvisor.Suggestion pair(String title) {
        return ChaosAdvisor.suggest(context).stream().filter(s -> title.equals(s.title())).findFirst().orElseThrow();
    }

    @Test
    void anOutageWhileTheDeadLetterChannelFails() {
        ChaosAdvisor.Suggestion s = pair("An outage while the dead letter channel fails");
        assertThat(s.route()).isEqualTo("orders");
        assertThat(s.kind()).isEqualTo(ChaosAdvisor.Kind.COMBINED);
        // what should hold is on the first rule
        assertThat(s.rules()).containsExactly(
                "endpoint=http://payment/charge error status=503 until=1m expect=orders:failed<=0",
                "endpoint=seda:parked error at=dlq until=1m");
        assertThat(s.why()).contains("dead letter channel seda:parked", "are messages lost?");
    }

    @Test
    void aSlowServiceThatAlsoFails() {
        ChaosAdvisor.Suggestion s = pair("A slow service that also fails");
        assertThat(s.rules()).containsExactly(
                "endpoint=http://payment/charge delay duration=1s..3s rate=0.5 until=2m expect=orders:failed<=0",
                "endpoint=http://payment/charge error status=503 rate=0.2 until=2m");
        assertThat(s.why()).contains("expect=orders:maxTime<=30s");
    }

    @Test
    void afterTheSingleFaults() {
        List<ChaosAdvisor.Suggestion> suggestions = ChaosAdvisor.suggest(context);
        int firstPair = suggestions.indexOf(pair("An outage while the dead letter channel fails"));
        // the HTTP error and the dead letter channel on their own come first
        assertThat(suggestions.subList(0, firstPair)).extracting(ChaosAdvisor.Suggestion::recipe)
                .contains("http-error", "dlq-unreachable");
        assertThat(suggestions).noneMatch(s -> "audit".equals(s.route()));
    }

    @Test
    void theJsonHasAllTheRulesAndNoTriedForAPair() {
        // the rules of the pair fired on their own, as their own kinds
        chaos().addRule("endpoint=http://payment/charge error status=503 until=1");
        template.sendBody("direct:orders", "order");

        JsonArray list = (JsonArray) chaos().suggest().get("suggestions");
        JsonObject pair = list.stream().map(JsonObject.class::cast)
                .filter(s -> "An outage while the dead letter channel fails".equals(s.getString("title")))
                .findFirst().orElseThrow();
        assertThat(pair.getString("kind")).isEqualTo("combined");
        assertThat(pair.getString("rule"))
                .isEqualTo("endpoint=http://payment/charge error status=503 until=1m expect=orders:failed<=0");
        List<Object> rules = pair.getCollection("rules");
        assertThat(rules).containsExactly(
                "endpoint=http://payment/charge error status=503 until=1m expect=orders:failed<=0",
                "endpoint=seda:parked error at=dlq until=1m");
        assertThat(pair.containsKey("tried")).isFalse();
        // a single suggestion has no rules list
        JsonObject single = list.stream().map(JsonObject.class::cast)
                .filter(s -> "http-error".equals(s.getString("recipe"))).findFirst().orElseThrow();
        assertThat(single.containsKey("rules")).isFalse();
        assertThat(single.getBoolean("tried")).isTrue();

        // and combined is never "not tried yet": its rules are tried as their own kinds
        assertThat(chaos().report().toText()).doesNotContain("combined");
    }
}
