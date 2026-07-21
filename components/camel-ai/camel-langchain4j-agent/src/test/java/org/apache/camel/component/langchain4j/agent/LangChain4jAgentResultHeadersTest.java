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
package org.apache.camel.component.langchain4j.agent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.Headers;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LangChain4jAgentResultHeadersTest extends CamelTestSupport {

    private final AtomicReference<Agent> agentRef = new AtomicReference<>();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("testAgent", (Agent) (body, exchange) -> agentRef.get().chat(body, exchange));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-agent:test?agent=#testAgent")
                        .to("mock:result");
            }
        };
    }

    @BeforeEach
    void resetAgent() {
        agentRef.set((body, exchange) -> {
            throw new UnsupportedOperationException("Agent stub must be configured per test");
        });
    }

    @Test
    void shouldPopulateAllResultHeaders() throws Exception {
        Content ragSource = Content.from("Apache Camel documentation excerpt");
        ToolExecution toolExecution = toolExecution("call-1", "weather", "{\"city\":\"London\"}", "Sunny, 22C");

        agentRef.set((body, exchange) -> Result.<String> builder()
                .content("The weather in London is sunny.")
                .finishReason(FinishReason.STOP)
                .tokenUsage(new TokenUsage(120, 30, 150))
                .sources(List.of(ragSource))
                .toolExecutions(List.of(toolExecution))
                .build());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "What is the weather in London?");

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals("The weather in London is sunny.", exchange.getMessage().getBody(String.class));
        assertEquals(FinishReason.STOP, exchange.getMessage().getHeader(Headers.FINISH_REASON));
        assertEquals(120, exchange.getMessage().getHeader(Headers.INPUT_TOKEN_COUNT, Integer.class));
        assertEquals(30, exchange.getMessage().getHeader(Headers.OUTPUT_TOKEN_COUNT, Integer.class));
        assertEquals(150, exchange.getMessage().getHeader(Headers.TOTAL_TOKEN_COUNT, Integer.class));

        @SuppressWarnings("unchecked")
        List<Content> sources = exchange.getMessage().getHeader(Headers.SOURCES, List.class);
        assertEquals(1, sources.size());
        assertSame(ragSource, sources.get(0));

        @SuppressWarnings("unchecked")
        List<ToolExecution> toolExecutions = exchange.getMessage().getHeader(Headers.TOOL_EXECUTIONS, List.class);
        assertEquals(1, toolExecutions.size());
        assertSame(toolExecution, toolExecutions.get(0));
        assertEquals("weather", toolExecutions.get(0).request().name());
        assertEquals("Sunny, 22C", toolExecutions.get(0).result());
    }

    @Test
    void shouldNotSetSourcesOrToolExecutionsWhenEmpty() throws Exception {
        agentRef.set((body, exchange) -> Result.<String> builder()
                .content("Plain answer")
                .finishReason(FinishReason.STOP)
                .tokenUsage(new TokenUsage(10, 5, 15))
                .sources(Collections.emptyList())
                .toolExecutions(Collections.emptyList())
                .build());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals("Plain answer", exchange.getMessage().getBody(String.class));
        assertEquals(FinishReason.STOP, exchange.getMessage().getHeader(Headers.FINISH_REASON));
        assertNull(exchange.getMessage().getHeader(Headers.SOURCES));
        assertNull(exchange.getMessage().getHeader(Headers.TOOL_EXECUTIONS));
    }

    @Test
    void shouldNotSetSourcesOrToolExecutionsWhenAbsent() throws Exception {
        agentRef.set((body, exchange) -> Result.<String> builder()
                .content("Minimal answer")
                .build());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals("Minimal answer", exchange.getMessage().getBody(String.class));
        assertNull(exchange.getMessage().getHeader(Headers.FINISH_REASON));
        assertNull(exchange.getMessage().getHeader(Headers.INPUT_TOKEN_COUNT));
        assertNull(exchange.getMessage().getHeader(Headers.OUTPUT_TOKEN_COUNT));
        assertNull(exchange.getMessage().getHeader(Headers.TOTAL_TOKEN_COUNT));
        assertNull(exchange.getMessage().getHeader(Headers.SOURCES));
        assertNull(exchange.getMessage().getHeader(Headers.TOOL_EXECUTIONS));
    }

    @Test
    void shouldPopulateOnlySourcesWhenNoToolsExecuted() throws Exception {
        Content ragSource = Content.from("Product manual section 3.2");

        agentRef.set((body, exchange) -> Result.<String> builder()
                .content("Based on the manual, reset the device.")
                .sources(List.of(ragSource))
                .build());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "How do I reset?");

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = mock.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        List<Content> sources = exchange.getMessage().getHeader(Headers.SOURCES, List.class);
        assertEquals(1, sources.size());
        assertSame(ragSource, sources.get(0));
        assertNull(exchange.getMessage().getHeader(Headers.TOOL_EXECUTIONS));
    }

    @Test
    void shouldPopulateOnlyToolExecutionsWhenNoRagSources() throws Exception {
        ToolExecution toolExecution = toolExecution("call-2", "inventory", "{\"sku\":\"ABC-123\"}", "42 units in stock");

        agentRef.set((body, exchange) -> Result.<String> builder()
                .content("There are 42 units in stock.")
                .toolExecutions(List.of(toolExecution))
                .build());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Stock level for ABC-123?");

        mock.assertIsSatisfied(10, TimeUnit.SECONDS);

        Exchange exchange = mock.getExchanges().get(0);
        assertNull(exchange.getMessage().getHeader(Headers.SOURCES));
        @SuppressWarnings("unchecked")
        List<ToolExecution> toolExecutions = exchange.getMessage().getHeader(Headers.TOOL_EXECUTIONS, List.class);
        assertEquals(1, toolExecutions.size());
        assertEquals("inventory", toolExecutions.get(0).request().name());
        assertFalse(toolExecutions.get(0).hasFailed());
    }

    private static ToolExecution toolExecution(String id, String name, String arguments, String resultText) {
        return ToolExecution.builder()
                .request(ToolExecutionRequest.builder()
                        .id(id)
                        .name(name)
                        .arguments(arguments)
                        .build())
                .result(ToolExecutionResult.builder().resultText(resultText).build())
                .invocationContext(InvocationContext.builder().build())
                .build();
    }
}
