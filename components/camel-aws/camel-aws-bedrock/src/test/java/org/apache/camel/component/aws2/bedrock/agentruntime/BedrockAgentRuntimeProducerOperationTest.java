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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.BedrockAgentRuntimeClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeAgentResponseHandler;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * The operation of an exchange: no operation, and an operation picked by the header that needs the async client on an
 * endpoint of an operation that does not.
 */
@ExtendWith(MockitoExtension.class)
class BedrockAgentRuntimeProducerOperationTest {

    @Mock
    private BedrockAgentRuntimeClient syncClient;

    @Mock
    private BedrockAgentRuntimeAsyncClient asyncClient;

    private CamelContext camelContext;
    private ProducerTemplate template;
    private AtomicReference<InvokeFlowRequest> capturedFlowRequest;
    private AtomicReference<InvokeAgentRequest> capturedAgentRequest;

    @BeforeEach
    void setup() throws Exception {
        capturedFlowRequest = new AtomicReference<>();
        capturedAgentRequest = new AtomicReference<>();
        lenient().doAnswer(invocation -> {
            capturedFlowRequest.set(invocation.getArgument(0, InvokeFlowRequest.class));
            return CompletableFuture.completedFuture(null);
        }).when(asyncClient).invokeFlow(any(InvokeFlowRequest.class), any(InvokeFlowResponseHandler.class));
        lenient().doAnswer(invocation -> {
            capturedAgentRequest.set(invocation.getArgument(0, InvokeAgentRequest.class));
            return CompletableFuture.completedFuture(null);
        }).when(asyncClient).invokeAgent(any(InvokeAgentRequest.class), any(InvokeAgentResponseHandler.class));

        // only the sync client is in the registry, so the async client is not autowired
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("syncClient", syncClient);
        camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                String common = "?bedrockAgentRuntimeClient=#syncClient&region=us-east-1&accessKey=unused&secretKey=unused";

                from("direct:retrieve")
                        .to("aws-bedrock-agent-runtime://label" + common
                            + "&operation=retrieve&knowledgeBaseId=kb-1"
                            + "&flowIdentifier=flow-1&flowAliasIdentifier=alias-1"
                            + "&agentId=agent-1&agentAliasId=agent-alias-1");

                from("direct:no-operation")
                        .to("aws-bedrock-agent-runtime://label2" + common);
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
    void noOperationFailsWithMessage() {
        assertThatThrownBy(() -> template.sendBody("direct:no-operation", "hi"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation option")
                .hasMessageContaining(BedrockAgentRuntimeConstants.OPERATION);
    }

    @Test
    void operationHeaderPicksInvokeFlowOnRetrieveEndpoint() {
        try (MockedStatic<BedrockAgentRuntimeClientFactory> factory = mockStatic(BedrockAgentRuntimeClientFactory.class)) {
            factory.when(() -> BedrockAgentRuntimeClientFactory.getBedrockAgentRuntimeAsyncClient(any()))
                    .thenReturn(asyncClient);

            template.sendBodyAndHeader("direct:retrieve", "hello flow", BedrockAgentRuntimeConstants.OPERATION,
                    BedrockAgentRuntimeOperations.invokeFlow);
            assertThat(capturedFlowRequest.get()).isNotNull();
            assertThat(capturedFlowRequest.get().flowIdentifier()).isEqualTo("flow-1");
            assertThat(capturedFlowRequest.get().inputs().get(0).content().document().asString()).isEqualTo("hello flow");

            template.sendBodyAndHeader("direct:retrieve", "hello agent", BedrockAgentRuntimeConstants.OPERATION,
                    "invokeAgent");
            assertThat(capturedAgentRequest.get()).isNotNull();
            assertThat(capturedAgentRequest.get().agentId()).isEqualTo("agent-1");
            assertThat(capturedAgentRequest.get().inputText()).isEqualTo("hello agent");

            // the async client is created once, on first use, and kept for the next exchanges
            factory.verify(() -> BedrockAgentRuntimeClientFactory.getBedrockAgentRuntimeAsyncClient(any()), times(1));
        }
    }
}
