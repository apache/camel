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
package org.apache.camel.component.openai.integration;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.openai.OpenAIComponent;
import org.apache.camel.component.openai.OpenAIConstants;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Requires too much network resources")
public class OpenAIEmbeddingsIT extends CamelTestSupport {

    protected String apiKey;
    protected String baseUrl;
    protected String embeddingModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        baseUrl = OLLAMA.baseUrlV1();
        embeddingModel = OLLAMA.embeddingModelName();
        apiKey = OLLAMA.apiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "dummy";
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        if (ObjectHelper.isNotEmpty(apiKey)) {
            component.setApiKey(apiKey);
        }

        if (ObjectHelper.isNotEmpty(embeddingModel)) {
            component.setEmbeddingModel(embeddingModel);
        }

        if (ObjectHelper.isNotEmpty(baseUrl)) {
            component.setBaseUrl(baseUrl);
        }

        camelContext.addComponent("openai", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:embedding")
                        .to("openai:embeddings")
                        .to("mock:response");

                from("direct:embeddingWithEncodingFormatFloat")
                        .to("openai:embeddings?encodingFormat=float")
                        .to("mock:responseEncodingFormatFloat");
            }
        };
    }

    @Test
    public void testSingleTextEmbedding() throws Exception {
        MockEndpoint mockResponse = getMockEndpoint("mock:response");
        mockResponse.expectedMessageCount(1);

        Exchange result = template.request("direct:embedding",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));

        mockResponse.assertIsSatisfied();

        assertThat(result).isNotNull();
        Object body = result.getMessage().getBody();
        assertThat(body).isNotNull();
        assertThat(body).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Float> embedding = (List<Float>) body;
        assertThat(embedding).isNotEmpty();

        // Verify headers
        assertThat(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_COUNT)).isEqualTo(1);
        assertThat(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_VECTOR_SIZE, Integer.class))
                .isGreaterThan(0);
        assertThat(result.getMessage().getHeader(OpenAIConstants.ORIGINAL_TEXT))
                .isEqualTo("Apache Camel is an integration framework");
    }

    @Test
    public void testSingleTextEmbeddingWithFloatEncodingFormat() throws Exception {
        MockEndpoint mockResponse = getMockEndpoint("mock:responseEncodingFormatFloat");
        mockResponse.expectedMessageCount(1);

        Exchange result = template.request("direct:embeddingWithEncodingFormatFloat",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));

        mockResponse.assertIsSatisfied();

        assertThat(result).isNotNull();
        Object body = result.getMessage().getBody();
        assertThat(body).isNotNull();
        assertThat(body).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<Float> embedding = (List<Float>) body;
        assertThat(embedding).isNotEmpty();

        // Verify headers
        assertThat(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_COUNT)).isEqualTo(1);
        assertThat(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_VECTOR_SIZE, Integer.class))
                .isGreaterThan(0);
        assertThat(result.getMessage().getHeader(OpenAIConstants.ORIGINAL_TEXT))
                .isEqualTo("Apache Camel is an integration framework");
    }

    @Test
    public void testBatchTextEmbeddings() throws Exception {
        MockEndpoint mockResponse = getMockEndpoint("mock:response");
        mockResponse.expectedMessageCount(1);

        List<String> inputs = List.of("Hello world", "Apache Camel");

        Exchange result = template.request("direct:embedding",
                e -> e.getIn().setBody(inputs));

        mockResponse.assertIsSatisfied();

        assertThat(result).isNotNull();
        Object body = result.getMessage().getBody();
        assertThat(body).isNotNull();
        assertThat(body).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<List<Float>> embeddings = (List<List<Float>>) body;
        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).isNotEmpty();
        assertThat(embeddings.get(1)).isNotEmpty();

        // Verify headers
        assertThat(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_COUNT)).isEqualTo(2);
        assertThat(result.getMessage().getHeader(OpenAIConstants.EMBEDDING_VECTOR_SIZE, Integer.class))
                .isGreaterThan(0);
    }

    @Test
    public void testSimilarityBetweenRelatedTexts() throws Exception {
        // Get embedding for first text
        Exchange result1 = template.request("direct:embedding",
                e -> e.getIn().setBody("Apache Camel is an integration framework"));

        @SuppressWarnings("unchecked")
        List<Float> embedding1 = (List<Float>) result1.getMessage().getBody();
        assertThat(embedding1).isNotEmpty();

        // Get embedding for similar text and calculate similarity
        Exchange result2 = template.request("direct:embedding", e -> {
            e.getIn().setBody("Camel is used for enterprise integration patterns");
            e.getIn().setHeader(OpenAIConstants.REFERENCE_EMBEDDING, embedding1);
        });

        @SuppressWarnings("unchecked")
        List<Float> embedding2 = (List<Float>) result2.getMessage().getBody();
        assertThat(embedding2).isNotEmpty();

        Double similarityScore = result2.getMessage().getHeader(OpenAIConstants.SIMILARITY_SCORE, Double.class);
        assertThat(similarityScore).isNotNull();
        // Related texts should have positive similarity
        assertThat(similarityScore).isGreaterThan(0.0);
    }
}
