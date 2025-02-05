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
package org.apache.camel.component.neo4j.it;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.neo4j.Neo4Operation;
import org.apache.camel.component.neo4j.Neo4jConstants;
import org.apache.camel.component.neo4j.Neo4jEmbedding;
import org.apache.camel.component.neo4j.Neo4jTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Neo4jVectorEmbeddingsIT extends Neo4jTestSupport {
    @Test
    @Order(0)
    void createVectorIndex() {
        Exchange result = fluentTemplate.to("neo4j:neo4j?vectorIndexName=movieIdx&alias=m&label=Movie&dimension=2")
                .withHeader(Neo4jConstants.Headers.OPERATION, Neo4Operation.CREATE_VECTOR_INDEX)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.CREATE_VECTOR_INDEX, in.getHeader(Neo4jConstants.Headers.OPERATION));
        assertTrue("The executed request should contain the create vector index",
                in.getHeader(Neo4jConstants.Headers.QUERY_RESULT, String.class)
                        .contains("CREATE VECTOR INDEX movieIdx IF NOT EXISTS"));

    }

    @ParameterizedTest
    @EnumSource(TestData.class)
    @Order(1)
    void addVectorIndex(TestData testData) {
        Exchange result = fluentTemplate.to("neo4j:neo4j?vectorIndexName=movieIdx&label=Movie&alias=m")
                .withHeader(Neo4jConstants.Headers.OPERATION, Neo4Operation.CREATE_VECTOR)
                .withHeader(Neo4jConstants.Headers.VECTOR_ID, testData.getId())
                .withHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, testData.getVectors())
                .withBody(testData.getText())
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.CREATE_VECTOR, in.getHeader(Neo4jConstants.Headers.OPERATION));
        assertTrue("The executed request should contain the procedure of setting vector embedding",
                in.getHeader(Neo4jConstants.Headers.QUERY_RESULT, String.class)
                        .contains("CALL db.create.setNodeVectorProperty"));
        assertEquals("A node creation is expected ", 1, in.getHeader(Neo4jConstants.Headers.QUERY_RESULT_NODES_CREATED));
    }

    @Order(2)
    @Test
    void addGeneratedNeo4jEmbedding() {
        Neo4jEmbedding neo4jEmbedding = new Neo4jEmbedding("15", "Hello World", new float[] { 10.8f, 10.6f });
        Exchange result = fluentTemplate.to("neo4j:neo4j?vectorIndexName=movieIdx&label=Movie&alias=m")
                .withHeader(Neo4jConstants.Headers.OPERATION, Neo4Operation.CREATE_VECTOR)
                .withBody(neo4jEmbedding)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.CREATE_VECTOR, in.getHeader(Neo4jConstants.Headers.OPERATION));
        assertTrue("The executed request should contain the procedure of setting vector embedding",
                in.getHeader(Neo4jConstants.Headers.QUERY_RESULT, String.class)
                        .contains("CALL db.create.setNodeVectorProperty"));
        assertEquals("A node creation is expected ", 1, in.getHeader(Neo4jConstants.Headers.QUERY_RESULT_NODES_CREATED));
    }

    @Test
    @Order(3)
    public void similaritySeach() {
        Exchange result = fluentTemplate.to("neo4j:neo4j?vectorIndexName=movieIdx&label=Movie&alias=m")
                .withHeader(Neo4jConstants.Headers.OPERATION, Neo4Operation.VECTOR_SIMILARITY_SEARCH)
                .withBody(List.of(0.75f, 0.65f))
                .request(Exchange.class);

        Message in = result.getMessage();
        assertNotNull(in);
        assertEquals(Neo4Operation.VECTOR_SIMILARITY_SEARCH, in.getHeader(Neo4jConstants.Headers.OPERATION));
        assertEquals(3, in.getHeader(Neo4jConstants.Headers.QUERY_RETRIEVE_SIZE));

        List resultList = in.getBody(List.class);
        assertNotNull(resultList);

        assertEquals(3, resultList.size());

    }

    @Test
    @Order(4)
    void dropVectorIndex() {
        Exchange result = fluentTemplate.to("neo4j:neo4j?vectorIndexName=movieIdx")
                .withHeader(Neo4jConstants.Headers.OPERATION, Neo4Operation.DROP_VECTOR_INDEX)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.DROP_VECTOR_INDEX, in.getHeader(Neo4jConstants.Headers.OPERATION));
        assertTrue("The executed request should contain the drop vector index",
                in.getHeader(Neo4jConstants.Headers.QUERY_RESULT, String.class).contains("DROP INDEX movieIdx"));
    }

    // Enum to provide test data
    public enum TestData {
        VECTOR_1(9, "VECTOR_1", List.of(0.8f, 0.6f)),
        VECTOR_2(10, "VECTOR_2", List.of(0.1f, 0.9f)),
        VECTOR_3(11, "VECTOR_3", List.of(0.7f, 0.7f)),
        VECTOR_4(12, "VECTOR_4", List.of(-0.3f, -0.9f)),
        VECTOR_5(13, "VECTOR_5", List.of(1.2f, 0.8f));

        private final int id;
        private final String text;
        private final List<Float> vectors;

        TestData(int id, String text, List<Float> vectors) {
            this.id = id;
            this.text = text;
            this.vectors = vectors;
        }

        public int getId() {
            return id;
        }

        public List<Float> getVectors() {
            return vectors;
        }

        public String getText() {
            return text;
        }
    }
}
