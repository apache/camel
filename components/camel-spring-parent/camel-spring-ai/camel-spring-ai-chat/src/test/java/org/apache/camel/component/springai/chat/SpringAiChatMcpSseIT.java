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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingSseService;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingSseServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for MCP client support using SSE transport with the MCP Everything Server.
 *
 * Uses the tzolov/mcp-everything-server:v3 Docker container in SSE mode, which provides echo and add tools.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Disabled unless running in CI")
public class SpringAiChatMcpSseIT extends OllamaTestSupport {

    @RegisterExtension
    static McpEverythingSseService MCP_EVERYTHING = McpEverythingSseServiceFactory.createSingletonService();

    @Test
    public void testMcpEchoTool() {
        String response = template().requestBody("direct:mcpSseChat",
                "Use the echo tool to echo the message 'Hello from Camel'.", String.class);

        assertThat(response).isNotNull();
        assertThat(response.toLowerCase()).containsAnyOf("hello", "camel");
    }

    @Test
    public void testMcpAddTool() {
        String response = template().requestBody("direct:mcpSseChat",
                "Use the add tool to add 17 and 25.", String.class);

        assertThat(response).isNotNull();
        assertThat(response).containsAnyOf("42", "forty-two", "forty two");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        String sseUrl = MCP_EVERYTHING.sseUrl();

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                bindChatModel(getCamelContext());

                from("direct:mcpSseChat")
                        .toF("spring-ai-chat:mcpSseChat?chatModel=#chatModel"
                             + "&mcpServer.everything.transportType=sse"
                             + "&mcpServer.everything.url=%s",
                                sseUrl);
            }
        };
    }
}
