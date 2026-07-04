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

import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.aws2.bedrock.BedrockModels;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests asserting that the {@code invokeTextModel} operation rejects image, embedding, rerank and video/speech
 * models with a clear, actionable {@link IllegalArgumentException} instead of the confusing
 * {@code IllegalStateException("Unexpected model: ...")} that these models — all advertised in
 * {@code BedrockConfiguration.modelId}'s {@code @UriParam(enums=...)} — previously produced (CAMEL-23463). Uses a
 * mocked {@link BedrockRuntimeClient}, so no AWS credentials or network access are required.
 */
@ExtendWith(MockitoExtension.class)
public class BedrockInvokeTextModelUnsupportedModelTest {

    @Mock
    private BedrockRuntimeClient client;

    private CamelContext camelContext;
    private ProducerTemplate template;

    @BeforeEach
    public void setup() {
        lenient().when(client.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(InvokeModelResponse.builder().body(SdkBytes.fromUtf8String("{}")).build());

        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("bedrockClient", client);
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

    static Stream<Arguments> nonTextModels() {
        return Stream.of(
                // Image generation models -> invokeImageModel
                Arguments.of(BedrockModels.TITAN_IMAGE_GENERATOR_V1.model, "invokeImageModel"),
                Arguments.of(BedrockModels.TITAN_IMAGE_GENERATOR_V2.model, "invokeImageModel"),
                Arguments.of(BedrockModels.NOVA_CANVAS_V1.model, "invokeImageModel"),
                Arguments.of(BedrockModels.STABLE_DIFFUSION_3_5_LARGE.model, "invokeImageModel"),
                Arguments.of(BedrockModels.STABLE_IMAGE_CONTROL_SKETCH.model, "invokeImageModel"),
                Arguments.of(BedrockModels.STABLE_IMAGE_CONTROL_STRUCTURE.model, "invokeImageModel"),
                Arguments.of(BedrockModels.STABLE_IMAGE_CORE.model, "invokeImageModel"),
                // Embedding models -> invokeEmbeddingsModel
                Arguments.of(BedrockModels.TITAN_EMBEDDINGS_G1.model, "invokeEmbeddingsModel"),
                Arguments.of(BedrockModels.TITAN_MULTIMODAL_EMBEDDINGS_G1.model, "invokeEmbeddingsModel"),
                Arguments.of(BedrockModels.COHERE_EMBED_ENGLISH_V3.model, "invokeEmbeddingsModel"),
                Arguments.of(BedrockModels.COHERE_EMBED_MULTILINGUAL_V3.model, "invokeEmbeddingsModel"),
                // Rerank models -> no dedicated operation, just reject clearly
                Arguments.of(BedrockModels.RERANK_V1.model, null),
                Arguments.of(BedrockModels.COHERE_RERANK_V3_5.model, null),
                // Video / speech models -> no dedicated operation, just reject clearly
                Arguments.of(BedrockModels.NOVA_REEL_V1.model, null),
                Arguments.of(BedrockModels.NOVA_REEL_V1_1.model, null),
                Arguments.of(BedrockModels.NOVA_SONIC_V1.model, null));
    }

    @ParameterizedTest
    @MethodSource("nonTextModels")
    public void invokeTextModelRejectsNonTextModelWithActionableMessage(String modelId, String expectedOperationHint) {
        Exchange exchange = template.send(endpointUri(modelId), ex -> {
            ex.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            ex.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
            ex.getMessage().setBody("{}");
        });

        Throwable cause = exchange.getException();
        assertNotNull(cause, "invokeTextModel should fail for non-text model " + modelId);
        assertInstanceOf(IllegalArgumentException.class, cause,
                "Expected a clear IllegalArgumentException for " + modelId + " but was: " + cause);
        assertTrue(cause.getMessage().contains(modelId),
                "Error message should name the offending model — was: " + cause.getMessage());
        assertTrue(cause.getMessage().contains("invokeTextModel"),
                "Error message should mention the invokeTextModel operation — was: " + cause.getMessage());
        if (expectedOperationHint != null) {
            assertTrue(cause.getMessage().contains(expectedOperationHint),
                    "Error message should point to the " + expectedOperationHint + " operation — was: " + cause.getMessage());
        }
    }

    @Test
    public void invokeTextModelStillWorksForTextModel() {
        Exchange exchange = template.send(endpointUri(BedrockModels.TITAN_TEXT_EXPRESS_V1.model), ex -> {
            ex.getMessage().setHeader(BedrockConstants.MODEL_CONTENT_TYPE, "application/json");
            ex.getMessage().setHeader(BedrockConstants.MODEL_ACCEPT_CONTENT_TYPE, "application/json");
            ex.getMessage().setBody("{}");
        });

        assertNull(exchange.getException(), "A text model must not be rejected by invokeTextModel");
        assertEquals("{}", exchange.getMessage().getBody(String.class),
                "Titan text response should be returned as-is");
    }

    private static String endpointUri(String modelId) {
        return "aws-bedrock://label"
               + "?bedrockRuntimeClient=#bedrockClient"
               + "&operation=invokeTextModel"
               + "&modelId=" + modelId
               + "&region=us-east-1"
               + "&accessKey=unused"
               + "&secretKey=unused";
    }
}
