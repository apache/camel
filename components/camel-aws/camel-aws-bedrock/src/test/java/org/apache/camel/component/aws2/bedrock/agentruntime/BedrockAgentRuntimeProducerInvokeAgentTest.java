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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BedrockAgentRuntimeProducerInvokeAgentTest {

    @Mock
    private BedrockAgentRuntimeAsyncClient asyncClient;

    private CamelContext camelContext;
    private ProducerTemplate template;
    private AtomicReference<InvokeAgentRequest> capturedAgentRequest;
    private AtomicReference<InvokeInlineAgentRequest> capturedInlineRequest;

    @BeforeEach
    void setup() throws Exception {
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
    void teardown() {
        if (template != null) {
            template.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void invokeAgentBuildsRequestFromStringBodyAndEndpointConfig() {
        Exchange result = template.send("direct:agent", exchange -> exchange.getMessage().setBody("hello agent"));

        assertThat(result).isNotNull();
        InvokeAgentRequest request = capturedAgentRequest.get();
        assertThat(request).as("Producer must have invoked the async client").isNotNull();
        assertThat(request.agentId()).isEqualTo("agent-1");
        assertThat(request.agentAliasId()).isEqualTo("alias-1");
        assertThat(request.sessionId()).isEqualTo("session-1");
        assertThat(request.inputText()).as("The body is sent as the agent input text").isEqualTo("hello agent");
        assertThat(request.enableTrace()).isFalse();

        // The session id is echoed back so a route can reuse it to continue the conversation.
        assertThat(result.getMessage().getHeader(BedrockAgentRuntimeConstants.SESSION_ID)).isEqualTo("session-1");
    }

    @Test
    void invokeAgentAllowsHeaderOverrides() {
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
        assertThat(request).isNotNull();
        assertThat(request.agentId()).as("AGENT_ID header must override the endpoint agentId").isEqualTo("agent-override");
        assertThat(request.agentAliasId()).as("AGENT_ALIAS_ID header must override the endpoint agentAliasId")
                .isEqualTo("alias-override");
        assertThat(request.sessionId()).as("SESSION_ID header must override the endpoint sessionId")
                .isEqualTo("session-override");
        assertThat(request.enableTrace()).isTrue();
        assertThat(request.endSession()).isTrue();
        assertThat(request.memoryId()).isEqualTo("memory-1");
    }

    @Test
    void invokeAgentGeneratesSessionIdWhenNotConfigured() {
        template.send("direct:agent-chunks", exchange -> exchange.getMessage().setBody("hi"));

        InvokeAgentRequest request = capturedAgentRequest.get();
        assertThat(request).isNotNull();
        // The API requires a session id, so one is generated when neither config nor header supplies it.
        assertThat(request.sessionId()).as("A session id must always be sent").isNotNull().isNotEmpty();
    }

    @Test
    void invokeAgentFailsWhenAgentIdIsMissing() {
        assertThatThrownBy(() -> template.sendBody("direct:agent-no-config", "hi"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .as("Error message should mention the missing agentId")
                .hasMessageContaining("agentId");
    }

    @Test
    void completeModeReturnsTheAccumulatedTextAndChunksModeReturnsTheChunkList() {
        // No events are emitted by the mock, so complete mode yields the empty accumulated string while chunks mode
        // yields an empty list. This asserts the streamOutputMode branching shapes the body differently.
        Exchange complete = template.send("direct:agent", exchange -> exchange.getMessage().setBody("hi"));
        assertThat(complete.getMessage().getBody())
                .as("complete mode (the default) must produce the accumulated text as a String")
                .isInstanceOf(String.class);

        Exchange chunks = template.send("direct:agent-chunks", exchange -> exchange.getMessage().setBody("hi"));
        assertThat(chunks.getMessage().getBody()).as("chunks mode must produce the list of chunks")
                .isInstanceOf(List.class);
    }

    @Test
    void streamOutputModeCanBeOverriddenByHeader() {
        Exchange result = template.send("direct:agent", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_STREAM_OUTPUT_MODE, "chunks");
            exchange.getMessage().setBody("hi");
        });

        assertThat(result.getMessage().getBody())
                .as("AGENT_STREAM_OUTPUT_MODE header must override the endpoint streamOutputMode")
                .isInstanceOf(List.class);
    }

    @Test
    void invokeInlineAgentBuildsRequestFromEndpointConfig() {
        template.send("direct:inline", exchange -> exchange.getMessage().setBody("hello inline"));

        InvokeInlineAgentRequest request = capturedInlineRequest.get();
        assertThat(request).as("Producer must have invoked the async client").isNotNull();
        assertThat(request.foundationModel()).isEqualTo("anthropic.claude-3-haiku-20240307-v1:0");
        assertThat(request.instruction()).isEqualTo("Be helpful");
        assertThat(request.inputText()).isEqualTo("hello inline");
        assertThat(request.sessionId()).as("A session id must always be sent").isNotNull();
    }

    @Test
    void invokeInlineAgentAllowsHeaderOverrides() {
        template.send("direct:inline", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_FOUNDATION_MODEL, "model-override");
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.AGENT_INSTRUCTION, "instruction-override");
            exchange.getMessage().setBody("hi");
        });

        InvokeInlineAgentRequest request = capturedInlineRequest.get();
        assertThat(request).isNotNull();
        assertThat(request.foundationModel()).isEqualTo("model-override");
        assertThat(request.instruction()).isEqualTo("instruction-override");
    }

    @Test
    void invokeInlineAgentFailsWhenFoundationModelIsMissing() {
        assertThatThrownBy(() -> template.sendBody("direct:inline-no-config", "hi"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .as("Error message should mention the missing foundationModel")
                .hasMessageContaining("foundationModel");
    }
}
