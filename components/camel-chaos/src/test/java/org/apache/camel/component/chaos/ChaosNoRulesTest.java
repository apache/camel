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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With chaos switched on and no rule, the routes behave as without chaos, and the exchanges create no events for it.
 */
class ChaosNoRulesTest {

    private static List<String> run(boolean chaos, boolean withRule) throws Exception {
        CamelContext context = new DefaultCamelContext();
        try {
            if (chaos) {
                context.getPropertiesComponent().addInitialProperty("camel.chaos.enabled", "true");
            }
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    onException(IllegalArgumentException.class).handled(true).setBody(constant("handled"));
                    from("direct:start").routeId("start")
                            .choice()
                                .when(body().isEqualTo("bad")).throwException(new IllegalArgumentException("bad"))
                                .otherwise().to("direct:upper").to("mock:end")
                            .end();
                    from("direct:upper").routeId("upper").transform(body().regexReplaceAll("a", "A"));
                }
            });
            context.start();
            List<String> answer = new ArrayList<>();
            answer.add("applicable=" + context.getCamelContextExtension().isEventNotificationApplicable());
            if (withRule) {
                ChaosManager.get(context).addRule("endpoint=nothing:* error");
                answer.add("applicable=" + context.getCamelContextExtension().isEventNotificationApplicable());
            }
            ProducerTemplate template = context.createProducerTemplate();
            for (String body : List.of("banana", "bad", "apple")) {
                Exchange out = template.send("direct:start", e -> e.getMessage().setBody(body));
                answer.add(out.getMessage().getBody(String.class) + "/" + out.getException());
            }
            return answer;
        } finally {
            context.stop();
        }
    }

    @Test
    void sameResultsWithAndWithoutChaos() throws Exception {
        List<String> without = run(false, false);
        List<String> with = run(true, false);
        assertThat(with).isEqualTo(without);
        assertThat(with).containsExactly("applicable=false", "bAnAnA/null", "handled/null", "Apple/null");
    }

    @Test
    void aRuleThatMatchesNothingChangesNothing() throws Exception {
        List<String> with = run(true, true);
        assertThat(with).containsExactly("applicable=false", "applicable=false", "bAnAnA/null", "handled/null",
                "Apple/null");
    }
}
