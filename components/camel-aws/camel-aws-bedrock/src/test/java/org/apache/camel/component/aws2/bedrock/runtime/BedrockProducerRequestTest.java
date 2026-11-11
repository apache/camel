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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.aws2.bedrock.BedrockModels;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTrace;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ResponseStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The request the producer sends to the model, and how it reads the model of the request (CAMEL-26069). Mocked clients
 * stand in for AWS, so no credentials or network access are needed.
 */
@ExtendWith(MockitoExtension.class)
public class BedrockProducerRequestTest {

    private static final String REQUEST = "{\"inputText\":\"Hello\"}";
    private static final String TITAN = BedrockModels.TITAN_TEXT_EXPRESS_V1.model;
    private static final String CLAUDE = BedrockModels.ANTROPHIC_CLAUDE_HAIKU_V3.model;

    @Mock
    private BedrockRuntimeClient client;

    @Mock
    private BedrockRuntimeAsyncClient asyncClient;

    @TempDir
    Path tempDir;

    private CamelContext camelContext;
    private ProducerTemplate template;

    @BeforeEach
    public void setup() {
        lenient().when(client.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(InvokeModelResponse.builder().body(SdkBytes.fromUtf8String("{\"results\":[]}")).build());

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

    @Test
    public void stringBodyIsSent() {
        assertEquals(REQUEST, sentRequest(REQUEST));
    }

    @Test
    public void bytesBodyIsSentAsTheBytes() {
        assertEquals(REQUEST, sentRequest(REQUEST.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void streamBodyIsSentAsItsContent() {
        assertEquals(REQUEST, sentRequest(new ByteArrayInputStream(REQUEST.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void fileBodyIsSentAsItsContent() throws Exception {
        Path file = Files.writeString(tempDir.resolve("request.json"), REQUEST);
        assertEquals(REQUEST, sentRequest(file.toFile()));
    }

    @Test
    public void mapBodyIsSentAsJson() {
        assertEquals(REQUEST, sentRequest(Map.of("inputText", "Hello")));
    }

    @Test
    public void jsonNodeBodyIsSentAsJson() throws Exception {
        assertEquals(REQUEST, sentRequest(JsonMapper.builder().build().readTree(REQUEST)));
    }

    @Test
    public void noBodyFails() {
        Exchange exchange = send(invokeUri("invokeTextModel", TITAN), null);

        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
    }

    @Test
    public void imageAndEmbeddingsOperationsSendTheBodyToo() {
        ArgumentCaptor<InvokeModelRequest> request = ArgumentCaptor.forClass(InvokeModelRequest.class);
        when(client.invokeModel(request.capture()))
                .thenReturn(InvokeModelResponse.builder().body(SdkBytes.fromUtf8String("{\"images\":[]}")).build());

        byte[] body = REQUEST.getBytes(StandardCharsets.UTF_8);
        assertNull(send(invokeUri("invokeImageModel", BedrockModels.TITAN_IMAGE_GENERATOR_V1.model), body).getException());
        assertNull(send(invokeUri("invokeEmbeddingsModel", BedrockModels.TITAN_EMBEDDINGS_G1.model), body).getException());

        assertEquals(2, request.getAllValues().size());
        request.getAllValues().forEach(r -> assertEquals(REQUEST, r.body().asUtf8String()));
    }

    @Test
    public void noOperationFailsWithAMessage() {
        Exchange exchange = send("aws-bedrock://label?bedrockRuntimeClient=#bedrockClient&modelId=" + TITAN
                                 + "&region=us-east-1&accessKey=unused&secretKey=unused",
                REQUEST);

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertEquals("Operation must be specified with the operation option or the CamelAwsBedrockOperation header",
                exchange.getException().getMessage());
    }

    @Test
    public void titanEmbeddingsV2IsRejectedByInvokeTextModel() {
        Exchange exchange = send(invokeUri("invokeTextModel", BedrockModels.TITAN_TEXT_EMBEDDINGS_V2.model), REQUEST);

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().contains("invokeEmbeddingsModel"),
                exchange.getException().getMessage());
    }

    @Test
    public void jambaResponseGivesTheChoices() {
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{\"choices\":[{\"message\":{\"content\":\"Hi\"}}]}")).build());

        Exchange exchange = send(invokeUri("invokeTextModel", BedrockModels.JAMBA_1_5_MINI.model), REQUEST);

        assertNull(exchange.getException());
        JsonNode body = assertInstanceOf(JsonNode.class, exchange.getMessage().getBody());
        assertEquals("Hi", body.get(0).get("message").get("content").asString());
    }

    @Test
    public void pojoRequestReadsTheResponseWithTheModelOfTheRequest() {
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}")).build());
        InvokeModelRequest request = InvokeModelRequest.builder().modelId(CLAUDE)
                .body(SdkBytes.fromUtf8String(REQUEST)).build();

        // no modelId option on the endpoint: the request carries the model
        Exchange exchange = send(pojoUri("invokeTextModel"), request);

        assertNull(exchange.getException());
        JsonNode body = assertInstanceOf(JsonNode.class, exchange.getMessage().getBody());
        assertEquals("Hi", body.get("content").get(0).get("text").asString());
    }

    @Test
    public void streamingPojoRequestUsesTheModelOfTheRequest() {
        stream("{\"outputText\":\"Hello\"}", "{\"outputText\":\" world\",\"completionReason\":\"FINISH\"}");
        InvokeModelWithResponseStreamRequest request = InvokeModelWithResponseStreamRequest.builder().modelId(TITAN)
                .body(SdkBytes.fromUtf8String(REQUEST)).build();

        Exchange exchange = send(pojoUri("invokeTextModelStreaming"), request);

        assertNull(exchange.getException());
        assertEquals("Hello world", exchange.getMessage().getBody());
    }

    @Test
    public void streamingResolvesAnInferenceProfileToTheModel() {
        stream("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");

        Exchange exchange = send(invokeUri("invokeTextModelStreaming", "us." + CLAUDE), REQUEST);

        assertNull(exchange.getException());
        assertEquals("Hi", exchange.getMessage().getBody());
    }

    @Test
    public void streamingSendsTheBodyAsTheBytes() {
        ArgumentCaptor<InvokeModelWithResponseStreamRequest> request
                = ArgumentCaptor.forClass(InvokeModelWithResponseStreamRequest.class);
        stream("{\"outputText\":\"Hi\"}");

        Exchange exchange = send(invokeUri("invokeTextModelStreaming", TITAN), REQUEST.getBytes(StandardCharsets.UTF_8));

        assertNull(exchange.getException());
        verify(asyncClient).invokeModelWithResponseStream(request.capture(),
                any(InvokeModelWithResponseStreamResponseHandler.class));
        assertEquals(REQUEST, request.getValue().body().asUtf8String());
    }

    @Test
    public void imageModelStreamingFailsInsteadOfGivingAnEmptyText() {
        Exchange exchange = send(invokeUri("invokeImageModelStreaming", BedrockModels.TITAN_IMAGE_GENERATOR_V1.model),
                REQUEST);

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().contains("invokeImageModel operation"),
                exchange.getException().getMessage());
    }

    @Test
    public void embeddingsModelStreamingFailsInsteadOfGivingAnEmptyText() {
        Exchange exchange = send(invokeUri("invokeEmbeddingsModelStreaming", BedrockModels.TITAN_EMBEDDINGS_G1.model),
                REQUEST);

        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(exchange.getException().getMessage().contains("invokeEmbeddingsModel operation"),
                exchange.getException().getMessage());
    }

    @Test
    public void deprecatedStreamingOperationsStillStreamATextModel() throws Exception {
        // CAMEL-26500: deprecated, but they keep working as invokeTextModelStreaming until they are removed
        stream("{\"outputText\":\"Hello\"}", "{\"outputText\":\" world\",\"completionReason\":\"FINISH\"}");
        for (String operation : List.of("invokeImageModelStreaming", "invokeEmbeddingsModelStreaming")) {
            assertNotNull(BedrockOperations.class.getField(operation).getAnnotation(Deprecated.class), operation);

            Exchange exchange = send(invokeUri(operation, TITAN), REQUEST);

            assertNull(exchange.getException(), operation);
            assertEquals("Hello world", exchange.getMessage().getBody(), operation);
        }
    }

    @Test
    public void novaStreamingGivesTheText() {
        stream("{\"messageStart\":{\"role\":\"assistant\"}}",
                "{\"contentBlockDelta\":{\"delta\":{\"text\":\"Hello\"},\"contentBlockIndex\":0}}",
                "{\"contentBlockDelta\":{\"delta\":{\"text\":\" world\"},\"contentBlockIndex\":0}}",
                "{\"messageStop\":{\"stopReason\":\"end_turn\"}}",
                "{\"metadata\":{\"usage\":{\"inputTokens\":5,\"outputTokens\":2}}}");

        Exchange exchange = send(invokeUri("invokeTextModelStreaming", BedrockModels.NOVA_LITE_V1.model), REQUEST);

        assertNull(exchange.getException());
        assertEquals("Hello world", exchange.getMessage().getBody());
        assertEquals("end_turn", exchange.getMessage().getHeader(BedrockConstants.STREAMING_COMPLETION_REASON));
        assertEquals(2, exchange.getMessage().getHeader(BedrockConstants.STREAMING_TOKEN_COUNT));
    }

    @Test
    public void jambaStreamingGivesTheText() {
        stream("{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}],"
                                                                                                                                + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2,\"total_tokens\":7}}");

        Exchange exchange = send(invokeUri("invokeTextModelStreaming", BedrockModels.JAMBA_1_5_MINI.model), REQUEST);

        assertNull(exchange.getException());
        assertEquals("Hello world", exchange.getMessage().getBody());
        assertEquals("stop", exchange.getMessage().getHeader(BedrockConstants.STREAMING_COMPLETION_REASON));
        assertEquals(2, exchange.getMessage().getHeader(BedrockConstants.STREAMING_TOKEN_COUNT));
    }

    @Test
    public void converseStreamTakesTheGuardrailConfigurationOfTheDocs() {
        ArgumentCaptor<ConverseStreamRequest> request = ArgumentCaptor.forClass(ConverseStreamRequest.class);
        when(asyncClient.converseStream(request.capture(), any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Exchange exchange = template.send("aws-bedrock://label?bedrockRuntimeAsyncClient=#bedrockAsyncClient"
                                          + "&operation=converseStream&modelId=" + CLAUDE
                                          + "&region=us-east-1&accessKey=unused&secretKey=unused",
                ex -> {
                    ex.getMessage().setHeader(BedrockConstants.CONVERSE_MESSAGES, List.of(userMessage()));
                    ex.getMessage().setHeader(BedrockConstants.GUARDRAIL_CONFIG, GuardrailConfiguration.builder()
                            .guardrailIdentifier("my-guardrail").guardrailVersion("1").trace(GuardrailTrace.ENABLED)
                            .build());
                });

        assertNull(exchange.getException());
        assertNotNull(request.getValue().guardrailConfig());
        assertEquals("my-guardrail", request.getValue().guardrailConfig().guardrailIdentifier());
        assertEquals("1", request.getValue().guardrailConfig().guardrailVersion());
        assertEquals(GuardrailTrace.ENABLED, request.getValue().guardrailConfig().trace());
    }

    @Test
    public void converseStreamEnablesTheGuardrailTraceOfTheEndpoint() {
        ArgumentCaptor<ConverseStreamRequest> request = ArgumentCaptor.forClass(ConverseStreamRequest.class);
        when(asyncClient.converseStream(request.capture(), any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Exchange exchange = template.send("aws-bedrock://label?bedrockRuntimeAsyncClient=#bedrockAsyncClient"
                                          + "&operation=converseStream&modelId=" + CLAUDE
                                          + "&guardrailIdentifier=my-guardrail&guardrailTrace=true"
                                          + "&region=us-east-1&accessKey=unused&secretKey=unused",
                ex -> ex.getMessage().setHeader(BedrockConstants.CONVERSE_MESSAGES, List.of(userMessage())));

        assertNull(exchange.getException());
        assertEquals(GuardrailTrace.ENABLED, request.getValue().guardrailConfig().trace());
    }

    private static Message userMessage() {
        return Message.builder().role(ConversationRole.USER).content(ContentBlock.fromText("Hello")).build();
    }

    private String sentRequest(Object body) {
        ArgumentCaptor<InvokeModelRequest> request = ArgumentCaptor.forClass(InvokeModelRequest.class);
        Exchange exchange = send(invokeUri("invokeTextModel", TITAN), body);

        assertNull(exchange.getException());
        verify(client).invokeModel(request.capture());
        return request.getValue().body().asUtf8String();
    }

    private Exchange send(String uri, Object body) {
        return template.send(uri, ex -> {
            ex.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            ex.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
            ex.getMessage().setBody(body);
        });
    }

    /**
     * The async client streams the given chunks to the response handler of the producer.
     */
    private void stream(String... chunks) {
        when(asyncClient.invokeModelWithResponseStream(any(InvokeModelWithResponseStreamRequest.class),
                any(InvokeModelWithResponseStreamResponseHandler.class))).thenAnswer(invocation -> {
                    InvokeModelWithResponseStreamResponseHandler handler = invocation.getArgument(1);
                    List<ResponseStream> events = Arrays.stream(chunks)
                            .map(c -> (ResponseStream) ResponseStream.chunkBuilder().bytes(SdkBytes.fromUtf8String(c)).build())
                            .toList();
                    handler.responseReceived(InvokeModelWithResponseStreamResponse.builder().build());
                    handler.onEventStream(SdkPublisher.adapt(publisherOf(events)));
                    handler.complete();
                    return CompletableFuture.completedFuture(null);
                });
    }

    private static <T> Publisher<T> publisherOf(List<T> events) {
        return subscriber -> subscriber.onSubscribe(new Subscription() {
            private int next;
            private boolean completed;

            @Override
            public void request(long n) {
                for (long i = 0; i < n && next < events.size(); i++) {
                    subscriber.onNext(events.get(next++));
                }
                if (next == events.size() && !completed) {
                    completed = true;
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                completed = true;
            }
        });
    }

    private static String invokeUri(String operation, String modelId) {
        return "aws-bedrock://label?bedrockRuntimeClient=#bedrockClient&bedrockRuntimeAsyncClient=#bedrockAsyncClient"
               + "&operation=" + operation + "&modelId=" + modelId
               + "&region=us-east-1&accessKey=unused&secretKey=unused";
    }

    private static String pojoUri(String operation) {
        return "aws-bedrock://label?bedrockRuntimeClient=#bedrockClient&bedrockRuntimeAsyncClient=#bedrockAsyncClient"
               + "&operation=" + operation + "&pojoRequest=true"
               + "&region=us-east-1&accessKey=unused&secretKey=unused";
    }
}
