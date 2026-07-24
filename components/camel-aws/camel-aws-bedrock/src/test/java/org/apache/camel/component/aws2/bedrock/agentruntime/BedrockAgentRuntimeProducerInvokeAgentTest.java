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
package org.apache.camel.component.aws2.bedrock.agentruntime;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeInlineAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeInlineAgentResponseHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class BedrockAgentRuntimeProducerInvokeAgentTest {

    @Mock
    private BedrockAgentRuntimeAsyncClient asyncClient;

    private CamelContext camelContext;
    private ProducerTemplate template;
    private AtomicReference<InvokeAgentRequest> capturedAgentRequest;
    private AtomicReference<InvokeInlineAgentRequest> capturedInlineRequest;

    @BeforeEach
    public void setup() throws Exception {
        capturedAgentRequest = new AtomicReference<>();
        capturedInlineRequest = new AtomicReference<>();
        // Capture the request and complete the future immediately. No events are emitted, so the tests focus on how
        // the producer translates the exchange into a correctly-shaped request, and on how the response body is
        // shaped by streamOutputMode. lenient(): the validation tests throw before ever reaching the client.
        lenient().doAnswer(invocation -> {
            capturedAgentRequest.set(invocation.getArgument(0, InvokeAgentRequest.class));
            return CompletableFuture.completedFuture(null);
        }).when(asyncClient).invokeAgent(any(InvokeAgentRequest.class), any(InvokeAgentResponseHandler.class));

        lenient().doAnswer(invocation -> {
            capturedInlineRequest.set(invocation.getArgument(0, InvokeInlineAgentRequest.class));
            return CompletableFuture.completedFuture(null);
        }).when(asyncClient).invokeInlineAgent(any(InvokeInlineAgentRequest.class),
                any(InvokeInlineAgentResponseHandler.class));

        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("asyncClient", asyncClient);
        camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                String common = "?bedrockAgentRuntimeAsyncClient=#asyncClient"
                                + "&region=us-east-1&accessKey=unused&secretKey=unused";

                from("direct:agent")
                        .to("aws-bedrock-agent-runtime://label" + common
                            + "&operation=invokeAgent&agentId=agent-1&agentAliasId=alias-1&sessionId=session-1");

                from("direct:agent-no-config")
                        .to("aws-bedrock-agent-runtime://label2" + common + "&operation=invokeAgent");

                from("direct:agent-chunks")
                        .to("aws-bedrock-agent-runtime://label3" + common
                            + "&operation=invokeAgent&agentId=agent-1&agentAliasId=alias-1&streamOutputMode=chunks");

                from("direct:inline")
                        .to("aws-bedrock-agent-runtime://label4" + common
                            + "&operation=invokeInlineAgent&foundationModel=anthropic.claude-3-haiku-20240307-v1:0"
                            + "&instruction=Be%20helpful");

                from("direct:inline-no-config")
                        .to("aws-bedrock-agent-runtime://label5" + common + "&operation=invokeInlineAgent");
            }
        });
        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    public void teardown() {
        if (template != null) {
            template.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    public void invokeAgentBuildsRequestFromStringBodyAndEndpointConfig() {
        Exchange result = template.send("direct:agent", exchange -> exchange.getMessage().setBody("hello agent"));

        assertNotNull(result);
        InvokeAgentRequest request = capturedAgentRequest.get();
        assertNotNull(request, "Producer must have invoked the async client");
        assertEquals("agent-1", request.agentId());
        assertEquals("alias-1", request.agentAliasId());
        assertEquals("session-1", request.sessionId());
        assertEquals("hello agent", request.inputText(), "The body is sent as the agent input text");
        assertEquals(Boolean.FALSE, request.enableTrace());

        // The session id is echoed back so a route can reuse it to continue the conversation.
        assertEquals("session-1", result.getMessage().getHeader(BedrockAgentRuntimeConstants.SESSION_ID));
    }

    @Test
    public void invokeAgentAllowsHeaderOverrides() {
        template.send("direct:agent", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_ID, "agent-override");
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_ALIAS_ID, "alias-override");
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.SESSION_ID, "session-override");
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_ENABLE_TRACE, Boolean.TRUE);
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_END_SESSION, Boolean.TRUE);
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_MEMORY_ID, "memory-1");
            exchange.getMessage().setBody("hi");
        });

        InvokeAgentRequest request = capturedAgentRequest.get();
        assertNotNull(request);
        assertEquals("agent-override", request.agentId(), "AGENT_ID header must override the endpoint agentId");
        assertEquals("alias-override", request.agentAliasId(),
                "AGENT_ALIAS_ID header must override the endpoint agentAliasId");
        assertEquals("session-override", request.sessionId(), "SESSION_ID header must override the endpoint sessionId");
        assertEquals(Boolean.TRUE, request.enableTrace());
        assertEquals(Boolean.TRUE, request.endSession());
        assertEquals("memory-1", request.memoryId());
    }

    @Test
    public void invokeAgentGeneratesSessionIdWhenNotConfigured() {
        template.send("direct:agent-chunks", exchange -> exchange.getMessage().setBody("hi"));

        InvokeAgentRequest request = capturedAgentRequest.get();
        assertNotNull(request);
        // The API requires a session id, so one is generated when neither config nor header supplies it.
        assertNotNull(request.sessionId(), "A session id must always be sent");
        assertNotEquals("", request.sessionId());
    }

    @Test
    public void invokeAgentFailsWhenAgentIdIsMissing() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:agent-no-config", "hi"));
        Throwable cause = ex.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("agentId"),
                "Error message should mention the missing agentId — was: " + cause.getMessage());
    }

    @Test
    public void completeModeReturnsTheAccumulatedTextAndChunksModeReturnsTheChunkList() {
        // No events are emitted by the mock, so complete mode yields the empty accumulated string while chunks mode
        // yields an empty list. This asserts the streamOutputMode branching shapes the body differently.
        Exchange complete = template.send("direct:agent", exchange -> exchange.getMessage().setBody("hi"));
        assertInstanceOf(String.class, complete.getMessage().getBody(),
                "complete mode (the default) must produce the accumulated text as a String");

        Exchange chunks = template.send("direct:agent-chunks", exchange -> exchange.getMessage().setBody("hi"));
        assertInstanceOf(List.class, chunks.getMessage().getBody(),
                "chunks mode must produce the list of chunks");
    }

    @Test
    public void streamOutputModeCanBeOverriddenByHeader() {
        Exchange result = template.send("direct:agent", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_STREAM_OUTPUT_MODE, "chunks");
            exchange.getMessage().setBody("hi");
        });

        assertInstanceOf(List.class, result.getMessage().getBody(),
                "AGENT_STREAM_OUTPUT_MODE header must override the endpoint streamOutputMode");
    }

    @Test
    public void invokeInlineAgentBuildsRequestFromEndpointConfig() {
        template.send("direct:inline", exchange -> exchange.getMessage().setBody("hello inline"));

        InvokeInlineAgentRequest request = capturedInlineRequest.get();
        assertNotNull(request, "Producer must have invoked the async client");
        assertEquals("anthropic.claude-3-haiku-20240307-v1:0", request.foundationModel());
        assertEquals("Be helpful", request.instruction());
        assertEquals("hello inline", request.inputText());
        assertNotNull(request.sessionId(), "A session id must always be sent");
    }

    @Test
    public void invokeInlineAgentAllowsHeaderOverrides() {
        template.send("direct:inline", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_FOUNDATION_MODEL, "model-override");
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_INSTRUCTION, "instruction-override");
            exchange.getMessage().setBody("hi");
        });

        InvokeInlineAgentRequest request = capturedInlineRequest.get();
        assertNotNull(request);
        assertEquals("model-override", request.foundationModel());
        assertEquals("instruction-override", request.instruction());
    }

    @Test
    public void invokeInlineAgentFailsWhenFoundationModelIsMissing() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:inline-no-config", "hi"));
        Throwable cause = ex.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("foundationModel"),
                "Error message should mention the missing foundationModel — was: " + cause.getMessage());
    }
}
