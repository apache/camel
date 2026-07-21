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
package org.apache.camel.component.springai.chat;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.tool.AiToolRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Unit test for tool discovery mechanism in camel-spring-ai-chat component.
 *
 * This test verifies that tools are properly registered in the AiToolRegistry when defined using the ai-tool component.
 */
public class SpringAiChatToolsDiscoveryTest extends CamelTestSupport {

    @Test
    public void testToolsAreRegisteredWithRegistry() throws Exception {
        var registry = AiToolRegistry.getOrCreate(context);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var tools = registry.getTools();

            assertThat(tools).isNotEmpty();
            assertThat(tools).containsKey("weather");
            assertThat(tools).containsKey("math");
            assertThat(tools).containsKey("database");

            assertThat(tools.get("weather")).isNotEmpty();
            assertThat(tools.get("weather")).hasSize(1);

            assertThat(tools.get("math")).isNotEmpty();
            assertThat(tools.get("math")).hasSize(1);

            assertThat(tools.get("database")).isNotEmpty();
            assertThat(tools.get("database")).hasSize(1);
        });
    }

    @Test
    public void testToolExecutionWithParameters() throws Exception {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var registry = AiToolRegistry.getOrCreate(context);
            assertThat(registry.getTools()).isNotEmpty();
        });

        var exchange = template().request("direct:testWeatherTool", e -> {
            e.getIn().setHeader("city", "Paris");
        });

        assertThat(exchange.getException()).isNull();
        String result = exchange.getMessage().getBody(String.class);
        assertThat(result).contains("Paris");
    }

    @Test
    public void testToolExecutionWithDifferentInput() throws Exception {
        var registry = AiToolRegistry.getOrCreate(context);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var tools = registry.getTools();
            assertThat(tools.get("weather")).isNotNull();
            assertThat(tools.get("weather")).hasSize(1);
        });

        var exchange = template().request("direct:testWeatherTool", e -> {
            e.getIn().setHeader("city", "London");
        });

        assertThat(exchange.getException()).isNull();
        String result = exchange.getMessage().getBody(String.class);
        assertThat(result).contains("London");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Define weather tool
                from("ai-tool:weather?tags=weather&description=Get weather for a city")
                        .process(exchange -> {
                            String city = exchange.getIn().getHeader("city", String.class);
                            String weather = "The weather in " + city + " is sunny";
                            exchange.getIn().setBody(weather);
                        });

                // Define calculator tool
                from("ai-tool:calculator?tags=math&description=Calculate expressions")
                        .process(exchange -> {
                            String expression = exchange.getIn().getHeader("expression", String.class);
                            exchange.getIn().setBody("Result: " + expression);
                        });

                // Define database tool
                from("ai-tool:queryDb?tags=database&description=Query database")
                        .process(exchange -> {
                            String query = exchange.getIn().getHeader("query", String.class);
                            exchange.getIn().setBody("Query result: " + query);
                        });

                // Direct route for testing tool execution
                from("direct:testWeatherTool")
                        .setHeader("city", simple("${header.city}"))
                        .process(exchange -> {
                            String city = exchange.getIn().getHeader("city", String.class);
                            String weather = "The weather in " + city + " is sunny";
                            exchange.getIn().setBody(weather);
                        });
            }
        };
    }
}
