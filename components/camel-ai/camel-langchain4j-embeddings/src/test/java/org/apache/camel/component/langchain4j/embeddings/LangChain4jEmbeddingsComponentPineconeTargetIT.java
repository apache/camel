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
package org.apache.camel.component.langchain4j.embeddings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pinecone.PineconeVectorDb;
import org.apache.camel.component.pinecone.PineconeVectorDbAction;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

// Must be manually tested. Provide your own accessKey and secretKey using -Dpinecone.token
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "pinecone.token", matches = ".*", disabledReason = "Pinecone token not provided"),
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LangChain4jEmbeddingsComponentPineconeTargetIT extends CamelTestSupport {
    public static final long POINT_ID = 8;
    public static final String PINECONE_URI = "pinecone:embeddings?token={{pinecone.token}}";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.getRegistry().bind("embedding-model", new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @Order(1)
    public void createServerlessIndex() {

        Exchange result = fluentTemplate.to(PINECONE_URI)
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.CREATE_SERVERLESS_INDEX)
                .withBody(
                        "hello")
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "embeddings")
                .withHeader(PineconeVectorDb.Headers.COLLECTION_SIMILARITY_METRIC, "cosine")
                .withHeader(PineconeVectorDb.Headers.COLLECTION_DIMENSION, 384)
                .withHeader(PineconeVectorDb.Headers.COLLECTION_CLOUD, "aws")
                .withHeader(PineconeVectorDb.Headers.COLLECTION_CLOUD_REGION, "us-east-1")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    public void upsert() {

        Exchange result = fluentTemplate.to("direct:in")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.UPSERT)
                .withBody("hi")
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "embeddings")
                .withHeader(PineconeVectorDb.Headers.INDEX_ID, "elements")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(3)
    public void queryByVector() {

        List<Float> elements = generateFloatVector();

        Exchange result = fluentTemplate.to(PINECONE_URI)
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.QUERY)
                .withBody(
                        elements)
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "embeddings")
                .withHeader(PineconeVectorDb.Headers.QUERY_TOP_K, 384)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(((QueryResponseWithUnsignedIndices) result.getMessage().getBody()).getMatchesList()).isNotNull();
    }

    @Test
    @Order(4)
    public void deleteIndex() {

        Exchange result = fluentTemplate.to(PINECONE_URI)
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.DELETE_INDEX)
                .withBody(
                        "test")
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "embeddings")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("langchain4j-embeddings:test")
                        .setHeader(PineconeVectorDb.Headers.ACTION).constant(PineconeVectorDbAction.UPSERT)
                        .setHeader(PineconeVectorDb.Headers.INDEX_ID).constant(POINT_ID)
                        .transform(
                                new DataType("pinecone:embeddings"))
                        .to(PINECONE_URI);
            }
        };
    }

    private List<Float> generateFloatVector() {
        Random ran = new Random();
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < 384; ++i) {
            vector.add(ran.nextFloat());
        }
        return vector;
    }
}
