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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.qdrant.QdrantAction;
import org.apache.camel.component.weaviate.WeaviateVectorDb;
import org.apache.camel.component.weaviate.WeaviateVectorDbAction;
import org.apache.camel.component.weaviate.WeaviateVectorDbComponent;
import org.apache.camel.component.weaviate.WeaviateVectorDbHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.infra.weaviate.services.WeaviateService;
import org.apache.camel.test.infra.weaviate.services.WeaviateServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LangChain4jEmbeddingsComponentWeaviateTargetIT extends CamelTestSupport {
    public static final long POINT_ID = 8;
    public static final String WEAVIATE_URI = "weaviate:embeddings";
    private static String CREATEID = null;

    @RegisterExtension
    static WeaviateService WEAVIATE = WeaviateServiceFactory.createSingletonService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        var qc = context.getComponent(WeaviateVectorDb.SCHEME, WeaviateVectorDbComponent.class);
        qc.getConfiguration().setScheme("http");
        qc.getConfiguration().setHost(WEAVIATE.getWeaviateEndpointUrl());
        context.getRegistry().bind("embedding-model", new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @Order(1)
    public void createCollection() {
        Exchange result = fluentTemplate.to(WEAVIATE_URI)
                .withHeader(WeaviateVectorDbHeaders.ACTION, QdrantAction.CREATE_COLLECTION)
                .withBody(
                        WeaviateClass.builder().className("embeddings").build())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(3)
    public void create() {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("sky", "blue");
        map.put("age", "34");

        Exchange result = fluentTemplate.to("direct:in")
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.CREATE)
                .withBody("hi")
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, "embeddings")
                .withHeader(WeaviateVectorDbHeaders.PROPERTIES, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        Result<WeaviateObject> res = (Result<WeaviateObject>) result.getIn().getBody();
        CREATEID = res.getResult().getId();
    }

    @Test
    @Order(3)
    public void updateById() {

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("dog", "dachshund");

        Exchange result = fluentTemplate.to("direct:up")
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.UPDATE_BY_ID)
                .withBody("hi")
                .withHeader(WeaviateVectorDbHeaders.INDEX_ID, CREATEID)
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, "embeddings")
                .withHeader(WeaviateVectorDbHeaders.PROPERTIES, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(8)
    public void querybyid() {
        Exchange result = fluentTemplate.to(WEAVIATE_URI)
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.QUERY_BY_ID)
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

    @Test
    @Order(9)
    public void query() {
        Exchange result = fluentTemplate.to("direct:query")
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.QUERY)
                .withBody("hi")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        Assertions.assertNotNull(result.getIn().getBody());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("langchain4j-embeddings:test")
                        .setHeader(WeaviateVectorDbHeaders.ACTION).constant(WeaviateVectorDbAction.CREATE)
                        // transform data to embed to a vecto embeddings
                        .transformDataType(
                                new DataType("weaviate:embeddings"))
                        .to(WEAVIATE_URI);

                from("direct:up")
                        .to("langchain4j-embeddings:test")
                        // transform prompt into embeddings for search
                        .transformDataType(
                                new DataType("weaviate:embeddings"))
                        .setHeader(WeaviateVectorDbHeaders.ACTION, constant(WeaviateVectorDbAction.UPDATE_BY_ID))
                        .to(WEAVIATE_URI)
                        // decode retrieved embeddings for RAG
                        .transformDataType(
                                new DataType("weaviate:embeddings"));

                from("direct:query")
                        .to("langchain4j-embeddings:test")
                        // transform prompt into embeddings for search
                        .transformDataType(
                                new DataType("weaviate:embeddings"))
                        .setHeader(WeaviateVectorDbHeaders.ACTION, constant(WeaviateVectorDbAction.QUERY))
                        .to(WEAVIATE_URI)
                        // decode retrieved embeddings for RAG
                        .transformDataType(
                                new DataType("weaviate:embeddings"));
            }
        };
    }
}
