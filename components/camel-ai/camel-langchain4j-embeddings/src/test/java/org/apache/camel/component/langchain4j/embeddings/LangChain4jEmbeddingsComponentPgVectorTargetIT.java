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

import java.util.List;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.pgvector.PgVector;
import org.apache.camel.component.pgvector.PgVectorAction;
import org.apache.camel.component.pgvector.PgVectorComponent;
import org.apache.camel.component.pgvector.PgVectorHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.infra.postgres.services.PostgresService;
import org.apache.camel.test.infra.postgres.services.PostgresVectorServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.ds.PGSimpleDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LangChain4jEmbeddingsComponentPgVectorTargetIT extends CamelTestSupport {

    public static final String PGVECTOR_URI = "pgvector:embeddings";

    @RegisterExtension
    static PostgresService POSTGRES = PostgresVectorServiceFactory.createService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.jdbcUrl());
        dataSource.setUser(POSTGRES.userName());
        dataSource.setPassword(POSTGRES.password());

        PgVectorComponent component = context.getComponent(PgVector.SCHEME, PgVectorComponent.class);
        component.getConfiguration().setDataSource(dataSource);
        context.getRegistry().bind("embedding-model", new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @Order(1)
    public void createTable() {
        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.CREATE_TABLE)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(2)
    public void upsertEmbedding() {
        Exchange result = fluentTemplate.to("direct:in")
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.UPSERT)
                .withHeader(PgVectorHeaders.RECORD_ID, "embed-1")
                .withBody("The sky is blue")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("embed-1");
    }

    @Test
    @Order(3)
    public void upsertSecondEmbedding() {
        Exchange result = fluentTemplate.to("direct:in")
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.UPSERT)
                .withHeader(PgVectorHeaders.RECORD_ID, "embed-2")
                .withBody("The ocean is deep")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
    }

    @Test
    @Order(4)
    @SuppressWarnings("unchecked")
    public void queryEmbeddings() {
        Exchange result = fluentTemplate.to("direct:query")
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.SIMILARITY_SEARCH)
                .withBody("blue sky")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        List<String> results = result.getMessage().getBody(List.class);
        assertThat(results).isNotEmpty();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("langchain4j-embeddings:test")
                        .transformDataType(new DataType("pgvector:embeddings"))
                        .to(PGVECTOR_URI);

                from("direct:query")
                        .to("langchain4j-embeddings:test")
                        .transformDataType(new DataType("pgvector:embeddings"))
                        .setHeader(PgVectorHeaders.ACTION, constant(PgVectorAction.SIMILARITY_SEARCH))
                        .to(PGVECTOR_URI)
                        .transformDataType(new DataType("pgvector:rag"));
            }
        };
    }
}
