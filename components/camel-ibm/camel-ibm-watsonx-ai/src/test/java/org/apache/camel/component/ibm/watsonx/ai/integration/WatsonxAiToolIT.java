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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.util.List;
import java.util.Map;

import com.ibm.watsonx.ai.tool.UtilityTool;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for watsonx.ai utility tool operations (experimental).
 * <p>
 * This test requires additional system properties:
 * <ul>
 * <li>camel.ibm.watsonx.ai.wxUrl - The wx.ai platform URL (e.g., https://api.dataplatform.cloud.ibm.com/wx)</li>
 * </ul>
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.wxUrl", matches = ".+",
                                 disabledReason = "IBM watsonx.ai WX URL not provided")
})
public class WatsonxAiToolIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiToolIT.class);

    protected static String wxUrl;

    static {
        wxUrl = System.getProperty("camel.ibm.watsonx.ai.wxUrl");
    }

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListTools() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:listTools", null);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof List, "Response should be a list");

        List<UtilityTool> tools = (List<UtilityTool>) body;
        LOG.info("Found {} utility tools", tools.size());

        // Log available tools
        tools.forEach(tool -> LOG.info("Tool: {} - {}", tool.name(), tool.description()));

        // Verify header
        List<UtilityTool> toolsHeader = exchange.getIn().getHeader(WatsonxAiConstants.UTILITY_TOOLS, List.class);
        assertNotNull(toolsHeader, "Tools header should be set");
        assertEquals(tools.size(), toolsHeader.size(), "Header and body should have same tool count");
    }

    @Test
    public void testRunWikipediaTool() throws Exception {
        mockResult.expectedMessageCount(1);

        template.send("direct:runTool", exchange -> {
            exchange.getIn().setHeader(WatsonxAiConstants.TOOL_NAME, "Wikipedia");
            // Wikipedia tool requires structured input with a "query" key
            exchange.getIn().setBody(Map.of("query", "Apache Camel"));
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        String body = exchange.getIn().getBody(String.class);

        assertNotNull(body, "Response body should not be null");
        assertFalse(body.isEmpty(), "Response should not be empty");

        LOG.info("Wikipedia search result length: {} characters", body.length());
        LOG.info("Wikipedia result preview: {}...", body.substring(0, Math.min(200, body.length())));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Use wxUrl for tool service operations
                from("direct:listTools")
                        .to(buildWxEndpointUri("listTools"))
                        .to("mock:result");

                from("direct:runTool")
                        .to(buildWxEndpointUri("runTool"))
                        .to("mock:result");
            }
        };
    }

    /**
     * Builds an endpoint URI using the wx.ai platform URL for tool operations.
     */
    private String buildWxEndpointUri(String operation) {
        StringBuilder uri = new StringBuilder("ibm-watsonx-ai://default");
        uri.append("?apiKey=RAW(").append(apiKey).append(")");
        uri.append("&baseUrl=").append(baseUrl);
        uri.append("&wxUrl=").append(wxUrl);
        uri.append("&operation=").append(operation);
        return uri.toString();
    }
}
