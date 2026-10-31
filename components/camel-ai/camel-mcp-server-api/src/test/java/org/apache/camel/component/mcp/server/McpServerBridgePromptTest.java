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
package org.apache.camel.component.mcp.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerBridgePromptTest extends CamelTestSupport {

    private RecordingMcpServerEngine engine;
    private final CountDownLatch slowRouteInterrupted = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        engine = new RecordingMcpServerEngine();
        answer.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("prompt-tests,other-tests");
        configuration.setPromptTimeout(1000);
        answer.addService(new McpServerBridge(configuration));
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-prompt:greeting?tags=prompt-tests&description=Greeting"
                     + "&argument.name.required=true")
                        .routeId("greeting-route")
                        .setBody(simple("Hello ${header.name}"));

                from("ai-prompt:multi_turn?tags=prompt-tests")
                        .setBody(constant(List.of(
                                "Plain user message",
                                Map.of("role", "assistant", "content", "Assistant reply"))));

                from("ai-prompt:shared?tags=prompt-tests")
                        .routeId("shared-first-route")
                        .setBody(constant("First version"));

                from("ai-prompt:shared?tags=other-tests")
                        .routeId("shared-second-route")
                        .setBody(constant("Second version"));

                from("ai-prompt:untagged")
                        .setBody(constant("Untagged prompt"));

                from("ai-prompt:unselected?tags=not-selected")
                        .setBody(constant("Unselected prompt"));

                from("ai-prompt:invalid?tags=prompt-tests")
                        .setBody(constant(List.of(Map.of("role", "system", "content", "Invalid role"))));

                from("ai-prompt:failure?tags=prompt-tests")
                        .process(exchange -> {
                            throw new IllegalStateException("sensitive route detail");
                        });

                from("ai-prompt:slow?tags=prompt-tests")
                        .process(exchange -> {
                            try {
                                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                            } catch (InterruptedException e) {
                                slowRouteInterrupted.countDown();
                                Thread.currentThread().interrupt();
                            }
                            exchange.getMessage().setBody("completed after interruption");
                        });
            }
        };
    }

    @Test
    void promptIsPublishedAndReturnsMessages() {
        McpServerPrompt prompt = engine.prompts().get("greeting");
        assertThat(prompt).isNotNull();
        assertThat(prompt.description()).isEqualTo("Greeting");

        McpPromptResult result = prompt.handler().get(Map.of("name", "Ada"));

        assertThat(result.isError()).isFalse();
        assertThat(result.messages()).containsExactly(new McpPromptMessage("user", "Hello Ada"));
    }

    @Test
    void listResultSupportsUserAndAssistantMessages() {
        McpPromptResult result = engine.prompts().get("multi_turn").handler().get(Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(result.messages()).containsExactly(
                new McpPromptMessage("user", "Plain user message"),
                new McpPromptMessage("assistant", "Assistant reply"));
    }

    @Test
    void missingRequiredArgumentIsAnArgumentError() {
        McpPromptResult result = engine.prompts().get("greeting").handler().get(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.argumentError()).isTrue();
        assertThat(result.errorMessage()).contains("Missing required argument 'name'");
    }

    @Test
    void onlySelectedTaggedPromptsArePublished() {
        assertThat(engine.prompts()).containsKeys("greeting", "multi_turn", "shared");
        assertThat(engine.prompts()).doesNotContainKeys("untagged", "unselected");
    }

    @Test
    void duplicatePromptNameFallsBackToAnotherSelectedRoute() throws Exception {
        McpServerPrompt first = engine.prompts().get("shared");
        assertThat(first).isNotNull();
        assertThat(first.handler().get(Map.of()).messages())
                .containsExactly(new McpPromptMessage("user", "First version"));

        context.getRouteController().stopRoute("shared-first-route");

        McpServerPrompt replacement = engine.prompts().get("shared");
        assertThat(replacement).isNotNull();
        assertThat(replacement.handler().get(Map.of()).messages())
                .containsExactly(new McpPromptMessage("user", "Second version"));
    }

    @Test
    void invalidRouteResultIsReportedWithoutLeakingDetails() {
        McpPromptResult result = engine.prompts().get("invalid").handler().get(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.argumentError()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Prompt returned an invalid result");
    }

    @Test
    void routeExceptionIsSanitized() {
        McpPromptResult result = engine.prompts().get("failure").handler().get(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.argumentError()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Prompt execution failed");
        assertThat(result.errorMessage()).doesNotContain("sensitive route detail");
    }

    @Test
    void promptTimeoutInterruptsRouteExecution() throws Exception {
        McpPromptResult result = engine.prompts().get("slow").handler().get(Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.errorMessage()).isEqualTo("Prompt execution timed out");
        assertThat(slowRouteInterrupted.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void promptIsRemovedWhenRouteStops() throws Exception {
        context.getRouteController().stopRoute("greeting-route");
        assertThat(engine.prompts()).doesNotContainKey("greeting");
    }
}
