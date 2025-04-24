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

import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;
import org.apache.camel.Exchange;
import org.apache.camel.component.weaviate.WeaviateTestSupport;
import org.apache.camel.component.weaviate.WeaviateVectorDb;
import org.apache.camel.component.weaviate.WeaviateVectorDbAction;
import org.junit.jupiter.api.*;
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
    private static final String COLLECTION = "WeaviateComponentITCollection";
    private static String CREATEID = null;

    @Test
    @Order(1)
    public void createCollection() {

        Exchange result = fluentTemplate
                .to("weaviate:test-collection")
                .withHeader(WeaviateVectorDb.Headers.ACTION, WeaviateVectorDbAction.CREATE_COLLECTION)
                .withHeader(WeaviateVectorDb.Headers.COLLECTION_NAME, COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        Result<Boolean> res = (Result<Boolean>) result.getIn().getBody();
        assertThat(!res.hasErrors());
        assertThat(res.getResult() == true);
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
                .to("weaviate:test-collection")
                .withHeader(WeaviateVectorDb.Headers.ACTION, WeaviateVectorDbAction.CREATE)
                .withBody(elements)
                .withHeader(WeaviateVectorDb.Headers.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDb.Headers.PROPERTIES, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();

        Result<WeaviateObject> res = (Result<WeaviateObject>) result.getIn().getBody();
        CREATEID = res.getResult().getId();

        assertThat(!res.hasErrors());
        assertThat(res != null);
    }

    @Test
    @Order(8)
    public void queryByVector() {

        List<Float> elements = Arrays.asList(1.0f, 2.0f, 3.2f);

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("sky", "blue");

        Exchange result = fluentTemplate
                .to("weaviate:test-collection")
                .withHeader(WeaviateVectorDb.Headers.ACTION, WeaviateVectorDbAction.QUERY)
                .withBody(
                        elements)
                .withHeader(WeaviateVectorDb.Headers.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDb.Headers.QUERY_TOP_K, 20)
                .withHeader(WeaviateVectorDb.Headers.FIELDS, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        List<Float> vector = (List<Float>) result.getIn().getBody();
        assertThat(vector.get(0) == 1.0f);
        assertThat(vector.get(1) == 2.0f);
        assertThat(vector.get(2) == 3.0f);
    }

    @Test
    @Order(9)
    public void deleteById() {

        Exchange result = fluentTemplate
                .to("weaviate:test-collection")
                .withHeader(WeaviateVectorDb.Headers.ACTION, WeaviateVectorDbAction.DELETE_BY_ID)
                .withHeader(WeaviateVectorDb.Headers.COLLECTION_NAME, COLLECTION)
                .withHeader(WeaviateVectorDb.Headers.INDEX_ID, CREATEID)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        Result<Boolean> res = (Result<Boolean>) result.getIn().getBody();

        assertThat(!res.hasErrors());
        assertThat(res.getResult() == true);
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(10)
    public void deleteCollection() {
        Exchange result = fluentTemplate
                .to("weaviate:test-collection")
                .withHeader(WeaviateVectorDb.Headers.ACTION, WeaviateVectorDbAction.DELETE_COLLECTION)
                .withHeader(WeaviateVectorDb.Headers.COLLECTION_NAME, COLLECTION)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        Result<Boolean> res = (Result<Boolean>) result.getIn().getBody();
        assertThat(!res.hasErrors());
        assertThat(res.getResult() == true);
        assertThat(result.getException()).isNull();
    }

}
