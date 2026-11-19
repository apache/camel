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
package org.apache.camel.component.aws2.bedrock.runtime;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * converse and converseStream send a text body as one user message when the CamelAwsBedrockConverseMessages header is
 * not set, so that a route without Java code (YAML or XML DSL, a Kamelet) can call them.
 */
@ExtendWith(MockitoExtension.class)
public class BedrockConverseTextBodyTest {

    private static final String MODEL = "anthropic.claude-3-haiku-20240307-v1:0";
    private static final String PROMPT = "What is Apache Camel?";

    @Mock
    private BedrockRuntimeClient client;

    @Mock
    private BedrockRuntimeAsyncClient asyncClient;

    private CamelContext camelContext;
    private ProducerTemplate template;

    @BeforeEach
    public void setup() {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("bedrockClient", client);
        registry.bind("bedrockAsyncClient", asyncClient);
        camelContext = new DefaultCamelContext(registry);
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

    private static String converseUri(String operation, String options) {
        return "aws-bedrock://label?bedrockRuntimeClient=#bedrockClient&bedrockRuntimeAsyncClient=#bedrockAsyncClient"
               + "&operation=" + operation + "&modelId=" + MODEL + "&region=us-east-1&accessKey=unused&secretKey=unused"
               + options;
    }

    private ArgumentCaptor<ConverseRequest> answer(String text) {
        ArgumentCaptor<ConverseRequest> request = ArgumentCaptor.forClass(ConverseRequest.class);
        when(client.converse(request.capture())).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.builder().message(Message.builder().role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText(text)).build()).build())
                .stopReason(StopReason.END_TURN)
                .build());
        return request;
    }

    @Test
    public void converseSendsTheTextBodyAsOneUserMessage() {
        ArgumentCaptor<ConverseRequest> request = answer("An integration framework");

        Exchange exchange = template.send(converseUri("converse", "&guardrailIdentifier=abc123&guardrailVersion=1"),
                ex -> ex.getMessage().setBody(PROMPT));

        assertNull(exchange.getException());
        List<Message> messages = request.getValue().messages();
        assertEquals(1, messages.size());
        assertEquals(ConversationRole.USER, messages.get(0).role());
        assertEquals(PROMPT, messages.get(0).content().get(0).text());
        // the guardrail options of the endpoint apply
        assertEquals("abc123", request.getValue().guardrailConfig().guardrailIdentifier());
        assertEquals("1", request.getValue().guardrailConfig().guardrailVersion());
        assertEquals("An integration framework", exchange.getMessage().getBody());
    }

    @Test
    public void theMessagesHeaderWinsOverTheBody() {
        ArgumentCaptor<ConverseRequest> request = answer("Hi");
        Message fromHeader = Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText("Hello")).build();

        template.send(converseUri("converse", ""), ex -> {
            ex.getMessage().setHeader(BedrockConstants.CONVERSE_MESSAGES, List.of(fromHeader));
            ex.getMessage().setBody(PROMPT);
        });

        assertEquals(List.of(fromHeader), request.getValue().messages());
    }

    @Test
    public void converseFailsWithoutMessagesOrBody() {
        Exchange exchange = template.send(converseUri("converse", ""), ex -> ex.getMessage().setBody(null));

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().contains(BedrockConstants.CONVERSE_MESSAGES + " or a text body"),
                exchange.getException().getMessage());
    }

    @Test
    public void converseStreamSendsTheTextBodyAsOneUserMessage() {
        ArgumentCaptor<ConverseStreamRequest> request = ArgumentCaptor.forClass(ConverseStreamRequest.class);
        when(asyncClient.converseStream(request.capture(), any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Exchange exchange = template.send(converseUri("converseStream", ""), ex -> ex.getMessage().setBody(PROMPT));

        assertNull(exchange.getException());
        List<Message> messages = request.getValue().messages();
        assertEquals(1, messages.size());
        assertEquals(ConversationRole.USER, messages.get(0).role());
        assertEquals(PROMPT, messages.get(0).content().get(0).text());
    }
}
