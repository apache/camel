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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.resource.AiResource;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bridge stamps the MCP caller identity on the exchange of a resource read (fixed and templated) and a prompt get,
 * exactly as for a tool call, when the engine passes a caller context; an engine that calls the methods without a
 * context, or passes the empty context, gets no identity on the exchange.
 */
class McpServerBridgeCallerIdentityTest extends CamelTestSupport {

    private static final List<String> IDENTITY_PROPERTIES = List.of(
            McpToolCallContext.SECURITY_PRINCIPAL_PROPERTY,
            McpToolCallContext.SECURITY_PRINCIPAL_NAME_PROPERTY,
            McpToolCallContext.SECURITY_PRINCIPAL_ROLES_PROPERTY,
            McpToolCallContext.OAUTH_SUBJECT_PROPERTY,
            McpToolCallContext.OAUTH_SCOPES_PROPERTY,
            McpToolCallContext.OAUTH_CLAIMS_PROPERTY,
            McpToolCallContext.OAUTH_TOKEN_VALIDATION_RESULT_PROPERTY);

    private final RecordingMcpServerEngine engine = new RecordingMcpServerEngine();
    // the identity properties each route saw on its last request, keyed by route
    private final Map<String, Map<String, Object>> seen = new ConcurrentHashMap<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.getRegistry().bind("mcpServerEngine", engine);
        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags("secured");
        camelContext.addService(new McpServerBridge(configuration));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-resource:who_res?resourceUri=camel:///whoami&tags=secured&mimeType=text/plain")
                        .process(exchange -> capture("resource", exchange));

                from("ai-resource:who_template?uriTemplate=crm://customers/{id}&tags=secured&mimeType=text/plain")
                        .process(exchange -> capture("template", exchange));

                from("ai-prompt:who_prompt?tags=secured&argument.topic.required=false")
                        .process(exchange -> capture("prompt", exchange));
            }
        };
    }

    private void capture(String route, Exchange exchange) {
        Map<String, Object> properties = new HashMap<>();
        for (String name : IDENTITY_PROPERTIES) {
            Object value = exchange.getProperty(name);
            if (value != null) {
                properties.put(name, value);
            }
        }
        seen.put(route, properties);
        exchange.getMessage().setBody("name=" + exchange.getProperty(McpToolCallContext.SECURITY_PRINCIPAL_NAME_PROPERTY)
                                      + ",id=" + exchange.getMessage()
                                              .getHeader(AiResource.TEMPLATE_VARIABLE_HEADER_PREFIX + "id")
                                      + ",topic=" + exchange.getMessage().getHeader("topic"));
    }

    private static McpToolCallContext callerContext(Object principal) {
        Map<String, Object> claims = Map.of("sub", "alice", "roles", List.of("crm-reader"));
        OAuthTokenValidationResult validation = OAuthTokenValidationResult.valid(
                "alice", "https://id.example.test", List.of("mcp-server"), List.of("mcp:read"), claims, 123L);
        return new McpToolCallContext(
                principal, "alice", List.of("crm-reader"), "alice", List.of("mcp:read"), claims, validation);
    }

    private void assertCallerStamped(String route, Object principal, McpToolCallContext context) {
        assertThat(seen.get(route))
                .containsEntry(McpToolCallContext.SECURITY_PRINCIPAL_PROPERTY, principal)
                .containsEntry(McpToolCallContext.SECURITY_PRINCIPAL_NAME_PROPERTY, "alice")
                .containsEntry(McpToolCallContext.SECURITY_PRINCIPAL_ROLES_PROPERTY, List.of("crm-reader"))
                .containsEntry(McpToolCallContext.OAUTH_SUBJECT_PROPERTY, "alice")
                .containsEntry(McpToolCallContext.OAUTH_SCOPES_PROPERTY, List.of("mcp:read"))
                .containsEntry(McpToolCallContext.OAUTH_CLAIMS_PROPERTY, context.oauthClaims())
                .containsEntry(McpToolCallContext.OAUTH_TOKEN_VALIDATION_RESULT_PROPERTY,
                        context.oauthTokenValidationResult());
        assertThat(seen.get(route).get(McpToolCallContext.SECURITY_PRINCIPAL_PROPERTY)).isSameAs(principal);
    }

    @Test
    void resourceReadWithCallerStampsIdentity() {
        Object principal = new Object();
        McpToolCallContext context = callerContext(principal);

        McpResourceReadResult result = engine.resources().get("camel:///whoami").handler().read(context);

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).startsWith("name=alice,");
        assertCallerStamped("resource", principal, context);
    }

    @Test
    void resourceReadWithoutContextStampsNothing() {
        McpResourceReadResult result = engine.resources().get("camel:///whoami").handler().read();

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).startsWith("name=null,");
        assertThat(seen.get("resource")).isEmpty();
    }

    @Test
    void resourceReadWithEmptyContextStampsNothing() {
        McpResourceReadResult result = engine.resources().get("camel:///whoami").handler()
                .read(McpToolCallContext.EMPTY);

        assertThat(result.isError()).isFalse();
        assertThat(seen.get("resource")).isEmpty();
    }

    @Test
    void templateReadWithCallerStampsIdentityAndKeepsTheVariables() {
        Object principal = new Object();
        McpToolCallContext context = callerContext(principal);

        McpResourceReadResult result = engine.resourceTemplates().get("crm://customers/{id}").handler()
                .read("crm://customers/42", context);

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).isEqualTo("name=alice,id=42,topic=null");
        assertCallerStamped("template", principal, context);
    }

    @Test
    void templateReadWithoutContextStampsNothing() {
        McpResourceReadResult result = engine.resourceTemplates().get("crm://customers/{id}").handler()
                .read("crm://customers/42");

        assertThat(result.isError()).isFalse();
        assertThat(result.text()).isEqualTo("name=null,id=42,topic=null");
        assertThat(seen.get("template")).isEmpty();
    }

    @Test
    void templateReadWithEmptyContextStampsNothing() {
        McpResourceReadResult result = engine.resourceTemplates().get("crm://customers/{id}").handler()
                .read("crm://customers/42", McpToolCallContext.EMPTY);

        assertThat(result.isError()).isFalse();
        assertThat(seen.get("template")).isEmpty();
    }

    @Test
    void promptGetWithCallerStampsIdentity() {
        Object principal = new Object();
        McpToolCallContext context = callerContext(principal);

        McpPromptResult result = engine.prompts().get("who_prompt").handler().get(Map.of("topic", "orders"), context);

        assertThat(result.isError()).isFalse();
        assertThat(result.messages()).singleElement()
                .extracting(McpPromptMessage::content).isEqualTo("name=alice,id=null,topic=orders");
        assertCallerStamped("prompt", principal, context);
    }

    @Test
    void promptGetWithoutContextStampsNothing() {
        McpPromptResult result = engine.prompts().get("who_prompt").handler().get(Map.of("topic", "orders"));

        assertThat(result.isError()).isFalse();
        assertThat(result.messages()).singleElement()
                .extracting(McpPromptMessage::content).isEqualTo("name=null,id=null,topic=orders");
        assertThat(seen.get("prompt")).isEmpty();
    }

    @Test
    void promptGetWithEmptyContextStampsNothing() {
        McpPromptResult result = engine.prompts().get("who_prompt").handler().get(Map.of(), McpToolCallContext.EMPTY);

        assertThat(result.isError()).isFalse();
        assertThat(seen.get("prompt")).isEmpty();
    }

    @Test
    void handlerWithoutContextOverloadIgnoresTheContext() {
        // a handler that implements only the original method, as an engine-side lambda does, keeps working when an
        // engine passes a caller context: the default overload delegates to the original method
        McpToolCallContext context = callerContext(new Object());
        McpResourceReadHandler resource = () -> McpResourceReadResult.text("fixed");
        McpResourceTemplateReadHandler template = uri -> McpResourceReadResult.text("read " + uri);
        McpPromptHandler prompt = arguments -> McpPromptResult.success("d",
                List.of(new McpPromptMessage("user", "hello " + arguments.get("name"))));

        assertThat(resource.read(context).text()).isEqualTo("fixed");
        assertThat(template.read("crm://customers/7", context).text()).isEqualTo("read crm://customers/7");
        assertThat(prompt.get(Map.of("name", "Camel"), context).messages()).singleElement()
                .extracting(McpPromptMessage::content).isEqualTo("hello Camel");
    }
}
