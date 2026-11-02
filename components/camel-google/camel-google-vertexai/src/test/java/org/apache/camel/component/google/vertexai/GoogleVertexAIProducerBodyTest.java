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
package org.apache.camel.component.google.vertexai;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.HttpBody;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.ServerStreamingCallable;
import com.google.api.gax.rpc.StreamController;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.RawPredictRequest;
import com.google.cloud.aiplatform.v1.StreamRawPredictRequest;
import com.google.cloud.aiplatform.v1.stub.PredictionServiceStub;
import com.google.genai.Client;
import com.google.genai.types.HarmBlockThreshold;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.SafetyRating;
import com.google.genai.types.SafetySetting;
import com.google.protobuf.ByteString;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sends the bodies a route has (text, bytes, a stream, a file, a Map, a POJO, no body) through the producer:
 * generateMultimodal and the generate operations against a fake of the Gemini API (a JDK HttpServer), rawPredict and
 * streamRawPredict against a fake prediction service.
 */
class GoogleVertexAIProducerBodyTest extends CamelTestSupport {

    private static final String GEMINI = "google-vertexai:my-project:us-central1:gemini-2.0-flash";
    private static final String MULTIMODAL = GEMINI + "?operation=generateMultimodal";
    private static final String META = "google-vertexai:my-project:us-central1:llama-3.3-70b-instruct-maas"
                                       + "?operation=rawPredict&publisher=meta";
    private static final String CLAUDE = "google-vertexai:my-project:us-east5:claude-sonnet-4@20250514"
                                         + "?operation=rawPredict&publisher=anthropic";
    private static final String CLAUDE_STREAM = "google-vertexai:my-project:us-east5:claude-sonnet-4@20250514"
                                                + "?operation=streamRawPredict&publisher=anthropic";

    private static final String ANSWER = """
            {"candidates": [{"content": {"role": "model", "parts": [{"text": "A cat"}]}, "finishReason": "STOP",
              "safetyRatings": [{"category": "HARM_CATEGORY_HARASSMENT", "probability": "NEGLIGIBLE"}]}],
             "usageMetadata": {"promptTokenCount": 5, "candidatesTokenCount": 2, "totalTokenCount": 7}}
            """;
    private static final String BLOCKED_PROMPT = """
            {"promptFeedback": {"blockReason": "SAFETY",
              "safetyRatings": [{"category": "HARM_CATEGORY_HARASSMENT", "probability": "HIGH", "blocked": true}]},
             "usageMetadata": {"promptTokenCount": 5, "totalTokenCount": 5}}
            """;
    private static final String JSON_REQUEST = "{\"instances\":[{\"prompt\":\"What is Camel?\"}]}";
    private static final String CLAUDE_REQUEST
            = "{\"max_tokens\":64,\"messages\":[{\"role\":\"user\",\"content\":\"What is Camel?\"}]}";

    // the bytes of an image are not text: 0x89, 0xff and 0xfe do not survive a conversion to a String and back
    private static final byte[] IMAGE = { (byte) 0x89, 'P', 'N', 'G', (byte) 0xff, 0x00, (byte) 0xfe, 0x01 };

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<JsonNode> GEMINI_REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile String geminiAnswer = ANSWER;
    private static HttpServer gemini;
    private static Client geminiClient;

    private FakePredictionService predictionService;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startGemini() throws IOException {
        gemini = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        gemini.createContext("/", http -> {
            GEMINI_REQUESTS.add(MAPPER.readTree(http.getRequestBody()));
            byte[] answer = geminiAnswer.getBytes(StandardCharsets.UTF_8);
            http.getResponseHeaders().add("Content-Type", "application/json");
            http.sendResponseHeaders(200, answer.length);
            try (OutputStream out = http.getResponseBody()) {
                out.write(answer);
            }
        });
        gemini.start();
        geminiClient = Client.builder()
                .vertexAI(false)
                .apiKey("test-key")
                .httpOptions(HttpOptions.builder().baseUrl("http://localhost:" + gemini.getAddress().getPort()).build())
                .build();
    }

    @AfterAll
    static void stopGemini() {
        geminiClient.close();
        gemini.stop(0);
    }

    @BeforeEach
    void useTheFakes() {
        GEMINI_REQUESTS.clear();
        geminiAnswer = ANSWER;
        predictionService = new FakePredictionService();
        GoogleVertexAIConfiguration configuration
                = context.getComponent("google-vertexai", GoogleVertexAIComponent.class).getConfiguration();
        configuration.setClient(geminiClient);
        configuration.setPredictionServiceClient(PredictionServiceClient.create(predictionService));
    }

    // ==================== generateMultimodal ====================

    @Test
    void aByteArrayBodyIsSentAsTheMediaWithTheMimeTypeOfTheHeader() {
        Exchange exchange = template.request(MULTIMODAL, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.PROMPT, "Describe this image");
            e.getIn().setHeader(GoogleVertexAIConstants.MEDIA_MIME_TYPE, "image/png");
            e.getIn().setBody(IMAGE);
        });

        assertNull(exchange.getException());
        assertEquals("A cat", exchange.getMessage().getBody());
        JsonNode parts = lastGeminiParts();
        assertEquals("Describe this image", parts.get(0).get("text").asText());
        assertArrayEquals(IMAGE, mediaBytes(parts.get(1)));
        assertEquals("image/png", parts.get(1).at("/inlineData/mimeType").asText());
    }

    @Test
    void aByteArrayBodyWithoutAMimeTypeFails() {
        Exchange exchange = template.request(MULTIMODAL, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.PROMPT, "Describe this image");
            e.getIn().setBody(IMAGE);
        });

        assertFailsWith(exchange, "[B", GoogleVertexAIConstants.MEDIA_MIME_TYPE);
        assertTrue(GEMINI_REQUESTS.isEmpty());
    }

    @Test
    void aStreamBodyIsSentAsTheMedia() {
        Exchange exchange = template.request(MULTIMODAL, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.PROMPT, "Describe this image");
            e.getIn().setHeader(GoogleVertexAIConstants.MEDIA_MIME_TYPE, "image/png");
            e.getIn().setBody(new ByteArrayInputStream(IMAGE));
        });

        assertNull(exchange.getException());
        JsonNode parts = lastGeminiParts();
        assertEquals(2, parts.size());
        assertArrayEquals(IMAGE, mediaBytes(parts.get(1)));
        assertEquals("image/png", parts.get(1).at("/inlineData/mimeType").asText());
    }

    @Test
    void aFileBodyIsSentWithTheMimeTypeOfItsName() throws IOException {
        File photo = Files.write(tempDir.resolve("photo.png"), IMAGE).toFile();

        Exchange exchange = template.request(MULTIMODAL, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.PROMPT, "Describe this image");
            e.getIn().setBody(photo);
        });

        assertNull(exchange.getException());
        JsonNode parts = lastGeminiParts();
        assertEquals(2, parts.size());
        assertArrayEquals(IMAGE, mediaBytes(parts.get(1)));
        assertEquals("image/png", parts.get(1).at("/inlineData/mimeType").asText());
    }

    @Test
    void theFileOfAFileConsumerIsSentWithTheMimeTypeOfItsName() throws Exception {
        Files.write(tempDir.resolve("photo.jpg"), IMAGE);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + tempDir + "?noop=true&initialDelay=0&delay=10")
                        .setHeader(GoogleVertexAIConstants.PROMPT, constant("Describe this image"))
                        .to(MULTIMODAL)
                        .to("mock:result");
            }
        });
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("A cat");

        result.assertIsSatisfied();

        JsonNode parts = lastGeminiParts();
        assertEquals(2, parts.size());
        assertArrayEquals(IMAGE, mediaBytes(parts.get(1)));
        assertEquals("image/jpeg", parts.get(1).at("/inlineData/mimeType").asText());
    }

    @Test
    void aFileWithoutAKnownExtensionNeedsTheMimeTypeHeader() throws IOException {
        File photo = Files.write(tempDir.resolve("photo.nosuchextension"), IMAGE).toFile();

        Exchange exchange = template.request(MULTIMODAL, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.PROMPT, "Describe this image");
            e.getIn().setBody(photo);
        });

        assertFailsWith(exchange, GoogleVertexAIConstants.MEDIA_MIME_TYPE);
        assertTrue(GEMINI_REQUESTS.isEmpty());
    }

    @Test
    void theMediaDataHeaderWithoutAMimeTypeFails() {
        Exchange exchange = template.request(MULTIMODAL, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.PROMPT, "Describe this image");
            e.getIn().setHeader(GoogleVertexAIConstants.MEDIA_DATA, IMAGE);
        });

        assertFailsWith(exchange, GoogleVertexAIConstants.MEDIA_DATA, GoogleVertexAIConstants.MEDIA_MIME_TYPE);
        assertTrue(GEMINI_REQUESTS.isEmpty());
    }

    @Test
    void aTextBodyIsThePromptWhenThePromptHeaderIsNotSet() {
        Exchange exchange = template.request(MULTIMODAL, e -> e.getIn().setBody("What is Apache Camel?"));

        assertNull(exchange.getException());
        JsonNode parts = lastGeminiParts();
        assertEquals(1, parts.size());
        assertEquals("What is Apache Camel?", parts.get(0).get("text").asText());
    }

    @Test
    void aTextBodyWithThePromptHeaderIsMediaThatNeedsAMimeType() {
        Exchange exchange = template.request(MULTIMODAL, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.PROMPT, "Summarize this text");
            e.getIn().setBody("Apache Camel is an integration framework");
        });

        assertFailsWith(exchange, GoogleVertexAIConstants.MEDIA_MIME_TYPE,
                "a text body is the prompt only when the " + GoogleVertexAIConstants.PROMPT);
        assertTrue(GEMINI_REQUESTS.isEmpty());
    }

    // ==================== headers of the generate operations ====================

    @Test
    void theSystemInstructionAndTheSafetySettingsAreSent() {
        SafetySetting safetySetting = SafetySetting.builder()
                .category(HarmCategory.Known.HARM_CATEGORY_HARASSMENT)
                .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                .build();

        Exchange exchange = template.request(GEMINI, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.SYSTEM_INSTRUCTION, "You answer in one sentence");
            e.getIn().setHeader(GoogleVertexAIConstants.SAFETY_SETTINGS, List.of(safetySetting));
            e.getIn().setBody("What is Apache Camel?");
        });

        assertNull(exchange.getException());
        JsonNode request = GEMINI_REQUESTS.get(GEMINI_REQUESTS.size() - 1);
        assertEquals("You answer in one sentence", request.at("/systemInstruction/parts/0/text").asText());
        assertEquals("HARM_CATEGORY_HARASSMENT", request.at("/safetySettings/0/category").asText());
        assertEquals("BLOCK_LOW_AND_ABOVE", request.at("/safetySettings/0/threshold").asText());
    }

    @Test
    void theSafetyRatingsOfTheAnswerAreInAHeader() {
        Exchange exchange = template.request(GEMINI, e -> e.getIn().setBody("What is Apache Camel?"));

        assertNull(exchange.getException());
        assertEquals("A cat", exchange.getMessage().getBody());
        assertEquals(Boolean.FALSE, exchange.getMessage().getHeader(GoogleVertexAIConstants.CONTENT_BLOCKED));
        List<?> ratings = exchange.getMessage().getHeader(GoogleVertexAIConstants.SAFETY_RATINGS, List.class);
        assertEquals(1, ratings.size());
        SafetyRating rating = (SafetyRating) ratings.get(0);
        assertEquals(Optional.of("HARM_CATEGORY_HARASSMENT"), rating.category().map(Object::toString));
        assertEquals(Optional.of("NEGLIGIBLE"), rating.probability().map(Object::toString));
    }

    @Test
    void aBlockedPromptSetsTheContentBlockedHeader() {
        geminiAnswer = BLOCKED_PROMPT;

        Exchange exchange = template.request(GEMINI, e -> e.getIn().setBody("Something the filters block"));

        assertNull(exchange.getException());
        assertNull(exchange.getMessage().getBody());
        assertEquals(Boolean.TRUE, exchange.getMessage().getHeader(GoogleVertexAIConstants.CONTENT_BLOCKED));
        List<?> ratings = exchange.getMessage().getHeader(GoogleVertexAIConstants.SAFETY_RATINGS, List.class);
        assertEquals(1, ratings.size());
        assertEquals(Optional.of("HIGH"), ((SafetyRating) ratings.get(0)).probability().map(Object::toString));
    }

    @Test
    void anAnswerThatASafetyFilterStopsFailsTheExchange() {
        geminiAnswer = """
                {"candidates": [{"finishReason": "SAFETY",
                  "safetyRatings": [{"category": "HARM_CATEGORY_HARASSMENT", "probability": "HIGH", "blocked": true}]}]}
                """;

        Exchange exchange = template.request(GEMINI, e -> e.getIn().setBody("Something the filters stop"));

        // the Google GenAI SDK fails to give the text of such an answer, so the content blocked header is not set
        assertFailsWith(exchange, "SAFETY");
    }

    // ==================== rawPredict and streamRawPredict ====================

    @Test
    void aJsonRequestInBytesIsSent() {
        predictionService.answer = "{\"predictions\":[\"Camel is an integration framework\"]}";

        Exchange exchange = template.request(META, e -> e.getIn().setBody(JSON_REQUEST.getBytes(StandardCharsets.UTF_8)));

        assertNull(exchange.getException());
        assertEquals(JSON_REQUEST, predictionService.lastRequest());
        assertEquals(predictionService.answer, exchange.getMessage().getBody());
    }

    @Test
    void aJsonRequestInAStreamIsSent() {
        Exchange exchange = template.request(META,
                e -> e.getIn().setBody(new ByteArrayInputStream(JSON_REQUEST.getBytes(StandardCharsets.UTF_8))));

        assertNull(exchange.getException());
        assertEquals(JSON_REQUEST, predictionService.lastRequest());
    }

    @Test
    void aJsonRequestInAFileIsSent() throws IOException {
        File request = Files.writeString(tempDir.resolve("request.json"), JSON_REQUEST).toFile();

        Exchange exchange = template.request(META, e -> e.getIn().setBody(request));

        assertNull(exchange.getException());
        assertEquals(JSON_REQUEST, predictionService.lastRequest());
    }

    @Test
    void aJsonRequestInBytesGetsTheAnthropicVersion() throws IOException {
        predictionService.answer = "{\"content\":[{\"type\":\"text\",\"text\":\"An integration framework\"}]}";

        Exchange exchange
                = template.request(CLAUDE, e -> e.getIn().setBody(CLAUDE_REQUEST.getBytes(StandardCharsets.UTF_8)));

        assertNull(exchange.getException());
        JsonNode request = MAPPER.readTree(predictionService.lastRequest());
        assertEquals("vertex-2023-10-16", request.get("anthropic_version").asText());
        assertEquals("What is Camel?", request.at("/messages/0/content").asText());
        assertEquals("An integration framework", exchange.getMessage().getBody());
    }

    @Test
    void theAnthropicVersionHeaderWinsOverTheOption() throws IOException {
        Exchange exchange = template.request(CLAUDE, e -> {
            e.getIn().setHeader(GoogleVertexAIConstants.ANTHROPIC_VERSION, "vertex-2099-01-01");
            e.getIn().setBody("What is Camel?");
        });

        assertNull(exchange.getException());
        JsonNode request = MAPPER.readTree(predictionService.lastRequest());
        assertEquals("vertex-2099-01-01", request.get("anthropic_version").asText());
        assertEquals("What is Camel?", request.at("/messages/0/content").asText());
    }

    @Test
    void aMapRequestIsWrittenAsJson() throws IOException {
        Exchange exchange = template.request(META,
                e -> e.getIn().setBody(Map.of("instances", List.of(Map.of("prompt", "What is Camel?")))));

        assertNull(exchange.getException());
        assertEquals(MAPPER.readTree(JSON_REQUEST), MAPPER.readTree(predictionService.lastRequest()));
    }

    @Test
    void aPojoOrNoBodyFails() {
        Exchange pojo = template.request(META, e -> e.getIn().setBody(new Question("What is Camel?")));
        Exchange noBody = template.request(META, e -> e.getIn().setBody(null));

        assertFailsWith(pojo, "Got: " + Question.class.getName());
        assertFailsWith(noBody, "Got: no body");
        assertTrue(predictionService.requests.isEmpty());
    }

    @Test
    void streamRawPredictSendsAJsonRequestInBytes() throws IOException {
        predictionService.streamAnswer = List.of(
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"An integration\"}}\n\n",
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" framework\"}}\n\n");

        Exchange exchange
                = template.request(CLAUDE_STREAM, e -> e.getIn().setBody(CLAUDE_REQUEST.getBytes(StandardCharsets.UTF_8)));

        assertNull(exchange.getException());
        JsonNode request = MAPPER.readTree(predictionService.lastRequest());
        assertTrue(request.get("stream").asBoolean());
        assertEquals("vertex-2023-10-16", request.get("anthropic_version").asText());
        assertEquals("An integration framework", exchange.getMessage().getBody());
        assertEquals(2, exchange.getMessage().getHeader(GoogleVertexAIConstants.CHUNK_COUNT, Integer.class));
    }

    /**
     * Checks that the exchange failed with an IllegalArgumentException whose message has all the given texts.
     */
    private static void assertFailsWith(Exchange exchange, String... texts) {
        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        for (String text : texts) {
            assertTrue(e.getMessage().contains(text), () -> "The message should contain " + text + ": " + e.getMessage());
        }
    }

    private static JsonNode lastGeminiParts() {
        assertFalse(GEMINI_REQUESTS.isEmpty(), "No request reached the fake of the Gemini API");
        return GEMINI_REQUESTS.get(GEMINI_REQUESTS.size() - 1).at("/contents/0/parts");
    }

    private static byte[] mediaBytes(JsonNode part) {
        return Base64.getDecoder().decode(part.at("/inlineData/data").asText());
    }

    /**
     * A POJO: the type converter could only turn it into its toString(), which is not a request.
     */
    private record Question(String text) {
    }

    /**
     * A prediction service that answers rawPredict and streamRawPredict with a canned response, and keeps the requests.
     */
    private static final class FakePredictionService extends PredictionServiceStub {

        private final List<String> requests = new CopyOnWriteArrayList<>();
        private volatile String answer = "{}";
        private volatile List<String> streamAnswer = List.of();

        String lastRequest() {
            assertFalse(requests.isEmpty(), "No request reached the fake prediction service");
            return requests.get(requests.size() - 1);
        }

        @Override
        public UnaryCallable<RawPredictRequest, HttpBody> rawPredictCallable() {
            return new UnaryCallable<>() {
                @Override
                public ApiFuture<HttpBody> futureCall(RawPredictRequest request, ApiCallContext context) {
                    requests.add(request.getHttpBody().getData().toStringUtf8());
                    return ApiFutures.immediateFuture(httpBody(answer));
                }
            };
        }

        @Override
        public ServerStreamingCallable<StreamRawPredictRequest, HttpBody> streamRawPredictCallable() {
            return new ServerStreamingCallable<>() {
                @Override
                public void call(
                        StreamRawPredictRequest request, ResponseObserver<HttpBody> observer, ApiCallContext context) {
                    requests.add(request.getHttpBody().getData().toStringUtf8());
                    Iterator<String> chunks = streamAnswer.iterator();
                    observer.onStart(new StreamController() {
                        private boolean completed;

                        @Override
                        public void cancel() {
                            // the chunks are in memory: nothing to cancel
                        }

                        @Override
                        public void disableAutoInboundFlowControl() {
                            // the chunks are only sent when they are requested anyway
                        }

                        @Override
                        public void request(int count) {
                            // the stream of the client has room for one chunk and the end only, so send what it asks
                            for (int i = 0; i < count && chunks.hasNext(); i++) {
                                observer.onResponse(httpBody(chunks.next()));
                            }
                            if (!chunks.hasNext() && !completed) {
                                completed = true;
                                observer.onComplete();
                            }
                        }
                    });
                }
            };
        }

        private static HttpBody httpBody(String data) {
            return HttpBody.newBuilder().setData(ByteString.copyFromUtf8(data)).setContentType("application/json").build();
        }

        @Override
        public void close() {
            // nothing to close
        }

        @Override
        public void shutdown() {
            // nothing to shut down
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public void shutdownNow() {
            // nothing to shut down
        }

        @Override
        public boolean awaitTermination(long duration, TimeUnit unit) {
            return true;
        }
    }
}
