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
package org.apache.camel.component.openai;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tagged OpenAI endpoint must merge the untagged ai-tool "default pool" into its offered tools and keep that offer in
 * sync as a default-pool route is registered, deregistered, suspended, resumed, stopped or restarted at runtime, not
 * only when a tool matching one of its own configured tags changes.
 */
class OpenAIEndpointDefaultPoolToolRefreshTest extends CamelTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AtomicReference<String> capturedRequest = new AtomicReference<>();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("probe")
            .assertRequest(capturedRequest::set)
            .replyWith("ack")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Each test adds its own default-pool ai-tool route at the point in the scenario where it matters, so no
        // route is predefined here.
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no routes
            }
        };
    }

    @Test
    void lateRegisteredDefaultPoolToolAppearsInSubsequentRequests() throws Exception {
        Endpoint endpoint = taggedChatEndpoint("existing");
        assertThat(requestedToolNames(endpoint)).isEmpty();

        addDefaultPoolTool("lateTool", "lateToolRoute");

        assertThat(requestedToolNames(endpoint)).contains("lateTool");
    }

    @Test
    void suspendingDefaultPoolRouteRemovesToolFromSubsequentRequests() throws Exception {
        addDefaultPoolTool("lifecycleTool", "lifecycleToolRoute");
        Endpoint endpoint = taggedChatEndpoint("suspend");
        assertThat(requestedToolNames(endpoint)).contains("lifecycleTool");

        context.getRouteController().suspendRoute("lifecycleToolRoute");

        assertThat(requestedToolNames(endpoint)).doesNotContain("lifecycleTool");
    }

    @Test
    void resumingDefaultPoolRouteRestoresTheTool() throws Exception {
        addDefaultPoolTool("lifecycleTool", "lifecycleToolRoute");
        Endpoint endpoint = taggedChatEndpoint("resume");
        context.getRouteController().suspendRoute("lifecycleToolRoute");
        assertThat(requestedToolNames(endpoint)).doesNotContain("lifecycleTool");

        context.getRouteController().resumeRoute("lifecycleToolRoute");

        assertThat(requestedToolNames(endpoint)).contains("lifecycleTool");
    }

    @Test
    void stoppingDefaultPoolRouteRemovesTheTool() throws Exception {
        addDefaultPoolTool("lifecycleTool", "lifecycleToolRoute");
        Endpoint endpoint = taggedChatEndpoint("stop");
        assertThat(requestedToolNames(endpoint)).contains("lifecycleTool");

        context.getRouteController().stopRoute("lifecycleToolRoute");

        assertThat(requestedToolNames(endpoint)).doesNotContain("lifecycleTool");
    }

    @Test
    void restartingDefaultPoolRouteRestoresTheTool() throws Exception {
        addDefaultPoolTool("lifecycleTool", "lifecycleToolRoute");
        Endpoint endpoint = taggedChatEndpoint("restart");
        context.getRouteController().stopRoute("lifecycleToolRoute");
        assertThat(requestedToolNames(endpoint)).doesNotContain("lifecycleTool");

        context.getRouteController().startRoute("lifecycleToolRoute");

        assertThat(requestedToolNames(endpoint)).contains("lifecycleTool");
    }

    private void addDefaultPoolTool(String name, String routeId) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:" + name + "?description=A default pool tool used by " + routeId)
                        .routeId(routeId)
                        .setBody(constant("ok"));
            }
        });
    }

    /**
     * A tagged endpoint whose configured tag never matches the default-pool tool by name: default-pool tools must still
     * be merged in for every tagged endpoint, per {@code AiToolRegistry.getToolsByTag}.
     */
    private Endpoint taggedChatEndpoint(String tag) throws Exception {
        Endpoint endpoint = context.getEndpoint("openai:chat-completion?model=gpt-5&apiKey=dummy&tags=unrelated-" + tag
                                                + "&autoToolExecution=false&baseUrl=" + openAIMock.getBaseUrl() + "/v1");
        endpoint.start();
        return endpoint;
    }

    private Set<String> requestedToolNames(Endpoint endpoint) throws Exception {
        template.requestBody(endpoint, "probe", String.class);
        JsonNode root = OBJECT_MAPPER.readTree(capturedRequest.get());
        Set<String> names = new HashSet<>();
        if (root.has("tools")) {
            for (JsonNode tool : root.get("tools")) {
                names.add(tool.path("function").path("name").asText());
            }
        }
        return names;
    }
}
