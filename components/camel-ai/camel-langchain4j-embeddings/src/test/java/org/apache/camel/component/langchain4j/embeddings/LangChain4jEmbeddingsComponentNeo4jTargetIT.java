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
import java.util.Map;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.neo4j.Neo4Operation;
import org.apache.camel.component.neo4j.Neo4jComponent;
import org.apache.camel.component.neo4j.Neo4jConstants;
import org.apache.camel.component.neo4j.Neo4jHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.infra.neo4j.services.Neo4jService;
import org.apache.camel.test.infra.neo4j.services.Neo4jServiceFactory;
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
public class LangChain4jEmbeddingsComponentNeo4jTargetIT extends CamelTestSupport {

    public static final String NEO4J_URI = "neo4j:neo4j?vectorIndexName=myIndex&label=Test";
    @RegisterExtension
    static Neo4jService NEO4J = Neo4jServiceFactory.createSingletonService();

    @Override

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Neo4jComponent component = context.getComponent(Neo4jConstants.SCHEME, Neo4jComponent.class);
        component.getConfiguration().setDatabaseUrl(NEO4J.getNeo4jDatabaseUri());
        component.getConfiguration().setUsername(NEO4J.getNeo4jDatabaseUser());
        component.getConfiguration().setPassword(NEO4J.getNeo4jDatabasePassword());

        context.getRegistry().bind("embedding-model", new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @Order(0)
    void createVectorIndex() {
        Exchange result = fluentTemplate.to("neo4j:neo4j?vectorIndexName=myIndex&alias=m&label=Test&dimension=384")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.CREATE_VECTOR_INDEX)
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(Neo4Operation.CREATE_VECTOR_INDEX, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertTrue(in.getHeader(Neo4jHeaders.QUERY_RESULT, String.class)
                .contains("CREATE VECTOR INDEX myIndex IF NOT EXISTS"),
                "The executed request should contain the create vector index");

    }

    @Test
    @Order(1)
    public void insert() {

        Exchange result = fluentTemplate.to("direct:in")
                .withBody("hi")
                .request(Exchange.class);

        assertThat(result).isNotNull();

        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(Neo4Operation.CREATE_VECTOR, in.getHeader(Neo4jHeaders.OPERATION),
                "Operation is create Vector");
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_CREATED),
                "A node creation is expected ");

    }

    @Test
    @Order(2)
    void testSearchEmbedding() {
        var cypherQuery = "MATCH " +
                          "(m:Test {id: '1'})" +
                          "RETURN m";

        Exchange result = fluentTemplate.to(NEO4J_URI)
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);
        Assertions.assertEquals(Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY,
                in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE),
                "The database should retrieve the node with id =1 that was created by previous test");

        List resultList = in.getBody(List.class);
        Assertions.assertNotNull(resultList, "Body should be a list");

        Assertions.assertEquals(1, resultList.size(), "The list of result should contain a unique embedding");

        Map<String, Object> map = (Map<String, Object>) resultList.get(0);
        Assertions.assertNotNull(map, "getting the single result that shouldn't be null");
        Assertions.assertTrue(map.containsKey("id"), "The map should contain an id");
        Assertions.assertEquals("1", map.get("id"), "The id should be equal to 1");
        Assertions.assertTrue(map.containsKey("embedding"), "The map should contain an embedding");
        List<Float> embedding = (List) map.get("embedding");
        Assertions.assertTrue(true, "The list of embeddings should be a list of float. if no erreor, this assert is valid");

    }

    @Test
    @Order(3)
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
                        .setHeader(Neo4jHeaders.OPERATION).constant(Neo4Operation.CREATE_VECTOR)
                        .setHeader(Neo4jHeaders.VECTOR_ID).constant("1")
                        .setHeader(Neo4jHeaders.LABEL).constant("Test")
                        .transformDataType(new org.apache.camel.spi.DataType("neo4j:embeddings"))
                        .to(NEO4J_URI);

                from("direct:search")
                        .to("langchain4j-embeddings:test")
                        // transform prompt into embeddings for search
                        .transformDataType(
                                new DataType("neo4j:embeddings"))
                        .setHeader(Neo4jHeaders.OPERATION, constant(Neo4Operation.VECTOR_SIMILARITY_SEARCH))
                        .to(NEO4J_URI)
                        // decode retrieved embeddings for RAG
                        .transformDataType(
                                new DataType("neo4j:rag"));
            }
        };
    }

}
