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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.component.jev.JevPredicate;
import org.apache.camel.main.Main;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JevPropertiesYamlDslTest {
    @ParameterizedTest
    @ValueSource(strings = { "jev-properties.yaml", "jev-native-properties.yaml" })
    void usesConfiguredJevPredicateWithoutRegisteringABean(String resource) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            calls.incrementAndGet();
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = """
                    {"model":"jev-1.13.0","answers":{"refund":{"type":"noul","noul":0.9}},
                    "usage":{"input_tokens":10,"output_tokens":2}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Main main = new Main();
        main.addProperty("camel.component.jev.api-key", "test-key");
        main.addProperty("camel.component.jev.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        main.addProperty("camel.component.jev.model", "jev-1.13.0");
        main.addProperty("camel.component.jev.request-timeout", "2000");
        main.addProperty("camel.component.jev.questions", """
                {"refund":{"type":"noul","instructions":"Refund requested?"}}
                """);
        main.addProperty("camel.component.jev.state", "${header.selected}");
        main.addProperty("camel.component.jev.threshold", "0.8");
        main.configure().withRoutesIncludePattern("classpath:" + resource);
        try {
            main.start();
            try (var producer = main.getCamelContext().createProducerTemplate()) {
                Exchange result = producer.request("direct:yaml", e -> {
                    e.getMessage().setBody("PRIVATE BODY");
                    e.getMessage().setHeader("selected", "Refund the payment");
                });
                assertThat(result.getException()).isNull();
                assertThat(result.getMessage().getHeader("branch")).isEqualTo("refund");
                assertThat(result.getMessage().getBody()).isEqualTo("PRIVATE BODY");
                assertThat(result.getProperty(JevPredicate.RESULT, JsonObject.class).path("answers.refund.noul")).isNotNull();
                assertThat(calls).hasValue(1);
                assertThat(request.get()).contains("Refund the payment").doesNotContain("PRIVATE BODY");
                assertThat(authorization).hasValue("Bearer test-key");
                assertThat(main.getCamelContext().getRegistry().findByType(JevPredicate.class)).isEmpty();
            }
        } finally {
            main.stop();
            server.stop(0);
        }
    }
}
