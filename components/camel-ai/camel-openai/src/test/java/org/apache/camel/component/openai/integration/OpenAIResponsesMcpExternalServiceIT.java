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
package org.apache.camel.component.openai.integration;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.openai.OpenAIComponent;
import org.apache.camel.component.openai.OpenAIConstants;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingService;
import org.apache.camel.test.infra.mcp.everything.services.McpEverythingServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the tool loop of the responses operation against a Responses API service and the MCP Everything server.
 */
@EnabledIfSystemProperty(named = OpenAIExternalServiceTestSupport.ENABLE_LIVE_TESTS, matches = "true",
                         disabledReason = "Set -Dopenai.live.tests=true and configure an OpenAI-compatible Responses API service")
public class OpenAIResponsesMcpExternalServiceIT extends OpenAIExternalServiceTestSupport {

    private static final String RESPONSES_MODEL = "openai.live.responses.model";

    @RegisterExtension
    static McpEverythingService MCP_EVERYTHING = McpEverythingServiceFactory.createSingletonService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getComponent("openai", OpenAIComponent.class).setModel(requiredProperty(RESPONSES_MODEL));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String mcpUrl = MCP_EVERYTHING.url();
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:responses-mcp")
                        .toF("openai:responses?temperature=0"
                             + "&mcpServer.everything.transportType=streamableHttp"
                             + "&mcpServer.everything.url=%s"
                             + "&mcpProtocolVersions=2024-11-05,2025-03-26,2025-06-18",
                                mcpUrl);
            }
        };
    }

    @Test
    void mcpServerToolsAreCalledByTheModel() {
        Exchange result = template.request("direct:responses-mcp",
                e -> e.getIn().setBody("Use the add tool to add 17 and 25. What is the result?"));

        assertThat(result.getException()).isNull();
        List<?> toolCalls = result.getMessage().getHeader(OpenAIConstants.MCP_TOOL_CALLS, List.class);
        assertThat(toolCalls).isNotNull().asList().contains("add");
        assertThat(result.getMessage().getBody(String.class)).contains("42");
    }
}
