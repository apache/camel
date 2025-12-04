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
import org.apache.camel.component.neo4j.Neo4jHeaders;
import org.apache.camel.component.neo4j.Neo4jTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Neo4jNodeIT extends Neo4jTestSupport {

    @Test
    @Order(0)
    void createNodeWithJsonObject() {

        var body = "{\"name\": \"Alice\", \"email\": \"alice@example.com\", \"age\": 30}";
        var expectedCypherQuery = "CREATE (u1:User $props)";

        Exchange result = fluentTemplate
                .to("neo4j:neo4j?alias=u1&label=User")
                .withBodyAs(body, String.class)
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.CREATE_NODE)
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(Neo4Operation.CREATE_NODE, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(expectedCypherQuery, in.getHeader(Neo4jHeaders.QUERY_RESULT));
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_CREATED));
    }

    @Test
    @Order(1)
    void createNodeWithProperties() {

        Map<String, Object> params = Map.of(
                "name", "Bob",
                "email", "bob@example.com",
                "age", 25);

        var expectedCypherQuery = "CREATE (u2:User $props)";

        Exchange result = fluentTemplate
                .to("neo4j:neo4j?alias=u2&label=User")
                .withBodyAs(params, Map.class)
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.CREATE_NODE)
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(Neo4Operation.CREATE_NODE, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(expectedCypherQuery, in.getHeader(Neo4jHeaders.QUERY_RESULT));
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_CREATED));
    }

    @Test
    @Order(2)
    void testCreateNodeWithCypherQuery() {
        var cypherQuery = "CREATE (u3:User {name: 'Charlie', email: 'charlie@example.com', age: 35})";

        Exchange result = fluentTemplate
                .to("neo4j:neo4j")
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(
                Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_CREATED));
        Assertions.assertEquals(0, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_DELETED));
        Assertions.assertTrue(in.getHeader(Neo4jHeaders.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class));
        Assertions.assertEquals(0, in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_CREATED));
        Assertions.assertEquals(0, in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_DELETED));
    }

    @Test
    @Order(3)
    void testCreateMultipleNodesAndRelationshipWithCypherQuery() {
        var cypherQuery = "CREATE " + "(u4:User {name: 'Diana', email: 'diana@example.com', age: 30}),"
                + "(u5:User {name: 'Ethan', email: 'ethan@example.com', age: 40}),"
                + "(u4)-[:FRIENDS_WITH]->(u5)";

        Exchange result = fluentTemplate
                .to("neo4j:neo4j")
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(
                Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(2, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_CREATED));
        Assertions.assertEquals(0, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_DELETED));
        Assertions.assertTrue(in.getHeader(Neo4jHeaders.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class));
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_CREATED));
        Assertions.assertEquals(0, in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_DELETED));
    }

    @Test
    @Order(4)
    void testRetrieveNode() {
        Exchange result = fluentTemplate
                .to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .withHeader(Neo4jHeaders.MATCH_PROPERTIES, "{\"name\": \"Alice\"}")
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);
        Assertions.assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE));

        List resultList = in.getBody(List.class);
        Assertions.assertNotNull(resultList);

        Assertions.assertEquals(1, resultList.size());
        Map<String, Object> aliceMap = (Map<String, Object>) resultList.get(0);

        Assertions.assertNotNull(aliceMap);
        Assertions.assertTrue(aliceMap.containsKey("name"));
        Assertions.assertEquals("Alice", aliceMap.get("name"));
        Assertions.assertTrue(aliceMap.containsKey("email"));
        Assertions.assertEquals("alice@example.com", aliceMap.get("email"));
        Assertions.assertTrue(aliceMap.containsKey("age"));
        Assertions.assertEquals(30L, aliceMap.get("age"));
    }

    @Test
    @Order(5)
    void testRetrieveAllNodes() {
        Exchange result = fluentTemplate
                .to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);
        Assertions.assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(5, in.getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE));

        List resultList = in.getBody(List.class);
        Assertions.assertNotNull(resultList);

        Assertions.assertEquals(5, resultList.size());
    }

    @Test
    @Order(6)
    void testDeleteNode() {
        // delete node
        Exchange result = fluentTemplate
                .to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.DELETE_NODE)
                .withHeader(Neo4jHeaders.MATCH_PROPERTIES, "{\"name\": \"Alice\"}")
                .request(Exchange.class);

        Assertions.assertNotNull(result);
        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(
                Neo4Operation.DELETE_NODE,
                in.getHeader(Neo4jHeaders.OPERATION),
                "Make sure we excuted the DELETE_NODE operation");
        Assertions.assertEquals(0, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_CREATED), "No created node expected");
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_DELETED), "1 deleted node expected");
        Assertions.assertTrue(
                in.getHeader(Neo4jHeaders.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class),
                "Delete node operation is considered as an update to the database");
        Assertions.assertEquals(
                0,
                in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_CREATED),
                "No relationship between nodes expected");
        Assertions.assertEquals(
                0, in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_DELETED), "No deleted relationships expected");

        // query to check we can't find Alice anymore

        result = fluentTemplate
                .to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .withHeader(Neo4jHeaders.MATCH_PROPERTIES, "{\"name\": \"Alice\"}")
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        in = result.getMessage();
        Assertions.assertNotNull(in);
        Assertions.assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(
                0,
                in.getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE),
                "The node should be deleted from the database, so no result expected");
    }

    @Test
    @Order(7)
    void testDeleteNodeWithExistingRelationship() {
        // try to delete user named Diana and this should fail as Diana has a relationship with Ethan
        Exchange result = fluentTemplate
                .to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.DELETE_NODE)
                .withHeader(Neo4jHeaders.MATCH_PROPERTIES, "{\"name\": \"Diana\"}")
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Assertions.assertNotNull(
                result.getException(),
                "Diana can't be deleted because of the existing relationship between Diana and Ethan created in previous testCreateMultipleNodesAndRelationshipWithCypherQuery test ");

        // delete the Diana by detaching its relationship with Ethan - detachRelationship=true
        result = fluentTemplate
                .to("neo4j:neo4j?alias=u&label=User&detachRelationship=true")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.DELETE_NODE)
                .withHeader(Neo4jHeaders.MATCH_PROPERTIES, "{\"name\": \"Diana\"}")
                .request(Exchange.class);
        Assertions.assertNotNull(result);
        Assertions.assertNull(result.getException(), "No exception anymore when deleting relationship at same time");

        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(
                Neo4Operation.DELETE_NODE,
                in.getHeader(Neo4jHeaders.OPERATION),
                "Make sure we excuted the DELETE_NODE operation");
        Assertions.assertEquals(0, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_CREATED), "No created node expected");
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_DELETED), "1 deleted node expected");
        Assertions.assertTrue(
                in.getHeader(Neo4jHeaders.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class),
                "Delete node operation is considered as an update to the database");
        Assertions.assertEquals(
                0,
                in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_CREATED),
                "No relationship between nodes expected");
        Assertions.assertEquals(
                1,
                in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_DELETED),
                "The relationships between Diana and Ethan created testCreateMultipleNodesAndRelationshipWithCypherQuery test is expected to be deleted when Diana is deleted from the database");

        // query to check we can't find Diana anymore

        result = fluentTemplate
                .to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .withHeader(Neo4jHeaders.MATCH_PROPERTIES, "{\"name\": \"Diana\"}")
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        in = result.getMessage();
        Assertions.assertNotNull(in);
        Assertions.assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(
                0,
                in.getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE),
                "The node should be deleted from the database, so no result expected");
    }

    @Test
    @Order(8)
    void testDeleteNodeWithCypherQuery() {
        var cypherQuery = "MATCH (u:User {name: 'Bob'}) DELETE u";

        Exchange result = fluentTemplate
                .to("neo4j:neo4j")
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        Assertions.assertNotNull(result);
        Message in = result.getMessage();
        Assertions.assertNotNull(in);

        Assertions.assertEquals(
                Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY,
                in.getHeader(Neo4jHeaders.OPERATION),
                "Make sure we excuted the DELETE_NODE operation");
        Assertions.assertEquals(0, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_CREATED), "No created node expected");
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RESULT_NODES_DELETED), "1 deleted node expected");
        Assertions.assertTrue(
                in.getHeader(Neo4jHeaders.QUERY_RESULT_CONTAINS_UPDATES, Boolean.class),
                "Delete node operation is considered as an update to the database");
        Assertions.assertEquals(
                0,
                in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_CREATED),
                "No relationship between nodes expected");
        Assertions.assertEquals(
                0, in.getHeader(Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_DELETED), "No deleted relationships expected");

        // query to check we can't find Bob anymore

        result = fluentTemplate
                .to("neo4j:neo4j?alias=u&label=User")
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.RETRIEVE_NODES)
                .withHeader(Neo4jHeaders.MATCH_PROPERTIES, "{\"name\": \"Bob\"}")
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        in = result.getMessage();
        Assertions.assertNotNull(in);
        Assertions.assertEquals(Neo4Operation.RETRIEVE_NODES, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(
                0,
                in.getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE),
                "The node should be deleted from the database, so no result expected");
    }

    @Test
    @Order(9)
    void testUpdateWithCypherQuery() {

        // update Ethan -- set age to 41 instead of 40
        var cypherQuery = "MATCH " + "(u:User {name: 'Ethan'})" + "SET u.age=41 " + "RETURN u";

        Exchange result = fluentTemplate
                .to("neo4j:neo4j")
                .withBodyAs(cypherQuery, String.class)
                .withHeader(Neo4jHeaders.OPERATION, Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY)
                .request(Exchange.class);

        Assertions.assertNotNull(result);

        Message in = result.getMessage();
        Assertions.assertNotNull(in);
        Assertions.assertEquals(
                Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY, in.getHeader(Neo4jHeaders.OPERATION));
        Assertions.assertEquals(1, in.getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE));

        List resultList = in.getBody(List.class);
        Assertions.assertNotNull(resultList);

        Assertions.assertEquals(1, resultList.size());
        Map<String, Object> aliceMap = (Map<String, Object>) resultList.get(0);

        Assertions.assertNotNull(aliceMap);
        Assertions.assertTrue(aliceMap.containsKey("name"));
        Assertions.assertEquals("Ethan", aliceMap.get("name"));
        Assertions.assertTrue(aliceMap.containsKey("email"));
        Assertions.assertEquals("ethan@example.com", aliceMap.get("email"));
        Assertions.assertTrue(aliceMap.containsKey("age"));
        Assertions.assertEquals(41L, aliceMap.get("age"), "The new age 41 is expected as value");
    }
}
