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
package org.apache.camel.component.typesafeai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeSafeAiQuestionsResourceTest extends TypeSafeAiTestSupport {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsQuestionsForEachEndpointAndPreservesBody() {
        respond = request -> result(request.getJsonObject("questions").containsKey("bug_severity")
                ? Map.of("is_urgent", Map.of("type", "noul", "noul", 0.7),
                        "department", choice("technical", Map.of("billing", 0.1, "technical", 0.8,
                                "sales", 0.05, "feedback", 0.05)),
                        "frustration", score(1.0, 3), "bug_severity", score(2.0, 4),
                        "refund_requested", Map.of("type", "noul", "noul", 0.1))
                : Map.of("is_urgent", Map.of("type", "noul", "noul", 0.9),
                        "department", choice("technical", Map.of("billing", 0.1, "technical", 0.8, "sales", 0.1)),
                        "frustration", score(1.0, 3)));

        Exchange quickstart = template.request(
                "typesafe-ai:quickstart?questionsResource=classpath:typesafe-ai/quickstart-questions.json"
                                               + "&resultProperty=evaluation",
                exchange -> exchange.getMessage().setBody("Help! My payouts have been failing for 3 days."));
        assertThat(quickstart.getException()).isNull();
        assertThat(quickstart.getMessage().getBody()).isEqualTo("Help! My payouts have been failing for 3 days.");
        assertThat(quickstart.getProperty("evaluation", JsonObject.class).getJsonObject("answers")).hasSize(3);

        Exchange triage = template.request(
                "typesafe-ai:triage?questionsResource=classpath:typesafe-ai/triage-questions.json"
                                           + "&resultProperty=evaluation",
                exchange -> exchange.getMessage().setBody("My Stripe account has failed for three days."));
        assertThat(triage.getException()).isNull();
        assertThat(triage.getProperty("evaluation", JsonObject.class).getJsonObject("answers")).hasSize(5);
        assertThat(requests).hasSize(2);
        assertThat(requests.stream().map(request -> request.getJsonObject("questions").size())).containsExactly(3, 5);
        assertThat(requests.stream().map(request -> request.get("state")))
                .containsExactly("Help! My payouts have been failing for 3 days.",
                        "My Stripe account has failed for three days.");
    }

    @Test
    void rejectsMissingOrInvalidResourceAtStartup() {
        assertThatThrownBy(() -> context.getEndpoint(
                "typesafe-ai:missing?questionsResource=classpath:typesafe-ai/missing.json").start())
                .hasMessageContaining("typesafe-ai/missing.json");
        assertThatThrownBy(() -> context.getEndpoint(
                "typesafe-ai:invalid?questionsResource=classpath:typesafe-ai/invalid-questions.json").start())
                .hasMessageContaining("Invalid questionsResource").hasMessageContaining("Choice requires 1 to 255 options");
        assertThat(requests).isEmpty();
    }

    @Test
    void rejectsInlineQuestionsTogetherWithResource() {
        assertThatThrownBy(() -> context.getEndpoint("typesafe-ai:both?questions=%7B%7D"
                                                     + "&questionsResource=classpath:typesafe-ai/quickstart-questions.json")
                .start())
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void rejectsResourceLargerThanFourMegabytes() throws Exception {
        Path resource = temporaryDirectory.resolve("oversized-questions.json");
        byte[] bytes = new byte[4 * 1024 * 1024 + 1];
        Files.write(resource, bytes);

        assertThatThrownBy(() -> context.getEndpoint(
                "typesafe-ai:oversized?questionsResource=" + resource.toUri()).start())
                .hasMessageContaining("questionsResource exceeds 4 MB limit");
    }

    @Test
    void fileResourceIsLoadedOnceAtEndpointStartup() throws Exception {
        Path resource = temporaryDirectory.resolve("questions.json");
        Files.writeString(resource, "{\"refund\":{\"type\":\"noul\",\"instructions\":\"Refund requested?\"}}");
        respond = request -> result(Map.of("refund", Map.of("type", "noul", "noul", 0.9)));

        String uri = "typesafe-ai:file-questions?questionsResource=" + resource.toUri()
                     + "&resultProperty=evaluation";
        Exchange first = template.request(uri, exchange -> exchange.getMessage().setBody("Please refund me"));
        assertThat(first.getException()).isNull();
        Files.delete(resource);
        Exchange second = template.request(uri, exchange -> exchange.getMessage().setBody("Another refund"));
        assertThat(second.getException()).isNull();
        assertThat(requests).hasSize(2);
        assertThat(requests.stream().map(request -> request.get("state")))
                .containsExactly("Please refund me", "Another refund");
    }

    private static Map<String, Object> choice(String selected, Map<String, Double> probabilities) {
        return Map.of("type", "choice", "choice", selected, "confidence", 0.7,
                "probabilities", probabilities);
    }

    private static Map<String, Object> score(double value, int levels) {
        Map<String, Object> probabilities = new LinkedHashMap<>();
        Map<String, Object> legend = new LinkedHashMap<>();
        for (int i = 0; i < levels; i++) {
            probabilities.put(Integer.toString(i), i == (int) value ? 1.0 : 0.0);
            legend.put(Integer.toString(i), "Level " + i);
        }
        return Map.of("type", "score", "score", value, "confidence", 0.7,
                "probabilities", probabilities, "legend", legend);
    }
}
