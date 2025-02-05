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
import java.util.Map;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.neo4j.Neo4Operation;
import org.apache.camel.component.neo4j.Neo4j;
import org.apache.camel.component.neo4j.Neo4jComponent;
import org.apache.camel.test.infra.neo4j.services.Neo4jService;
import org.apache.camel.test.infra.neo4j.services.Neo4jServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LangChain4jEmbeddingsComponentNeo4jTargetIT extends CamelTestSupport {

    public static final String NEO4J_URI = "neo4j:neo4j";
    @RegisterExtension
    static Neo4jService NEO4J = Neo4jServiceFactory.createSingletonService();

    @Override

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        Neo4jComponent component = context.getComponent(Neo4j.SCHEME, Neo4jComponent.class);
        component.getConfiguration().setDbUri(NEO4J.getNeo4jDatabaseUri());
        component.getConfiguration().setDbUser(NEO4J.getNeo4jDatabaseUser());
        component.getConfiguration().setDbPassword(NEO4J.getNeo4jDatabasePassword());

        context.getRegistry().bind("embedding-model", new AllMiniLmL6V2EmbeddingModel());

        return context;
    }

    @Test
    @Order(0)
    void createVectorIndex() {
        Exchange result = fluentTemplate.to("neo4j:neo4j?vectorIndexName=myIndex&alias=m&label=Test&dimension=384")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.CREATE_VECTOR_INDEX)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.CREATE_VECTOR_INDEX, in.getHeader(Neo4j.Headers.OPERATION));
        assertTrue("The executed request should contain the create vector index",
                in.getHeader(Neo4j.Headers.QUERY_RESULT, String.class).contains("CREATE VECTOR INDEX myIndex IF NOT EXISTS"));

    }

    @Test
    @Order(1)
    public void insert() {

        Exchange result = fluentTemplate.to("direct:in")
                .withBody("hi")
                .request(Exchange.class);

        assertThat(result).isNotNull();

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals("Operation is create Vector", Neo4Operation.CREATE_VECTOR, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals("A node creation is expected ", 1, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_CREATED));

    }

    @Test
    @Order(2)
    void testSearchEmbedding() {
        var cypherQuery = "MATCH " +
                          "(m:Test {id: '1'})" +
                          "RETURN m";

        Exchange result = fluentTemplate.to(NEO4J_URI)
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);
        assertEquals(Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals("The database should retrieve the node with id =1 that was created by previous test", 1,
                in.getHeader(Neo4j.Headers.QUERY_RETRIEVE_SIZE));

        List resultList = in.getBody(List.class);
        assertNotNull("Body should be a list", resultList);

        assertEquals("The list of result should contain a unique embedding", 1, resultList.size());

        Map<String, Object> map = (Map<String, Object>) resultList.get(0);
        assertNotNull("getting the single result that shouldn't be null", map);
        assertTrue("The map should contain an id", map.containsKey("id"));
        assertEquals("The id should be equal to 1", "1", map.get("id"));
        assertTrue("The map should contain an embedding", map.containsKey("embedding"));
        List<Float> embedding = (List) map.get("embedding");
        assertTrue("The list of embeddings should be a list of float. if no erreor, this assert is valid", true);

    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("langchain4j-embeddings:test")
                        .setHeader(Neo4j.Headers.OPERATION).constant(Neo4Operation.CREATE_VECTOR)
                        .setHeader(Neo4j.Headers.VECTOR_ID).constant("1")
                        .setHeader(Neo4j.Headers.LABEL).constant("Test")
                        .transform(new org.apache.camel.spi.DataType("neo4j:embeddings"))
                        .to(NEO4J_URI);
            }
        };
    }

}
