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
package org.apache.camel.component.aws2.s3vectors;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.CreateIndexResponse;
import software.amazon.awssdk.services.s3vectors.model.CreateVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.CreateVectorBucketResponse;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.GetVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.GetVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsResponse;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.QueryVectorsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The requests the producer sends to S3 Vectors, and the headers it leaves for the next steps.
 */
class AWS2S3VectorsProducerRequestTest {

    private DefaultCamelContext context;
    private S3VectorsClient client;

    @BeforeEach
    void setup() {
        client = Mockito.mock(S3VectorsClient.class);
        when(client.putVectors(any(PutVectorsRequest.class))).thenReturn(PutVectorsResponse.builder().build());
        when(client.getVectors(any(GetVectorsRequest.class)))
                .thenReturn(GetVectorsResponse.builder().vectors(List.of()).build());
        when(client.deleteVectors(any(DeleteVectorsRequest.class))).thenReturn(DeleteVectorsResponse.builder().build());
        when(client.queryVectors(any(QueryVectorsRequest.class)))
                .thenReturn(QueryVectorsResponse.builder().vectors(List.of()).build());
        when(client.createVectorBucket(any(CreateVectorBucketRequest.class)))
                .thenReturn(CreateVectorBucketResponse.builder().build());
        when(client.createIndex(any(CreateIndexRequest.class))).thenReturn(CreateIndexResponse.builder().build());
        context = new DefaultCamelContext();
        context.getRegistry().bind("client", client);
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void putVectorsDoesNotSetBucketAndIndexHeaders() throws Exception {
        Exchange exchange = exchange(List.of(0.1f, 0.2f));
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_ID, "doc-1");

        send("bucket-a", "index-a", AWS2S3VectorsOperations.putVectors, exchange);

        assertThat(exchange.getMessage().getHeader(AWS2S3VectorsConstants.VECTOR_BUCKET_NAME)).isNull();
        assertThat(exchange.getMessage().getHeader(AWS2S3VectorsConstants.VECTOR_INDEX_NAME)).isNull();
        assertThat(exchange.getMessage().getHeader(AWS2S3VectorsConstants.VECTOR_ID)).isEqualTo("doc-1");
    }

    @Test
    void createVectorBucketDoesNotRedirectTheNextStep() throws Exception {
        Exchange exchange = exchange(null);

        send("bucket-a", "index-a", AWS2S3VectorsOperations.createVectorBucket, exchange);
        send("bucket-b", "index-b", AWS2S3VectorsOperations.createVectorBucket, exchange);

        ArgumentCaptor<CreateVectorBucketRequest> captor = ArgumentCaptor.forClass(CreateVectorBucketRequest.class);
        Mockito.verify(client, Mockito.times(2)).createVectorBucket(captor.capture());
        assertThat(captor.getAllValues()).extracting(CreateVectorBucketRequest::vectorBucketName)
                .containsExactly("bucket-a", "bucket-b");
        assertThat(exchange.getMessage().getHeader(AWS2S3VectorsConstants.VECTOR_BUCKET_NAME)).isNull();
    }

    @Test
    void createVectorIndexDoesNotSetIndexHeader() throws Exception {
        Exchange exchange = exchange(null);

        send("bucket-a", "index-a", AWS2S3VectorsOperations.createVectorIndex, exchange);

        assertThat(exchange.getMessage().getHeader(AWS2S3VectorsConstants.VECTOR_INDEX_NAME)).isNull();
    }

    @Test
    void bucketAndIndexHeadersOverrideTheEndpoint() throws Exception {
        Exchange exchange = exchange(List.of(0.1f, 0.2f));
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_BUCKET_NAME, "header-bucket");
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_INDEX_NAME, "header-index");

        send("bucket-a", "index-a", AWS2S3VectorsOperations.putVectors, exchange);

        PutVectorsRequest request = captured(PutVectorsRequest.class);
        assertThat(request.vectorBucketName()).isEqualTo("header-bucket");
        assertThat(request.indexName()).isEqualTo("header-index");
    }

    @Test
    void getVectorsTakesTheKeysFromTheBodyBeforeTheHeader() throws Exception {
        Exchange exchange = exchange("doc-list");
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_ID, "key-of-earlier-put");

        send("bucket-a", "index-a", AWS2S3VectorsOperations.getVectors, exchange);

        assertThat(captured(GetVectorsRequest.class).keys()).containsExactly("doc-list");
    }

    @Test
    void deleteVectorsTakesTheKeysFromAListBody() throws Exception {
        Exchange exchange = exchange(List.of("k1", "k2"));
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_ID, "key-of-earlier-put");

        send("bucket-a", "index-a", AWS2S3VectorsOperations.deleteVectors, exchange);

        assertThat(captured(DeleteVectorsRequest.class).keys()).containsExactly("k1", "k2");
    }

    @Test
    void getVectorsTakesTheKeysFromTheHeaderWhenTheBodyHoldsNone() throws Exception {
        Exchange exchange = exchange(PutVectorsResponse.builder().build());
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_ID, "doc-1");

        send("bucket-a", "index-a", AWS2S3VectorsOperations.getVectors, exchange);

        assertThat(captured(GetVectorsRequest.class).keys()).containsExactly("doc-1");
    }

    @Test
    void getVectorsRejectsKeysThatAreNotStrings() throws Exception {
        Exchange exchange = exchange(List.of(1, 2));

        assertThatThrownBy(() -> send("bucket-a", "index-a", AWS2S3VectorsOperations.getVectors, exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.Integer");
        Mockito.verify(client, Mockito.never()).getVectors(any(GetVectorsRequest.class));
    }

    @Test
    void deleteVectorsRejectsAHeaderOfKeysThatAreNotStrings() throws Exception {
        Exchange exchange = exchange(null);
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_ID, List.of(1, 2));

        assertThatThrownBy(() -> send("bucket-a", "index-a", AWS2S3VectorsOperations.deleteVectors, exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AWS2S3VectorsConstants.VECTOR_ID)
                .hasMessageContaining("java.lang.Integer");
        Mockito.verify(client, Mockito.never()).deleteVectors(any(DeleteVectorsRequest.class));
    }

    @Test
    void getVectorsAsksForDataAndMetadata() throws Exception {
        send("bucket-a", "index-a", AWS2S3VectorsOperations.getVectors, exchange("doc-1"));

        GetVectorsRequest request = captured(GetVectorsRequest.class);
        assertThat(request.returnData()).isTrue();
        assertThat(request.returnMetadata()).isTrue();
    }

    @Test
    void queryVectorsAsksForMetadataAndDistanceAndSendsTheFilter() throws Exception {
        Exchange exchange = exchange(List.of(0.1f, 0.2f));
        exchange.getIn().setHeader(AWS2S3VectorsConstants.METADATA_FILTER,
                "{\"genre\": {\"$eq\": \"drama\"}, \"year\": {\"$gte\": 2020}, \"$or\": [{\"draft\": false}]}");

        send("bucket-a", "index-a", AWS2S3VectorsOperations.queryVectors, exchange);

        QueryVectorsRequest request = captured(QueryVectorsRequest.class);
        assertThat(request.returnMetadata()).isTrue();
        assertThat(request.returnDistance()).isTrue();
        Document filter = request.filter();
        assertThat(filter).isNotNull();
        assertThat(filter.asMap().get("genre").asMap().get("$eq").asString()).isEqualTo("drama");
        assertThat(filter.asMap().get("year").asMap().get("$gte").asNumber().intValue()).isEqualTo(2020);
        assertThat(filter.asMap().get("$or").asList().get(0).asMap().get("draft").asBoolean()).isFalse();
    }

    @Test
    void queryVectorsWithoutFilterSendsNoFilter() throws Exception {
        send("bucket-a", "index-a", AWS2S3VectorsOperations.queryVectors, exchange(List.of(0.1f, 0.2f)));

        assertThat(captured(QueryVectorsRequest.class).filter()).isNull();
    }

    @Test
    void queryVectorsRejectsAFilterThatIsNotJson() throws Exception {
        Exchange exchange = exchange(List.of(0.1f, 0.2f));
        exchange.getIn().setHeader(AWS2S3VectorsConstants.METADATA_FILTER, "genre = drama");

        assertThatThrownBy(() -> send("bucket-a", "index-a", AWS2S3VectorsOperations.queryVectors, exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AWS2S3VectorsConstants.METADATA_FILTER);
        Mockito.verify(client, Mockito.never()).queryVectors(any(QueryVectorsRequest.class));
    }

    @Test
    void putVectorsRejectsANullElementNamingItsPosition() throws Exception {
        Exchange exchange = exchange(Arrays.asList(0.1f, null, 0.3f));

        assertThatThrownBy(() -> send("bucket-a", "index-a", AWS2S3VectorsOperations.putVectors, exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("position 1");
    }

    @Test
    void putVectorsConvertsMetadataValuesThatAreNotStrings() throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", "Camel");
        metadata.put("year", 2026);
        metadata.put("rating", 4.5d);
        metadata.put("published", true);
        metadata.put("tags", List.of("integration", 1, false));
        Exchange exchange = exchange(List.of(0.1f, 0.2f));
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_METADATA, metadata);

        send("bucket-a", "index-a", AWS2S3VectorsOperations.putVectors, exchange);

        Map<String, Document> sent = captured(PutVectorsRequest.class).vectors().get(0).metadata().asMap();
        assertThat(sent.get("title").asString()).isEqualTo("Camel");
        assertThat(sent.get("year").asNumber().intValue()).isEqualTo(2026);
        assertThat(sent.get("rating").asNumber().doubleValue()).isEqualTo(4.5d);
        assertThat(sent.get("published").asBoolean()).isTrue();
        List<Document> tags = sent.get("tags").asList();
        assertThat(tags.get(0).asString()).isEqualTo("integration");
        assertThat(tags.get(1).asNumber().intValue()).isEqualTo(1);
        assertThat(tags.get(2).asBoolean()).isFalse();
    }

    @Test
    void putVectorsRejectsAnUnsupportedMetadataValueNamingTheKey() throws Exception {
        Exchange exchange = exchange(List.of(0.1f, 0.2f));
        exchange.getIn().setHeader(AWS2S3VectorsConstants.VECTOR_METADATA, Map.of("author", Map.of("name", "x")));

        assertThatThrownBy(() -> send("bucket-a", "index-a", AWS2S3VectorsOperations.putVectors, exchange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("author");
        Mockito.verify(client, Mockito.never()).putVectors(any(PutVectorsRequest.class));
    }

    private Exchange exchange(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }

    private void send(String bucket, String index, AWS2S3VectorsOperations operation, Exchange exchange)
            throws Exception {
        AWS2S3VectorsEndpoint endpoint = context.getEndpoint(
                "aws2-s3-vectors://" + bucket + "?vectorIndexName=" + index + "&operation=" + operation
                                                             + "&s3VectorsClient=#client",
                AWS2S3VectorsEndpoint.class);
        Producer producer = endpoint.createProducer();
        producer.start();
        try {
            producer.process(exchange);
        } finally {
            producer.stop();
        }
    }

    private <T> T captured(Class<T> requestType) {
        ArgumentCaptor<T> captor = ArgumentCaptor.forClass(requestType);
        if (requestType == PutVectorsRequest.class) {
            Mockito.verify(client).putVectors((PutVectorsRequest) captor.capture());
        } else if (requestType == GetVectorsRequest.class) {
            Mockito.verify(client).getVectors((GetVectorsRequest) captor.capture());
        } else if (requestType == DeleteVectorsRequest.class) {
            Mockito.verify(client).deleteVectors((DeleteVectorsRequest) captor.capture());
        } else if (requestType == QueryVectorsRequest.class) {
            Mockito.verify(client).queryVectors((QueryVectorsRequest) captor.capture());
        } else {
            throw new IllegalArgumentException("Not captured: " + requestType);
        }
        return captor.getValue();
    }
}
