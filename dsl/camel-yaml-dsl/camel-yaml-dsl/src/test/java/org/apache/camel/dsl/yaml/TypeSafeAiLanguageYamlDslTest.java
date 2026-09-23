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
package org.apache.camel.dsl.yaml;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.language.typesafeai.TypeSafeAiLanguage;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeAiLanguageYamlDslTest {
    @TempDir
    Path directory;

    @Test
    void filterAndChoiceUseGenericLanguageAndPropertiesWithoutBeans() throws Exception {
        var requests = new ConcurrentLinkedQueue<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", http -> {
            String body = new String(http.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(body);
            double probability = body.contains("SELECTED REFUND") ? 0.8 : 0.1;
            byte[] response = ("{\"model\":\"jev-1.13.0\",\"usage\":{},\"answers\":{\"predicate\":{"
                               + "\"type\":\"noul\",\"noul\":" + probability + "}}}")
                    .getBytes(StandardCharsets.UTF_8);
            http.sendResponseHeaders(200, response.length);
            http.getResponseBody().write(response);
            http.close();
        });
        server.start();
        Main main = new Main();
        try {
            Path routes = directory.resolve("typesafe-ai.yaml");
            Files.writeString(routes, """
                    - from:
                        uri: direct:filter
                        steps:
                          - filter:
                              expression:
                                language:
                                  language: typesafe-ai
                                  expression: "Does this message request a refund?"
                              steps:
                                - setHeader:
                                    name: handled
                                    constant: refund
                    - from:
                        uri: direct:choice
                        steps:
                          - choice:
                              when:
                                - expression:
                                    language:
                                      language: typesafe-ai
                                      expression: "Does this message request a refund?"
                                  steps:
                                    - setHeader:
                                        name: handled
                                        constant: refund
                              otherwise:
                                steps:
                                  - setHeader:
                                      name: handled
                                      constant: general
                    """);
            main.addProperty("camel.component.typesafe-ai.api-key", "test-key");
            main.addProperty("camel.component.typesafe-ai.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
            main.addProperty("camel.language.typesafe-ai.endpoint", "typesafe-ai:refund");
            main.addProperty("camel.language.typesafe-ai.threshold", "0.8");
            main.addProperty("camel.language.typesafe-ai.state", "${header.selected}");
            main.configure().withRoutesIncludePattern("file:" + routes);
            main.start();
            assertThat(main.getCamelContext().getRegistry().findByType(Predicate.class)).isEmpty();
            try (var producer = main.getCamelContext().createProducerTemplate()) {
                for (String route : new String[] { "filter", "choice" }) {
                    for (boolean matches : new boolean[] { true, false }) {
                        Exchange result = producer.request("direct:" + route, e -> {
                            e.getMessage().setBody("PRIVATE BODY");
                            e.getMessage().setHeader("selected", matches ? "SELECTED REFUND" : "hello");
                        });
                        assertThat(result.getException()).isNull();
                        assertThat(result.getMessage().getBody()).isEqualTo("PRIVATE BODY");
                        assertThat(result.getProperty(TypeSafeAiLanguage.RESULT)).isNotNull();
                        assertThat(result.getMessage().getHeader("handled"))
                                .isEqualTo(matches ? "refund" : (route.equals("choice") ? "general" : null));
                    }
                }
            }
            assertThat(requests).hasSize(4).allSatisfy(request -> assertThat(request)
                    .contains("Does this message request a refund?").doesNotContain("PRIVATE BODY"));
        } finally {
            main.stop();
            server.stop(0);
        }
    }
}
