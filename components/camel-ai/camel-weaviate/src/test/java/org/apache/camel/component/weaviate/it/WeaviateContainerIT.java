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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
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
        Result<Boolean> res = (Result<Boolean>) result.getIn().getBody();
        assertThat(res.hasErrors()).isFalse();
        assertThat(res.getResult()).isTrue();
        assertThat(result.getException()).isNull();
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

        Result<WeaviateObject> res = (Result<WeaviateObject>) result.getIn().getBody();
        CREATEID = res.getResult().getId();

        assertThat(res.hasErrors()).isFalse();
        assertThat(res).isNotNull();
    }

    @Test
    @Order(7)
    public void updateById() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.0f);

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("dog", "dachshund");

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.UPDATE_BY_ID)
                .withBody(elements)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.INDEX_ID, CREATEID)
                .withHeader(WeaviateVectorDbHeaders.PROPERTIES, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();

        Result<Boolean> res = (Result<Boolean>) result.getIn().getBody();
        assertThat(res.hasErrors()).isFalse();
        assertThat(res.getResult()).isTrue();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(8)
    public void queryById() {

        Exchange result = fluentTemplate
                .to(getUri())
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.QUERY_BY_ID)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.INDEX_ID, CREATEID)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        Result<WeaviateObject> res = (Result<WeaviateObject>) result.getIn().getBody();
        assertThat(res.hasErrors()).isFalse();

        List<WeaviateObject> list = (List) res.getResult();
        for (WeaviateObject wo : list) {

            Map<String, Object> map = wo.getProperties();
            assertThat(map).containsKey("sky");
            assertThat(map).containsKey("age");
            assertThat(map).containsKey("dog");
        }
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
                .withBody(elements)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDbHeaders.QUERY_TOP_K, 20)
                .withHeader(WeaviateVectorDbHeaders.FIELDS, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        GraphQLResponse<?> qlResponse =
                (GraphQLResponse<?>) result.getIn().getBody(Result.class).getResult();
        var dataMap = (Map<String, Map<String, List<Map<String, String>>>>) qlResponse.getData();
        assertThat(dataMap.get("Get").get(COLLECTION).get(0)).containsEntry("sky", "blue");
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
        Result<Boolean> res = (Result<Boolean>) result.getIn().getBody();

        assertThat(res.hasErrors()).isFalse();
        assertThat(res.getResult()).isTrue();
        assertThat(result.getException()).isNull();
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
        Result<Boolean> res = (Result<Boolean>) result.getIn().getBody();
        assertThat(res.hasErrors()).isFalse();
        assertThat(res.getResult()).isTrue();
        assertThat(result.getException()).isNull();
    }

    private String getUri() {
        if (System.getProperties().containsKey("weaviate.apikey")) {
            return "weaviate:test-collection?scheme={{weaviate.scheme}}&host={{weaviate.host}}&apiKey={{weaviate.apikey}}";
        }

        return "weaviate:test-collection";
    }
}
