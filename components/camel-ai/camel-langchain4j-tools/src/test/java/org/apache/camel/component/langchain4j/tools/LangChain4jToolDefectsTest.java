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
package org.apache.camel.component.langchain4j.tools;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the three defects reported in CAMEL-23943:
 * <ol>
 * <li>Unbounded tool-calling loop (do/while(true) with no iteration cap)</li>
 * <li>Crash on hallucinated tool names (.findFirst().get() throws NoSuchElementException)</li>
 * <li>Tool errors swallowed silently</li>
 * </ol>
 */
class LangChain4jToolDefectsTest extends CamelTestSupport {

    protected ChatModel chatModel;

    @RegisterExtension
    static OpenAIMock openAIMock = new OpenAIMock().builder()
            // Scenario 1: LLM keeps requesting tools — 5 sequential round trips
            .when("Do something that requires many round trips\n")
            .invokeTool("RealTool")
            .withParam("input", "round1")
            .andThenInvokeTool("RealTool")
            .withParam("input", "round2")
            .andThenInvokeTool("RealTool")
            .withParam("input", "round3")
            .andThenInvokeTool("RealTool")
            .withParam("input", "round4")
            .andThenInvokeTool("RealTool")
            .withParam("input", "round5")
            .end()
            // Scenario 2: LLM hallucinates a tool name that doesn't exist
            .when("Call a tool that does not exist\n")
            .invokeTool("HallucinatedToolXyz")
            .withParam("input", "test")
            .end()
            // Scenario 3: LLM calls a tool that throws an exception
            .when("Call the failing tool\n")
            .invokeTool("FailingTool")
            .withParam("input", "test")
            .build();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        chatModel = ToolsHelper.createModel(openAIMock.getBaseUrl());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        LangChain4jToolsComponent component
                = context.getComponent(LangChain4jTools.SCHEME, LangChain4jToolsComponent.class);
        component.getConfiguration().setChatModel(chatModel);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Producer with low maxToolCallingRoundTrips for testing the iteration cap
                from("direct:testMaxRoundTrips")
                        .to("langchain4j-tools:test1?tags=defect-test&maxToolCallingRoundTrips=2");

                // Producer with default settings for hallucinated tool and error tests
                from("direct:testHallucinatedTool")
                        .to("langchain4j-tools:test1?tags=defect-test");

                from("direct:testToolError")
                        .to("langchain4j-tools:test1?tags=defect-test");

                // A real tool that succeeds
                from("langchain4j-tools:test1?tags=defect-test&name=RealTool"
                     + "&description=A real tool that works&parameter.input=string")
                        .setBody(simple("{\"result\": \"ok\", \"input\": \"${header.input}\"}"));

                // A tool that always throws an exception
                from("langchain4j-tools:test1?tags=defect-test&name=FailingTool"
                     + "&description=A tool that always fails&parameter.input=string")
                        .throwException(new RuntimeException("Tool execution failed intentionally"));
            }
        };
    }

    /**
     * CAMEL-23943 defect 1: The do/while(true) loop in toolsChat has no iteration cap. If the LLM keeps requesting
     * tools indefinitely, this never terminates.
     *
     * With the fix, the loop is bounded by maxToolCallingRoundTrips (default 10, set to 2 for this test). Exceeding the
     * cap throws a RuntimeCamelException.
     */
    @Test
    void testMaxToolCallingRoundTripsExceeded() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("You are a helpful assistant."));
        messages.add(new UserMessage("Do something that requires many round trips\n"));

        Exchange exchange = fluentTemplate.to("direct:testMaxRoundTrips")
                .withBody(messages).request(Exchange.class);

        assertThat(exchange).isNotNull();
        assertThat(exchange.getException())
                .as("Should throw when max round trips exceeded")
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("maximum round trips (2)");
    }

    /**
     * CAMEL-23943 defect 2: When the LLM hallucinates a tool name that doesn't exist, .findFirst().get() throws
     * NoSuchElementException, crashing the producer.
     *
     * With the fix, the producer sends an error message back to the LLM listing available tools, and the LLM responds
     * with a final text answer instead of crashing.
     */
    @Test
    void testHallucinatedToolNameHandledGracefully() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("You are a helpful assistant."));
        messages.add(new UserMessage("Call a tool that does not exist\n"));

        Exchange exchange = fluentTemplate.to("direct:testHallucinatedTool")
                .withBody(messages).request(Exchange.class);

        assertThat(exchange).isNotNull();
        // Should NOT have an exception — the error is sent back to the LLM gracefully
        assertThat(exchange.getException())
                .as("Hallucinated tool should not crash the producer")
                .isNull();
        // The LLM should have responded with a final text answer
        String body = exchange.getMessage().getBody(String.class);
        assertThat(body)
                .as("LLM should return a text response after receiving the tool error")
                .isNotNull();
    }

    /**
     * CAMEL-23943 defect 3: When a tool route throws an exception, the catch block sets toolExchange.setException(e)
     * but the error is swallowed — it's never propagated back to the LLM and subsequent logic overwrites the exchange
     * state.
     *
     * With the fix, the error is sent back to the LLM as a ToolExecutionResultMessage, allowing the LLM to handle it
     * gracefully.
     */
    @Test
    void testToolExecutionErrorPropagatedToLLM() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("You are a helpful assistant."));
        messages.add(new UserMessage("Call the failing tool\n"));

        Exchange exchange = fluentTemplate.to("direct:testToolError")
                .withBody(messages).request(Exchange.class);

        assertThat(exchange).isNotNull();
        // Should NOT have an exception — the tool error is sent back to the LLM gracefully
        assertThat(exchange.getException())
                .as("Tool error should not crash the producer — it should be sent to the LLM")
                .isNull();
        // The LLM should have responded with a final text answer after receiving the error
        String body = exchange.getMessage().getBody(String.class);
        assertThat(body)
                .as("LLM should return a text response after receiving the tool error")
                .isNotNull();
    }
}
