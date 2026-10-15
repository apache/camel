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

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticEvaluations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeAiSemanticApiPathTest extends TypeSafeAiTestSupport {
    private final ConcurrentLinkedQueue<String> paths = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void capturePaths() {
        server.removeContext("/v1/systemone");
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().toString());
            handle(exchange);
        });
    }

    @Test
    void componentPropertiesConfigureSemanticAdapter() throws Exception {
        Main main = new Main();
        main.addProperty("camel.component.typesafe-ai.api-key", "test-key");
        main.addProperty("camel.component.typesafe-ai.base-url",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/gateway/");
        main.addProperty("camel.component.typesafe-ai.api-path", "/v1/decision");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                var expert = new TypeSafeAiSemanticAdapter();
                expert.setCamelContext(getContext());
                getContext().getRegistry().bind("decisions", expert);
                SemanticEvaluations.get(getContext()).replace("test",
                        Map.of("refund",
                                new SemanticEvaluation(
                                        "boolean", "decisions", null,
                                        Map.of("instructions", "Refund requested?", "threshold", 0.5,
                                                "uncertainty", 0.0, "uncertaintyPolicy", "fail"))));
                from("direct:request").setBody().language("semantic", "ref:refund");
            }
        });
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", 0.9)));
        try {
            main.start();
            try (var producer = main.getCamelContext().createProducerTemplate()) {
                Object response = producer.requestBody("direct:request", "Refund");
                assertThat(response).isEqualTo(true);
                assertThat(paths).containsExactly("/gateway/v1/decision");
                assertThat(requests).hasSize(1);
                assertThat(requests.peek().get("state")).isEqualTo("Refund");
                assertThat(authorization).containsExactly("Bearer test-key");
            }
        } finally {
            main.stop();
        }
    }

}
