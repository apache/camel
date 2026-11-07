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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26727: what to try, the rules suggested per route from what the routes do.
 */
class ChaosAdvisorTest extends ChaosTestSupport {

    @BindToRegistry("http")
    private final TestHttpComponent http = new TestHttpComponent();

    @BindToRegistry("testpoll")
    private final TestPollComponent poll = new TestPollComponent();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders")
                        .to("http://stock/check?throwExceptionOnFailure=true")
                        .to("seda:shipping");
                from("seda:shipping?pollTimeout=10").routeId("shipping")
                        .errorHandler(deadLetterChannel("seda:dlq"))
                        .to("mock:ship");
                from("seda:dlq?pollTimeout=10").routeId("dlq").to("mock:dead");
                from("direct:pay").routeId("pay")
                        .circuitBreaker()
                        .to("http://pay/charge")
                        .onFallback().setBody(constant("later"))
                        .end();
                from("testpoll:inbox?delay=100000&initialDelay=100000").routeId("inbox").to("mock:inbox");
            }
        };
    }

    private static Map<String, String> rules(List<ChaosAdvisor.Suggestion> suggestions) {
        Map<String, String> answer = new LinkedHashMap<>();
        suggestions.forEach(s -> answer.put(s.route() + " " + s.recipe() + " " + s.kind().key, s.rule()));
        return answer;
    }

    @Test
    void whatToTryPerRouteFromWhatTheRoutesDo() {
        List<ChaosAdvisor.Suggestion> suggestions = ChaosAdvisor.suggest(context);
        Map<String, String> rules = rules(suggestions);

        assertThat(rules)
                .containsEntry("orders http-error errors", "endpoint=http://stock/check error status=503 rate=0.25 until=10m")
                .containsEntry("orders timeout slow",
                        "endpoint=http://stock/check error after=5s exception=java.net.SocketTimeoutException rate=0.1 until=10m")
                // a hand-off over seda: the next route fails, and nothing may be lost between the two
                .containsEntry("orders route-error lost",
                        "route=shipping error rate=0.2 until=2m expect=orders->shipping:lost<=0 within=3m")
                .containsEntry("shipping dlq-unreachable dead letter channel", "endpoint=seda:dlq error at=dlq")
                // a circuit breaker: the service is down for a while
                .containsEntry("pay outage errors", "endpoint=http://pay/charge error rate=burst:10s/1m until=10m")
                .containsEntry("inbox poll-error consumer", "endpoint=testpoll://inbox error at=poll rate=0.5 until=10m")
                .containsEntry("inbox stall lifecycle", "route=inbox stall duration=20s");
        // no HTTP error without a breaker for the call inside the breaker, and the query is left out
        assertThat(rules).doesNotContainKey("pay http-error errors");
        assertThat(rules.values()).noneMatch(r -> r.contains("throwExceptionOnFailure"));
        // an internal input (direct) is not a consumer to fail
        assertThat(rules).doesNotContainKey("orders route-error consumer");

        // the most worth trying first
        assertThat(suggestions.get(0).recipe()).isEqualTo("http-error");
        assertThat(suggestions.get(0).why()).contains("without a circuit breaker");
    }

    @Test
    void everySuggestedRuleIsValid() {
        for (ChaosAdvisor.Suggestion s : ChaosAdvisor.suggest(context)) {
            String id = chaos().addRule(s.rule());
            chaos().removeRule(id);
        }
    }

    @Test
    void theManagerGivesThemAsJson() {
        JsonArray list = (JsonArray) chaos().suggest().get("suggestions");
        JsonObject first = (JsonObject) list.get(0);
        assertThat(first.getString("route")).isEqualTo("orders");
        assertThat(first.getString("kind")).isEqualTo("errors");
        assertThat(first.getString("recipe")).isEqualTo("http-error");
        assertThat(first.getString("title")).isEqualTo("Returns an error (HTTP status)");
        assertThat(first.getString("rule")).startsWith("endpoint=http://stock/check error status=503");
        assertThat(first.getString("why")).isNotBlank();
    }
}
