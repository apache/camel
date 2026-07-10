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
package org.apache.camel.component.weaviate.it;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.weaviate.client6.v1.api.collections.Vectors;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.aggregate.AggregateResponse;
import io.weaviate.client6.v1.api.collections.data.InsertManyResponse;
import io.weaviate.client6.v1.api.collections.query.QueryResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.weaviate.WeaviateTestSupport;
import org.apache.camel.component.weaviate.WeaviateVectorDbAction;
import org.apache.camel.component.weaviate.WeaviateVectorDbHeaders;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

// This Integration test is meant to be run over the local container set up
// by camel-test-infra-weaviate - if we want to run the integration tests against
// a weaviate server, we should not run it.  If weaviate.apikey is specified,
// skip this IT
@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "weaviate.apikey", matches = ".*", disabledReason = "weaviate API Key provided"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WeaviateContainerIT extends WeaviateTestSupport {
    private static final String COLLECTION = "WeaviateITCollection";
    private static String CREATEID = null;

    @Test
    @Order(1)
    public void createCollection() {

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.CREATE_COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        Boolean res = result.getIn().getBody(Boolean.class);
        assertThat(res).isTrue();
    }

    @Test
    @Order(2)
    public void create() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.0f);

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("sky", "blue");
        map.put("age", "34");

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.CREATE)
                .withBody(elements)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.PROPERTIES, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        WeaviateObject<Map<String, Object>> res = (WeaviateObject<Map<String, Object>>) result.getIn().getBody();
        CREATEID = res.uuid();

        assertThat(res).isNotNull();
        assertThat(CREATEID).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(3)
    public void batchCreate() {

        WeaviateObject<Map<String, Object>> obj1 = new WeaviateObject.Builder<Map<String, Object>>()
                .properties(Map.of("sky", "green", "age", "10"))
                .vectors(Vectors.of(new float[] { 4.0f, 5.0f, 6.0f }))
                .build();
        WeaviateObject<Map<String, Object>> obj2 = new WeaviateObject.Builder<Map<String, Object>>()
                .properties(Map.of("sky", "red", "age", "20"))
                .vectors(Vectors.of(new float[] { 7.0f, 8.0f, 9.0f }))
                .build();

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.BATCH_CREATE)
                .withBody(List.of(obj1, obj2))
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        InsertManyResponse res = (InsertManyResponse) result.getIn().getBody();
        assertThat(res.uuids()).hasSize(2);
        assertThat(res.errors()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(4)
    public void hybridQuery() {

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.HYBRID_QUERY)
                .withBody("blue")
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 10)
                .withHeader(WeaviateVectorDbHeaders.HYBRID_ALPHA, 0.5f)
                .withHeader(WeaviateVectorDbHeaders.QUERY_VECTOR, Arrays.asList(1.0f, 2.0f, 3.0f))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        QueryResponse<Map<String, Object>> queryResponse
                = (QueryResponse<Map<String, Object>>) result.getIn().getBody();
        assertThat(queryResponse).isNotNull();
        assertThat(queryResponse.objects()).isNotEmpty();
        assertThat(queryResponse.objects().get(0).properties()).containsKey("sky");
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(5)
    public void hybridQueryWithoutVector() {

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.HYBRID_QUERY)
                .withBody("blue")
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 10)
                .withHeader(WeaviateVectorDbHeaders.HYBRID_ALPHA, 0.0f)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        QueryResponse<Map<String, Object>> queryResponse
                = (QueryResponse<Map<String, Object>>) result.getIn().getBody();
        assertThat(queryResponse).isNotNull();
        assertThat(queryResponse.objects()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(6)
    public void bm25Query() {

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.BM25_QUERY)
                .withBody("blue")
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 10)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        QueryResponse<Map<String, Object>> queryResponse
                = (QueryResponse<Map<String, Object>>) result.getIn().getBody();
        assertThat(queryResponse).isNotNull();
        assertThat(queryResponse.objects()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(7)
    public void bm25QueryWithFields() {

        HashMap<String, Object> fields = new HashMap<>();
        fields.put("sky", null);

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.BM25_QUERY)
                .withBody("blue")
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 10)
                .withHeader(WeaviateVectorDbHeaders.FIELDS, fields)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        QueryResponse<Map<String, Object>> queryResponse
                = (QueryResponse<Map<String, Object>>) result.getIn().getBody();
        assertThat(queryResponse).isNotNull();
        assertThat(queryResponse.objects()).isNotNull();
    }

    @Test
    @Order(8)
    public void aggregate() {

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.AGGREGATE)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        AggregateResponse res = (AggregateResponse) result.getIn().getBody();
        assertThat(res.totalCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @Order(9)
    public void updateById() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.0f);

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("dog", "dachshund");

        Exchange result = fluentTemplate.to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.UPDATE_BY_ID)
                .withBody(elements)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.INDEX_ID, CREATEID)
                .withHeader(WeaviateVectorDbHeaders.PROPERTIES, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        Boolean res = result.getIn().getBody(Boolean.class);
        assertThat(res).isTrue();
    }

    @Test
    @Order(10)
    public void queryById() {

        Exchange result = fluentTemplate.to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.QUERY_BY_ID)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.INDEX_ID, CREATEID)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        Optional<WeaviateObject<Map<String, Object>>> res
                = (Optional<WeaviateObject<Map<String, Object>>>) result.getIn().getBody();
        assertThat(res).isPresent();

        WeaviateObject<Map<String, Object>> wo = res.get();
        Map<String, Object> props = wo.properties();
        assertThat(props).containsKey("sky");
        assertThat(props).containsKey("age");
        assertThat(props).containsKey("dog");
    }

    @SuppressWarnings("unchecked")
    @Test
    @Order(11)
    public void queryByVector() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.2f);

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("sky", "blue");

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.QUERY)
                .withBody(
                        elements)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 20)
                .withHeader(WeaviateVectorDbHeaders.FIELDS, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        QueryResponse<Map<String, Object>> queryResponse
                = (QueryResponse<Map<String, Object>>) result.getIn().getBody();
        assertThat(queryResponse.objects()).isNotEmpty();
        assertThat(queryResponse.objects().get(0).properties()).containsEntry("sky", "blue");
    }

    @Test
    @Order(12)
    public void deleteById() {

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.DELETE_BY_ID)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.INDEX_ID, CREATEID)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        Boolean res = result.getIn().getBody(Boolean.class);
        assertThat(res).isTrue();
    }

    @Test
    @Order(13)
    public void deleteCollection() {
        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.DELETE_COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        Boolean res = result.getIn().getBody(Boolean.class);
        assertThat(res).isTrue();
    }

    private String getUri() {
        if (System.getProperties().containsKey("weaviate.apikey")) {
            return "weaviate:test-collection?scheme={{weaviate.scheme}}&host={{weaviate.host}}&apiKey={{weaviate.apikey}}";
        }

        return "weaviate:test-collection";
    }

}
