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
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
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
    public static final String WEAVIATE_URI = "weaviate:Embeddings";
    private static String CREATEID = null;

    @RegisterExtension
    static WeaviateService WEAVIATE = WeaviateServiceFactory.createSingletonService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        var qc = context.getComponent(WeaviateVectorDb.SCHEME, WeaviateVectorDbComponent.class);
        qc.getConfiguration().setScheme("http");
        qc.getConfiguration().setHost(WEAVIATE.getWeaviateHost() + ":" + WEAVIATE.getWeaviatePort());
        qc.getConfiguration().setGrpcHost(WEAVIATE.getWeaviateHost());
        qc.getConfiguration().setGrpcPort(WEAVIATE.getWeaviateGrpcPort());
        context.getRegistry().bind("embedding-model", new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @Order(1)
    public void createCollection() {
        Exchange result = fluentTemplate.to(WEAVIATE_URI)
                .withHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.CREATE_COLLECTION)
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
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, "Embeddings")
                .withHeader(WeaviateVectorDbHeaders.PROPERTIES, map)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        WeaviateObject<Map<String, Object>> res = (WeaviateObject<Map<String, Object>>) result.getIn().getBody();
        CREATEID = res.uuid();
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
                .withHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, "Embeddings")
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

        Optional<WeaviateObject<Map<String, Object>>> res
                = (Optional<WeaviateObject<Map<String, Object>>>) result.getIn().getBody();
        assertThat(res).isPresent();

        WeaviateObject<Map<String, Object>> wo = res.get();
        Map<String, Object> props = wo.properties();
        assertThat(props).containsKey("sky");
        assertThat(props).containsKey("age");
        assertThat(props).containsKey("dog");
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
