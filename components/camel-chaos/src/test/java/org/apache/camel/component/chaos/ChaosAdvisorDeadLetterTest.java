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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A dead letter channel that the routes share (the error handler of the route builder) is one thing to try: it is
 * suggested once, not for each route, and never for the route that consumes it; a rule of its kind in any of its routes
 * tries it (CAMEL-26732).
 */
class ChaosAdvisorDeadLetterTest extends ChaosTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("seda:dlq?size=100"));

                from("direct:orders").routeId("orders").to("mock:stock");
                from("direct:billing").routeId("billing").throwException(new IllegalStateException("no money"));
                from("direct:audit").routeId("audit").to("mock:audit");
                from("seda:dlq?pollTimeout=10").routeId("dlq").to("mock:dead");
            }
        };
    }

    private List<ChaosAdvisor.Suggestion> deadLetter() {
        return ChaosAdvisor.suggest(context).stream().filter(s -> s.kind() == ChaosAdvisor.Kind.DEAD_LETTER).toList();
    }

    @Test
    void theSharedDeadLetterChannelIsSuggestedOnce() {
        List<ChaosAdvisor.Suggestion> dead = deadLetter();
        assertThat(dead).hasSize(1);
        ChaosAdvisor.Suggestion s = dead.get(0);
        assertThat(s.route()).isEqualTo("orders");
        // the route that consumes the dead letter channel is not one of its routes
        assertThat(s.routes()).containsExactly("orders", "billing", "audit");
        assertThat(s.rule()).isEqualTo("endpoint=seda:dlq error at=dlq");
        assertThat(s.why()).startsWith("routes orders, billing, audit send what fails to the dead letter channel seda:dlq");
    }

    @Test
    void aRuleOfItsKindInAnyOfItsRoutesTriesIt() {
        chaos().addRule("endpoint=seda:dlq error at=dlq until=10m");
        // billing fails, and the send to the dead letter channel fails too
        template.send("direct:billing", e -> e.getMessage().setBody("invoice"));

        JsonObject suggestion = null;
        for (Object o : (JsonArray) chaos().suggest().get("suggestions")) {
            JsonObject jo = (JsonObject) o;
            if ("dead letter channel".equals(jo.getString("kind"))) {
                suggestion = jo;
            }
        }
        assertThat(suggestion).isNotNull();
        assertThat(suggestion.getString("route")).isEqualTo("orders");
        assertThat((JsonArray) suggestion.get("routes")).containsExactly("orders", "billing", "audit");
        assertThat(suggestion.getBooleanOrDefault("tried", false)).isTrue();

        // what was tried: in billing, and orders has nothing of that kind left to try
        for (Object o : (JsonArray) chaos().report().toJson().get("tried")) {
            JsonObject route = (JsonObject) o;
            if ("orders".equals(route.getString("route"))) {
                assertThat((JsonArray) route.get("notTried")).doesNotContain("dead letter channel");
            }
        }
    }
}
