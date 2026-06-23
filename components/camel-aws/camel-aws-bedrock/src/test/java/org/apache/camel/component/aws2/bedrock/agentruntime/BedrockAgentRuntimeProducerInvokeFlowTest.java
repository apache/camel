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
import software.amazon.awssdk.services.bedrockagentruntime.model.FlowInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.InvokeFlowResponseHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class BedrockAgentRuntimeProducerInvokeFlowTest {

    @Mock
    private BedrockAgentRuntimeAsyncClient asyncClient;

    private CamelContext camelContext;
    private ProducerTemplate template;
    private AtomicReference<InvokeFlowRequest> capturedRequest;

    @BeforeEach
    public void setup() throws Exception {
        capturedRequest = new AtomicReference<>();
        // Capture the request and complete the future immediately. We don't emit any events here — the test focuses
        // on verifying that the producer translates the incoming exchange into a correctly-shaped InvokeFlowRequest.
        // lenient(): one of the tests verifies a producer-side validation that throws before reaching the client, so
        // not every test exercises this stubbing.
        lenient().doAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0, InvokeFlowRequest.class));
            return CompletableFuture.completedFuture(null);
        }).when(asyncClient).invokeFlow(any(InvokeFlowRequest.class), any(InvokeFlowResponseHandler.class));

        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("asyncClient", asyncClient);
        camelContext = new DefaultCamelContext(registry);
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:flow-config-default")
                        .to("aws-bedrock-agent-runtime://label"
                            + "?bedrockAgentRuntimeAsyncClient=#asyncClient"
                            + "&operation=invokeFlow"
                            + "&flowIdentifier=flow-1"
                            + "&flowAliasIdentifier=alias-1"
                            + "&region=us-east-1"
                            + "&accessKey=unused"
                            + "&secretKey=unused");

                from("direct:flow-no-config")
                        .to("aws-bedrock-agent-runtime://label2"
                            + "?bedrockAgentRuntimeAsyncClient=#asyncClient"
                            + "&operation=invokeFlow"
                            + "&region=us-east-1"
                            + "&accessKey=unused"
                            + "&secretKey=unused");
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
    public void invokeFlowBuildsRequestFromStringBodyAndEndpointConfig() {
        Exchange result = template.send("direct:flow-config-default",
                exchange -> exchange.getMessage().setBody("hello flow"));

        assertNotNull(result.getException() == null ? "ok" : result.getException().getMessage(), "Exchange should not fail");
        InvokeFlowRequest request = capturedRequest.get();
        assertNotNull(request, "Producer must have invoked the async client");
        assertEquals("flow-1", request.flowIdentifier());
        assertEquals("alias-1", request.flowAliasIdentifier());
        assertEquals(Boolean.FALSE, request.enableTrace());

        List<FlowInput> inputs = request.inputs();
        assertEquals(1, inputs.size(), "A single FlowInput should be derived from the String body");
        FlowInput input = inputs.get(0);
        assertEquals("FlowInputNode", input.nodeName());
        assertEquals("document", input.nodeOutputName());
        assertNotNull(input.content(), "Input content must be populated");
        assertNotNull(input.content().document(), "Input document must be populated");
        assertTrue(input.content().document().isString(), "Document should be a string");
        assertEquals("hello flow", input.content().document().asString());

        // Default response body is left untouched (no events emitted by the mock); flow headers default to empty.
        Object outputs = result.getMessage().getHeader(BedrockAgentRuntimeConstants.FLOW_OUTPUTS);
        assertInstanceOf(List.class, outputs);
        assertTrue(((List<?>) outputs).isEmpty(), "FLOW_OUTPUTS header should be an empty list when no events were emitted");
    }

    @Test
    public void invokeFlowAllowsHeaderOverridesForFlowIdentifiers() {
        template.send("direct:flow-config-default", exchange -> {
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.FLOW_IDENTIFIER, "flow-override");
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.FLOW_ALIAS_IDENTIFIER, "alias-override");
            exchange.getMessage().setHeader(BedrockAgentRuntimeConstants.FLOW_ENABLE_TRACE, Boolean.TRUE);
            exchange.getMessage().setBody("hi");
        });

        InvokeFlowRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals("flow-override", request.flowIdentifier(),
                "Header CamelAwsBedrockAgentRuntimeFlowIdentifier must override the endpoint flowIdentifier");
        assertEquals("alias-override", request.flowAliasIdentifier(),
                "Header CamelAwsBedrockAgentRuntimeFlowAliasIdentifier must override the endpoint flowAliasIdentifier");
        assertEquals(Boolean.TRUE, request.enableTrace(),
                "Header CamelAwsBedrockAgentRuntimeFlowEnableTrace must override enableTrace");
    }

    @Test
    public void invokeFlowFailsWhenFlowIdentifierIsMissing() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:flow-no-config", "hi"));
        Throwable cause = ex.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause);
        assertTrue(cause.getMessage().contains("flowIdentifier"),
                "Error message should mention the missing flowIdentifier — was: " + cause.getMessage());
    }
}
