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
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.neo4j.Neo4Operation;
import org.apache.camel.component.neo4j.Neo4j;
import org.apache.camel.component.neo4j.Neo4jTestSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Neo4jNodeIT extends Neo4jTestSupport {

    @Test
    @Order(0)
    void createNodeWithJsonObject() {

        var body = "{name: 'Alice', email: 'alice@example.com', age: 30}";
        var expectedCypherQuery = "CREATE (u1:User {name: 'Alice', email: 'alice@example.com', age: 30})";

        Exchange result = fluentTemplate.to("neo4j:neo4j?alias=u1&label=User")
                .withBodyAs(body, String.class)
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.CREATE_NODE)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.CREATE_NODE, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals(expectedCypherQuery, in.getHeader(Neo4j.Headers.QUERY_RESULT));
        assertEquals(1, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_CREATED));

    }

    @Test
    @Order(1)
    void createNodeWithProperties() {

        Map<String, Object> params = Map.of(
                "name", "Bob",
                "email", "bob@example.com",
                "age", 25);

        var expectedCypherQuery = "CREATE (u2:User $props)";

        Exchange result = fluentTemplate.to("neo4j:neo4j?alias=u2&label=User")
                .withBodyAs(params, Map.class)
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.CREATE_NODE)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.CREATE_NODE, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals(expectedCypherQuery, in.getHeader(Neo4j.Headers.QUERY_RESULT));
        assertEquals(1, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_CREATED));

    }

    @Test
    @Order(2)
    void testCreateNodeWithCypherQuery() {
        var cypherQuery = "CREATE (u3:User {name: 'Charlie', email: 'charlie@example.com', age: 35})";

        Exchange result = fluentTemplate.to("neo4j:neo4j")
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals(1, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_CREATED));
        assertEquals(0, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_DELETED));
        assertTrue(in.getHeader(Neo4j.Headers.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class));
        assertEquals(0, in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_CREATED));
        assertEquals(0, in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_DELETED));
    }

    @Test
    @Order(3)
    void testCreateMultipleNodesAndRelationshipWithCypherQuery() {
        var cypherQuery = "CREATE " +
                          "(u4:User {name: 'Diana', email: 'diana@example.com', age: 30})," +
                          "(u5:User {name: 'Ethan', email: 'ethan@example.com', age: 40})," +
                          "(u4)-[:FRIENDS_WITH]->(u5)";

        Exchange result = fluentTemplate.to("neo4j:neo4j")
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals(Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals(2, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_CREATED));
        assertEquals(0, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_DELETED));
        assertTrue(in.getHeader(Neo4j.Headers.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class));
        assertEquals(1, in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_CREATED));
        assertEquals(0, in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_DELETED));
    }

    @Test
    @Order(4)
    void testRetrieveNode() {
        Exchange result = fluentTemplate.to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .withHeader(Neo4j.Headers.MATCH_PROPERTIES, "{name: 'Alice'}")
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);
        assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals(1, in.getHeader(Neo4j.Headers.QUERY_RETRIEVE_SIZE));

        List resultList = in.getBody(List.class);
        assertNotNull(resultList);

        assertEquals(1, resultList.size());
        Map<String, Object> aliceMap = (Map<String, Object>) resultList.get(0);

        assertNotNull(aliceMap);
        assertTrue(aliceMap.containsKey("name"));
        assertEquals("Alice", aliceMap.get("name"));
        assertTrue(aliceMap.containsKey("email"));
        assertEquals("alice@example.com", aliceMap.get("email"));
        assertTrue(aliceMap.containsKey("age"));
        assertEquals(30L, aliceMap.get("age"));

    }

    @Test
    @Order(5)
    void testRetrieveAllNodes() {
        Exchange result = fluentTemplate.to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);
        assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals(5, in.getHeader(Neo4j.Headers.QUERY_RETRIEVE_SIZE));

        List resultList = in.getBody(List.class);
        assertNotNull(resultList);

        assertEquals(5, resultList.size());
    }

    @Test
    @Order(6)
    void testDeleteNode() {
        // delete node
        Exchange result = fluentTemplate.to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.DELETE_NODE)
                .withHeader(Neo4j.Headers.MATCH_PROPERTIES, "{name: 'Alice'}")
                .request(Exchange.class);

        assertNotNull(result);
        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals("Make sure we excuted the DELETE_NODE operation", Neo4Operation.DELETE_NODE,
                in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals("No created node expected", 0, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_CREATED));
        assertEquals("1 deleted node expected", 1, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_DELETED));
        assertTrue("Delete node operation is considered as an update to the database",
                in.getHeader(Neo4j.Headers.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class));
        assertEquals("No relationship between nodes expected", 0,
                in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_CREATED));
        assertEquals("No deleted relationships expected", 0, in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_DELETED));

        // query to check we can't find Alice anymore

        result = fluentTemplate.to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .withHeader(Neo4j.Headers.MATCH_PROPERTIES, "{name: 'Alice'}")
                .request(Exchange.class);

        assertNotNull(result);

        in = result.getMessage();
        assertNotNull(in);
        assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals("The node should be deleted from the database, so no result expected", 0,
                in.getHeader(Neo4j.Headers.QUERY_RETRIEVE_SIZE));
    }

    @Test
    @Order(7)
    void testDeleteNodeWithExistingRelationship() {
        // try to delete user named Diana and this should fail as Diana has a relationship with Ethan
        Exchange result = fluentTemplate.to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.DELETE_NODE)
                .withHeader(Neo4j.Headers.MATCH_PROPERTIES, "{name: 'Diana'}")
                .request(Exchange.class);

        assertNotNull(result);

        assertNotNull(
                "Diana can't be deleted because of the existing relationship between Diana and Ethan created in previous testCreateMultipleNodesAndRelationshipWithCypherQuery test ",
                result.getException());

        // delete the Diana by detaching its relationship with Ethan - detachRelationship=true
        result = fluentTemplate.to("neo4j:neo4j?alias=u&label=User&detachRelationship=true")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.DELETE_NODE)
                .withHeader(Neo4j.Headers.MATCH_PROPERTIES, "{name: 'Diana'}")
                .request(Exchange.class);
        assertNotNull(result);
        assertNull("No exception anymore when deleting relationship at same time", result.getException());

        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals("Make sure we excuted the DELETE_NODE operation", Neo4Operation.DELETE_NODE,
                in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals("No created node expected", 0, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_CREATED));
        assertEquals("1 deleted node expected", 1, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_DELETED));
        assertTrue("Delete node operation is considered as an update to the database",
                in.getHeader(Neo4j.Headers.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class));
        assertEquals("No relationship between nodes expected", 0,
                in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_CREATED));
        assertEquals(
                "The relationships between Diana and Ethan created testCreateMultipleNodesAndRelationshipWithCypherQuery test is expected to be deleted when Diana is deleted from the database",
                1, in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_DELETED));

        // query to check we can't find Diana anymore

        result = fluentTemplate.to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .withHeader(Neo4j.Headers.MATCH_PROPERTIES, "{name: 'Diana'}")
                .request(Exchange.class);

        assertNotNull(result);

        in = result.getMessage();
        assertNotNull(in);
        assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals("The node should be deleted from the database, so no result expected", 0,
                in.getHeader(Neo4j.Headers.QUERY_RETRIEVE_SIZE));

    }

    @Test
    @Order(8)
    void testDeleteNodeWithCypherQuery() {
        var cypherQuery = "MATCH (u:User {name: 'Bob'}) DELETE u";

        Exchange result = fluentTemplate.to("neo4j:neo4j")
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        assertNotNull(result);
        Message in = result.getMessage();
        assertNotNull(in);

        assertEquals("Make sure we excuted the DELETE_NODE operation", Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY,
                in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals("No created node expected", 0, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_CREATED));
        assertEquals("1 deleted node expected", 1, in.getHeader(Neo4j.Headers.QUERY_RESULT_NODES_DELETED));
        assertTrue("Delete node operation is considered as an update to the database",
                in.getHeader(Neo4j.Headers.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class));
        assertEquals("No relationship between nodes expected", 0,
                in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_CREATED));
        assertEquals("No deleted relationships expected", 0, in.getHeader(Neo4j.Headers.QUERY_RESULT_RELATIONSHIPS_DELETED));

        // query to check we can't find Bob anymore

        result = fluentTemplate.to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .withHeader(Neo4j.Headers.MATCH_PROPERTIES, "{name: 'Bob'}")
                .request(Exchange.class);

        assertNotNull(result);

        in = result.getMessage();
        assertNotNull(in);
        assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals("The node should be deleted from the database, so no result expected", 0,
                in.getHeader(Neo4j.Headers.QUERY_RETRIEVE_SIZE));
    }

    @Test
    @Order(9)
    void testUpdateWithCypherQuery() {

        // update Ethan -- set age to 41 instead of 40
        var cypherQuery = "MATCH " +
                          "(u:User {name: 'Ethan'})" +
                          "SET u.age=41 " +
                          "RETURN u";

        Exchange result = fluentTemplate.to("neo4j:neo4j")
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4j.Headers.OPERATION, Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        assertNotNull(result);

        Message in = result.getMessage();
        assertNotNull(in);
        assertEquals(Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY, in.getHeader(Neo4j.Headers.OPERATION));
        assertEquals(1, in.getHeader(Neo4j.Headers.QUERY_RETRIEVE_SIZE));

        List resultList = in.getBody(List.class);
        assertNotNull(resultList);

        assertEquals(1, resultList.size());
        Map<String, Object> aliceMap = (Map<String, Object>) resultList.get(0);

        assertNotNull(aliceMap);
        assertTrue(aliceMap.containsKey("name"));
        assertEquals("Ethan", aliceMap.get("name"));
        assertTrue(aliceMap.containsKey("email"));
        assertEquals("ethan@example.com", aliceMap.get("email"));
        assertTrue(aliceMap.containsKey("age"));
        assertEquals("The new age 41 is expected as value", 41L, aliceMap.get("age"));

    }

}
