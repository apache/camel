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

import io.weaviate.client6.v1.api.collections.WeaviateObject;
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

    @Test
    @Order(7)
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
    @Order(8)
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
    @Order(8)
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
    @Order(9)
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
    @Order(10)
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
