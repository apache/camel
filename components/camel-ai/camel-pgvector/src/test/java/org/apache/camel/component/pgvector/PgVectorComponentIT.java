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
package org.apache.camel.component.pgvector;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
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
public class PgVectorComponentIT extends CamelTestSupport {

    public static final String PGVECTOR_URI = "pgvector:test_embeddings";
    private static final int DIMENSION = 3;

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
        component.getConfiguration().setDimension(DIMENSION);

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
        assertThat(result.getMessage().getBody(Boolean.class)).isTrue();
    }

    @Test
    @Order(2)
    public void createIndex() {
        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.CREATE_INDEX)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(Boolean.class)).isTrue();
    }

    @Test
    @Order(3)
    public void upsert() {
        List<Float> vector = Arrays.asList(0.1f, 0.2f, 0.3f);

        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.UPSERT)
                .withHeader(PgVectorHeaders.RECORD_ID, "test-1")
                .withHeader(PgVectorHeaders.TEXT_CONTENT, "Hello World")
                .withHeader(PgVectorHeaders.METADATA, "{\"category\":\"greeting\"}")
                .withBody(vector)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("test-1");
    }

    @Test
    @Order(4)
    public void upsertUpdate() {
        // Update test-1 with new text and vector to verify ON CONFLICT DO UPDATE
        List<Float> vector = Arrays.asList(0.15f, 0.25f, 0.35f);

        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.UPSERT)
                .withHeader(PgVectorHeaders.RECORD_ID, "test-1")
                .withHeader(PgVectorHeaders.TEXT_CONTENT, "Hello Updated World")
                .withBody(vector)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("test-1");
    }

    @Test
    @Order(5)
    public void upsertSecond() {
        List<Float> vector = Arrays.asList(0.4f, 0.5f, 0.6f);

        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.UPSERT)
                .withHeader(PgVectorHeaders.RECORD_ID, "test-2")
                .withHeader(PgVectorHeaders.TEXT_CONTENT, "Goodbye World")
                .withHeader(PgVectorHeaders.METADATA, "{\"category\":\"farewell\"}")
                .withBody(vector)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("test-2");
    }

    @Test
    @Order(6)
    public void upsertAutoId() {
        // Verify auto-generated UUID when no RECORD_ID is provided
        List<Float> vector = Arrays.asList(0.7f, 0.8f, 0.9f);

        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.UPSERT)
                .withHeader(PgVectorHeaders.TEXT_CONTENT, "Auto ID record")
                .withBody(vector)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        String autoId = result.getMessage().getBody(String.class);
        assertThat(autoId).isNotNull();
        // Verify it's a valid UUID format
        assertThat(autoId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @Order(7)
    @SuppressWarnings("unchecked")
    public void similaritySearch() {
        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);

        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.SIMILARITY_SEARCH)
                .withHeader(PgVectorHeaders.QUERY_TOP_K, 2)
                .withBody(queryVector)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        List<Map<String, Object>> results = result.getMessage().getBody(List.class);
        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeLessThanOrEqualTo(2);

        // The closest vector should be test-1 (updated by upsertUpdate test)
        assertThat(results.get(0).get("id")).isEqualTo("test-1");
        assertThat(results.get(0).get("text_content")).isEqualTo("Hello Updated World");
    }

    @Test
    @Order(8)
    @SuppressWarnings("unchecked")
    public void similaritySearchWithFilter() {
        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);

        // Filter to only match the "farewell" category — should return test-2 even though test-1 is closer
        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.SIMILARITY_SEARCH)
                .withHeader(PgVectorHeaders.QUERY_TOP_K, 2)
                .withHeader(PgVectorHeaders.FILTER, "metadata LIKE '%farewell%'")
                .withBody(queryVector)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        List<Map<String, Object>> results = result.getMessage().getBody(List.class);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("id")).isEqualTo("test-2");
        assertThat(results.get(0).get("text_content")).isEqualTo("Goodbye World");
    }

    @Test
    @Order(9)
    @SuppressWarnings("unchecked")
    public void similaritySearchWithParameterizedFilter() {
        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);

        // Same filter as above but using parameterized query for safety
        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.SIMILARITY_SEARCH)
                .withHeader(PgVectorHeaders.QUERY_TOP_K, 2)
                .withHeader(PgVectorHeaders.FILTER, "metadata LIKE ?")
                .withHeader(PgVectorHeaders.FILTER_PARAMS, List.of("%farewell%"))
                .withBody(queryVector)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        List<Map<String, Object>> results = result.getMessage().getBody(List.class);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("id")).isEqualTo("test-2");
        assertThat(results.get(0).get("text_content")).isEqualTo("Goodbye World");
    }

    @Test
    @Order(10)
    public void delete() {
        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.DELETE)
                .withHeader(PgVectorHeaders.RECORD_ID, "test-1")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(Boolean.class)).isTrue();
    }

    @Test
    @Order(11)
    @SuppressWarnings("unchecked")
    public void searchAfterDelete() {
        List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f);

        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.SIMILARITY_SEARCH)
                .withHeader(PgVectorHeaders.QUERY_TOP_K, 2)
                .withBody(queryVector)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();

        List<Map<String, Object>> results = result.getMessage().getBody(List.class);
        assertThat(results).hasSize(2);
        // test-1 was deleted, so results should be test-2 and auto-ID record
        assertThat(results).extracting(r -> r.get("id")).doesNotContain("test-1");
    }

    @Test
    @Order(12)
    public void missingActionHeader() {
        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withBody(Arrays.asList(0.1f, 0.2f, 0.3f))
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isInstanceOf(NoSuchHeaderException.class);
    }

    @Test
    @Order(13)
    public void deleteNonExistentRecord() {
        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.DELETE)
                .withHeader(PgVectorHeaders.RECORD_ID, "non-existent-id")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(Boolean.class)).isFalse();
    }

    @Test
    @Order(14)
    public void dropTable() {
        Exchange result = fluentTemplate.to(PGVECTOR_URI)
                .withHeader(PgVectorHeaders.ACTION, PgVectorAction.DROP_TABLE)
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(Boolean.class)).isTrue();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to(PGVECTOR_URI);
            }
        };
    }
}
