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
package org.apache.camel.component.jev;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JevPropertiesTest extends JevTestSupport {
    private Main configuredMain() {
        Main main = new Main();
        main.addProperty("camel.component.jev.api-key", "test-key");
        main.addProperty("camel.component.jev.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        main.addProperty("camel.component.jev.model", "jev-1.13.0");
        main.addProperty("camel.component.jev.request-timeout", "2000");
        main.addProperty("camel.component.jev.questions",
                Jsoner.serialize(Map.of("refund", noulQuestion("Refund requested?"))));
        main.addProperty("camel.component.jev.state", "${header.selected}");
        main.addProperty("camel.component.jev.result-property", "evaluation");
        main.addProperty("camel.component.jev.threshold", "0.8");
        main.addProperty("camel.component.jev.uncertainty", "0.05");
        main.addProperty("camel.component.jev.uncertainty-policy", "NonMatch");
        respond = request -> result(Map.of("refund", Map.of("type", "noul",
                "noul", "refund".equals(request.get("state")) ? 0.9 : 0.1)));
        return main;
    }

    @Test
    void configuresProducerAndInlinePredicatesEntirelyThroughProperties() throws Exception {
        Main main = configuredMain();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());
                from("direct:produce").to("jev:configured");
                from("direct:choose").choice().when().language("jev", "jev:configured")
                        .setHeader("branch", constant("refund")).otherwise().setHeader("branch", constant("other"));
                from("direct:filter").filter().language("jev", "jev:configured").setHeader("admitted", constant(true));
                from("direct:validate").validate().language("jev", "jev:configured");
            }
        });
        try {
            main.start();
            var producer = main.getCamelContext().createProducerTemplate();
            Exchange evaluated = producer.request("direct:produce", e -> {
                e.getMessage().setBody("PRIVATE BODY");
                e.getMessage().setHeader("selected", "refund");
            });
            assertThat(evaluated.getException()).isNull();
            assertThat(evaluated.getMessage().getBody()).isEqualTo("PRIVATE BODY");
            assertThat(evaluated.getProperty("evaluation", JsonObject.class).path("answers.refund.noul")).isNotNull();
            for (String route : new String[] { "direct:choose", "direct:filter", "direct:validate" }) {
                Exchange accepted = producer.request(route, e -> {
                    e.getMessage().setBody("PRIVATE BODY");
                    e.getMessage().setHeader("selected", "refund");
                });
                assertThat(accepted.getException()).isNull();
                assertThat(accepted.getMessage().getBody()).isEqualTo("PRIVATE BODY");
                assertThat(accepted.getProperty(JevPredicate.RESULT)).isInstanceOf(JsonObject.class);
            }
            Exchange rejected = producer.request("direct:choose", e -> e.getMessage().setHeader("selected", "hello"));
            assertThat(rejected.getMessage().getHeader("branch")).isEqualTo("other");
            assertThat(requests).hasSize(5).allSatisfy(request -> assertThat(request.toJson()).doesNotContain("PRIVATE BODY"));
            assertThat(main.getCamelContext().getRegistry().findByType(JevPredicate.class)).isEmpty();
            producer.stop();
        } finally {
            main.stop();
        }
    }

    @Test
    void languageRequiresExplicitThresholdAndOneNoulQuestion() {
        JevComponent component = context.getComponent("jev", JevComponent.class);
        component.getConfiguration().setQuestions(Jsoner.serialize(Map.of("refund", noulQuestion("Refund?"))));
        assertThatThrownBy(() -> context.resolveLanguage("jev").createPredicate("jev:no-threshold"))
                .hasRootCauseMessage("An explicit threshold is required for the Jev language");
        component.getConfiguration().setThreshold(0.8);
        component.getConfiguration().setQuestions(Jsoner.serialize(mixedQuestions()));
        assertThatThrownBy(() -> context.resolveLanguage("jev").createPredicate("jev:mixed"))
                .hasRootCauseMessage("The Jev language requires exactly one configured Noul question");
    }
}
