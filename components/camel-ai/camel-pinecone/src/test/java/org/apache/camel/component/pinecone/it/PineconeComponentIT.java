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
package org.apache.camel.component.pinecone.it;

import java.util.Arrays;
import java.util.List;

import io.pinecone.proto.FetchResponse;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import org.apache.camel.Exchange;
import org.apache.camel.component.pinecone.PineconeVectorDb;
import org.apache.camel.component.pinecone.PineconeVectorDbAction;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

// Must be manually tested. Provide your own accessKey and secretKey using -Dpinecone.token
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "pinecone.token", matches = ".*", disabledReason = "Pinecone token not provided"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PineconeComponentIT extends CamelTestSupport {

    @Test
    @Order(1)
    public void createServerlessIndex() {

        Exchange result = fluentTemplate.to("pinecone:test-collection?token={{pinecone.token}}")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.CREATE_SERVERLESS_INDEX)
                .withBody(
                        "hello")
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "test-serverless-index")
                .withHeader(PineconeVectorDb.Headers.COLLECTION_SIMILARITY_METRIC, "cosine")
                .withHeader(PineconeVectorDb.Headers.COLLECTION_DIMENSION, 3)
                .withHeader(PineconeVectorDb.Headers.COLLECTION_CLOUD, "aws")
                .withHeader(PineconeVectorDb.Headers.COLLECTION_CLOUD_REGION, "us-east-1")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    public void upsert() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.0f);

        Exchange result = fluentTemplate.to("pinecone:test-collection?token={{pinecone.token}}")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.UPSERT)
                .withBody(
                        elements)
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "test-serverless-index")
                .withHeader(PineconeVectorDb.Headers.INDEX_ID, "elements")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(3)
    public void update() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.2f);

        Exchange result = fluentTemplate.to("pinecone:test-collection?token={{pinecone.token}}")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.UPDATE)
                .withBody(
                        elements)
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "test-serverless-index")
                .withHeader(PineconeVectorDb.Headers.INDEX_ID, "elements")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(4)
    public void queryByVector() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.2f);

        Exchange result = fluentTemplate.to("pinecone:test-collection?token={{pinecone.token}}")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.QUERY)
                .withBody(
                        elements)
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "test-serverless-index")
                .withHeader(PineconeVectorDb.Headers.QUERY_TOP_K, 3)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(!result.getMessage().getBody(QueryResponseWithUnsignedIndices.class).getMatchesList().isEmpty());
    }

    @Test
    @Order(4)
    public void queryByVectorExtended() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.2f);

        Exchange result = fluentTemplate.to("pinecone:test-collection?token={{pinecone.token}}")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.QUERY)
                .withBody(
                        elements)
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "test-serverless-index")
                .withHeader(PineconeVectorDb.Headers.QUERY_TOP_K, 3)
                .withHeader(PineconeVectorDb.Headers.NAMESPACE, "defaultNamespace")
                .withHeader(PineconeVectorDb.Headers.QUERY_FILTER, "subject\": {\"$eq\": \"marine\"}")
                .withHeader(PineconeVectorDb.Headers.QUERY_INCLUDE_VALUES, true)
                .withHeader(PineconeVectorDb.Headers.QUERY_INCLUDE_METADATA, true)

                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(!result.getMessage().getBody(QueryResponseWithUnsignedIndices.class).getMatchesList().isEmpty());
    }

    @Test
    @Order(5)
    public void queryById() {

        Exchange result = fluentTemplate.to("pinecone:test-collection?token={{pinecone.token}}")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.QUERY_BY_ID)
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "test-serverless-index")
                .withHeader(PineconeVectorDb.Headers.INDEX_ID, "elements")
                .withHeader(PineconeVectorDb.Headers.QUERY_TOP_K, 3)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(!result.getMessage().getBody(QueryResponseWithUnsignedIndices.class).getMatchesList().isEmpty());
    }

    @Test
    @Order(6)
    public void deleteIndex() {

        Exchange result = fluentTemplate.to("pinecone:test-collection?token={{pinecone.token}}")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.DELETE_INDEX)
                .withBody(
                        "test")
                .withHeader(PineconeVectorDb.Headers.INDEX_NAME, "test-serverless-index")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(7)
    public void fetch() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.2f);

        Exchange result = fluentTemplate.to("pinecone:test-collection?token={{pinecone.token}}")
                .withHeader(PineconeVectorDb.Headers.ACTION, PineconeVectorDbAction.FETCH)
                .withBody(
                        elements)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(FetchResponse.class).getVectorsCount() != 0);
    }

}
