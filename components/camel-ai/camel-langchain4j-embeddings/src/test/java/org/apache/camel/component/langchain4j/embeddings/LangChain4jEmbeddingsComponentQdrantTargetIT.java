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

import java.util.Collection;
import java.util.List;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.grpc.Collections;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.qdrant.Qdrant;
import org.apache.camel.component.qdrant.QdrantAction;
import org.apache.camel.component.qdrant.QdrantComponent;
import org.apache.camel.component.qdrant.QdrantHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.infra.qdrant.services.QdrantService;
import org.apache.camel.test.infra.qdrant.services.QdrantServiceFactory;
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
public class LangChain4jEmbeddingsComponentQdrantTargetIT extends CamelTestSupport {
    public static final long POINT_ID = 8;
    public static final String QDRANT_URI = "qdrant:embeddings";

    @RegisterExtension
    static QdrantService QDRANT = QdrantServiceFactory.createSingletonService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        var qc = context.getComponent(Qdrant.SCHEME, QdrantComponent.class);
        qc.getConfiguration().setHost(QDRANT.getGrpcHost());
        qc.getConfiguration().setPort(QDRANT.getGrpcPort());
        qc.getConfiguration().setTls(false);

        context.getRegistry().bind("embedding-model", new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @Order(1)
    public void createCollection() {
        Exchange result = fluentTemplate.to(QDRANT_URI)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.CREATE_COLLECTION)
                .withBody(
                        Collections.VectorParams.newBuilder()
                                .setSize(384)
                                .setDistance(Collections.Distance.Cosine).build())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    public void upsert() {
        Exchange result = fluentTemplate.to("direct:in")
                .withBody("hi")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(3)
    public void retrieve() {
        Exchange result = fluentTemplate.to(QDRANT_URI)
                .withHeader(QdrantHeaders.ACTION, QdrantAction.RETRIEVE)
                .withBody(PointIdFactory.id(POINT_ID))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> assertThat(c).hasSize(1));
    }

    @Test
    @Order(4)
    public void rag_similarity_search() {
        Exchange result = fluentTemplate.to("direct:search")
                .withBody("hi")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        assertThat(result.getIn().getBody()).isInstanceOfSatisfying(Collection.class, c -> assertThat(c).hasSize(1));
        Assertions.assertTrue(result.getIn().getBody(List.class).contains("hi"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("langchain4j-embeddings:test")
                        .setHeader(QdrantHeaders.ACTION).constant(QdrantAction.UPSERT)
                        .setHeader(QdrantHeaders.POINT_ID).constant(POINT_ID)
                        // transform data to embed to a vecto embeddings
                        .transformDataType(
                                new DataType("qdrant:embeddings"))
                        .to(QDRANT_URI);

                from("direct:search")
                        .to("langchain4j-embeddings:test")
                        // transform prompt into embeddings for search
                        .transformDataType(
                                new DataType("qdrant:embeddings"))
                        .setHeader(QdrantHeaders.ACTION, constant(QdrantAction.SIMILARITY_SEARCH))
                        .setHeader(QdrantHeaders.INCLUDE_VECTORS, constant(true))
                        .setHeader(QdrantHeaders.INCLUDE_PAYLOAD, constant(true))
                        .to(QDRANT_URI)
                        // decode retrieved embeddings for RAG
                        .transformDataType(
                                new DataType("qdrant:rag"));
            }
        };
    }
}
