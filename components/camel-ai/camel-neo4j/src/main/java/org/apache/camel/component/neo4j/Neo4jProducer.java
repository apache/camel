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
package org.apache.camel.component.neo4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.neo4j.driver.summary.ResultSummary;

import static org.apache.camel.component.neo4j.Neo4Operation.RETRIEVE_NODES;
import static org.apache.camel.component.neo4j.Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY;
import static org.apache.camel.component.neo4j.Neo4Operation.VECTOR_SIMILARITY_SEARCH;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.MATCH_PROPERTIES;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.QUERY_RESULT;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.QUERY_RESULT_CONTAINS_UPDATES;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.QUERY_RESULT_NODES_CREATED;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.QUERY_RESULT_NODES_DELETED;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.QUERY_RESULT_RELATIONSHIPS_CREATED;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.QUERY_RESULT_RELATIONSHIPS_DELETED;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.QUERY_RETRIEVE_LIST_NEO4J_NODES;
import static org.apache.camel.component.neo4j.Neo4jConstants.Headers.QUERY_RETRIEVE_SIZE;

public class Neo4jProducer extends DefaultProducer {

    private Driver driver;

    public Neo4jProducer(Neo4jEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public Neo4jEndpoint getEndpoint() {
        return (Neo4jEndpoint) super.getEndpoint();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        this.driver = getEndpoint().getDriver();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        final Message in = exchange.getMessage();
        final Neo4Operation operation = in.getHeader(Neo4jConstants.Headers.OPERATION, Neo4Operation.class);

        if (operation == null) {
            throw new NoSuchHeaderException("The operation is a required header", exchange, Neo4jConstants.Headers.OPERATION);
        }

        switch (operation) {
            case CREATE_NODE -> createNode(exchange);
            case DELETE_NODE -> deleteNode(exchange);
            case RETRIEVE_NODES -> retrieveNodes(exchange);
            case RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY -> retrieveNodesWithCypherQuery(exchange);
            case ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY -> writeWithCypherQuery(exchange);
            case CREATE_VECTOR_INDEX -> createVectorIndex(exchange);
            case DROP_VECTOR_INDEX -> dropVectorIndex(exchange);
            case CREATE_VECTOR -> createVector(exchange);
            case VECTOR_SIMILARITY_SEARCH -> similaritySearch(exchange);
            default -> throw new UnsupportedOperationException("Unsupported operation: " + operation.name());
        }

    }

    private void createNode(Exchange exchange) throws InvalidPayloadException {
        final String label = getEndpoint().getConfiguration().getLabel();
        ObjectHelper.notNull(label, "label");

        final String alias = getEndpoint().getConfiguration().getAlias();
        ObjectHelper.notNull(alias, "alias");

        Object body = exchange.getMessage().getBody();

        final String databaseName = getEndpoint().getName();

        var query = "";
        Map<String, Object> properties = null;

        if (body instanceof String) {
            // Case we get the object in a Json format
            query = String.format("CREATE (%s:%s %s)", alias, label, body);
        } else {
            // body should be a list of properties
            query = String.format("CREATE (%s:%s $props)", alias, label);
            properties = Map.of("props", body);
        }

        executeWriteQuery(exchange, query, properties, databaseName, Neo4Operation.CREATE_NODE);
    }

    private void retrieveNodes(Exchange exchange) throws NoSuchHeaderException {
        final String label = getEndpoint().getConfiguration().getLabel();
        ObjectHelper.notNull(label, "label");

        final String alias = getEndpoint().getConfiguration().getAlias();
        ObjectHelper.notNull(alias, "alias");

        String matchQuery = exchange.getMessage().getHeader(MATCH_PROPERTIES, String.class);
        // in this case we search all nodes
        if (matchQuery == null) {
            matchQuery = "";
        }

        final String databaseName = getEndpoint().getName();

        var query = String.format("MATCH (%s:%s %s) RETURN %s", alias, label, matchQuery, alias);

        queryRetriveNodes(exchange, databaseName, null, query, RETRIEVE_NODES);
    }

    private void retrieveNodesWithCypherQuery(Exchange exchange) throws NoSuchHeaderException {
        final String query = exchange.getMessage().getBody(String.class);
        final String databaseName = getEndpoint().getName();
        queryRetriveNodes(exchange, databaseName, null, query, RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY);
    }

    private void queryRetriveNodes(
            Exchange exchange, String databaseName, Map<String, Object> queryParams, String query, Neo4Operation operation) {
        try {

            EagerResult result;
            if (queryParams != null) {
                result = driver.executableQuery(query)
                        .withConfig(QueryConfig.builder().withDatabase(databaseName).build())
                        .withParameters(queryParams)
                        .execute();
            } else {
                result = driver.executableQuery(query)
                        .withConfig(QueryConfig.builder().withDatabase(databaseName).build())
                        .execute();
            }

            var records = result.records();
            exchange.getMessage().setHeader(QUERY_RETRIEVE_SIZE, records.size());
            exchange.getMessage().setHeader(QUERY_RETRIEVE_LIST_NEO4J_NODES, records);
            exchange.getMessage().setBody(nodeProperties(records));
        } catch (Exception error) {
            exchange.setException(new Neo4jOperationException(operation, error));
        }
    }

    private List nodeProperties(List<Record> result) {
        return result.stream()
                .map(record -> record.get(0).asNode())
                .map(node -> node.asMap())
                .collect(Collectors.toList());
    }

    private void deleteNode(Exchange exchange) throws NoSuchHeaderException {
        final String label = getEndpoint().getConfiguration().getLabel();
        ObjectHelper.notNull(label, "label");

        final String alias = getEndpoint().getConfiguration().getAlias();
        ObjectHelper.notNull(alias, "alias");

        String matchQuery = exchange.getMessage().getHeader(MATCH_PROPERTIES, String.class);
        // in this case we search all nodes
        if (matchQuery == null) {
            matchQuery = "";
        }

        final String databaseName = getEndpoint().getName();

        final String detached = getEndpoint().getConfiguration().isDetachRelationship() ? "DETACH" : "";

        var query = String.format("MATCH (%s:%s %s) %s DELETE %s", alias, label, matchQuery, detached, alias);

        executeWriteQuery(exchange, query, null, databaseName, Neo4Operation.DELETE_NODE);
    }

    private void createVectorIndex(Exchange exchange) {

        final String vectorIndexName = getEndpoint().getConfiguration().getVectorIndexName();
        ObjectHelper.notNull(vectorIndexName, "vectorIndexName");

        final String label = getEndpoint().getConfiguration().getLabel();
        ObjectHelper.notNull(label, "label");

        final String alias = getEndpoint().getConfiguration().getAlias();
        ObjectHelper.notNull(alias, "alias");

        final int dimension = getEndpoint().getConfiguration().getDimension();
        ObjectHelper.notNull(dimension, "dimension");

        final Neo4jSimilarityFunction similarityFunction = getEndpoint().getConfiguration().getSimilarityFunction();
        ObjectHelper.notNull(similarityFunction, "similarityFunction");

        final String databaseName = getEndpoint().getName();

        String query = String.format("CREATE VECTOR INDEX %s IF NOT EXISTS\n" +
                                     "FOR (%s:%s)\n" +
                                     "ON %s.embedding\n" +
                                     "OPTIONS { indexConfig: {\n" +
                                     " `vector.dimensions`: %s,\n" +
                                     " `vector.similarity_function`: 'cosine'\n" +
                                     "}}",
                vectorIndexName, alias, label, alias, dimension, similarityFunction.name());

        executeWriteQuery(exchange, query, null, databaseName, Neo4Operation.CREATE_VECTOR_INDEX);

    }

    private void dropVectorIndex(Exchange exchange) {
        final String vectorIndexName = getEndpoint().getConfiguration().getVectorIndexName();
        ObjectHelper.notNull(vectorIndexName, "vectorIndexName");
        final String databaseName = getEndpoint().getName();

        String query = String.format("DROP INDEX %s", vectorIndexName);

        executeWriteQuery(exchange, query, null, databaseName, Neo4Operation.DROP_VECTOR_INDEX);
    }

    private void createVector(Exchange exchange) {
        final String alias
                = getEndpoint().getConfiguration().getAlias() != null ? getEndpoint().getConfiguration().getAlias() : "x";

        final String label = exchange.getMessage().getHeader(Neo4jConstants.Headers.LABEL,
                () -> getEndpoint().getConfiguration().getLabel(), String.class);
        ObjectHelper.notNull(label, "label");

        final String id
                = exchange.getMessage().getHeader(Neo4jConstants.Headers.VECTOR_ID, () -> UUID.randomUUID(), String.class);

        final float[] body = exchange.getMessage().getBody(float[].class);

        final String databaseName = getEndpoint().getName();

        String query = String.format("""
                MERGE (%s:%s {id: $id})
                WITH %s
                CALL db.create.setNodeVectorProperty(%s, 'embedding', $embedding);
                """, alias, label, alias, alias);

        Map<String, Object> params = Map.of(
                "embedding", Values.value(body),
                "id", id);

        executeWriteQuery(exchange, query, params, databaseName, Neo4Operation.CREATE_VECTOR);
    }

    public void similaritySearch(Exchange exchange) {
        final String vectorIndexName = getEndpoint().getConfiguration().getVectorIndexName();
        ObjectHelper.notNull(vectorIndexName, "vectorIndexName");

        final float[] body = exchange.getMessage().getBody(float[].class);

        final double minScore = getEndpoint().getConfiguration().getMinScore();

        final double maxResults = getEndpoint().getConfiguration().getMaxResults();

        final String databaseName = getEndpoint().getName();

        String query = """
                CALL db.index.vector.queryNodes($indexName, $maxResults, $embeddingValue)
                YIELD node, score
                WHERE score >= $minScore
                RETURN *
                """;

        Map<String, Object> params = Map.of("indexName", vectorIndexName,
                "embeddingValue", body,
                "minScore", minScore,
                "maxResults", maxResults);

        queryRetriveNodes(exchange, databaseName, params, query, VECTOR_SIMILARITY_SEARCH);

    }

    private void writeWithCypherQuery(Exchange exchange) {
        final String query = exchange.getMessage().getBody(String.class);
        final String databaseName = getEndpoint().getName();

        executeWriteQuery(exchange, query, null, databaseName, Neo4Operation.ADD_OR_DELETE_NODE_WITH_CYPHER_QUERY);
    }

    private void executeWriteQuery(
            Exchange exchange, String query, Map<String, Object> properties, String databaseName, Neo4Operation operation) {
        try {
            EagerResult result;
            if (properties != null) {
                result = driver.executableQuery(query)
                        .withConfig(QueryConfig.builder().withDatabase(databaseName).build())
                        .withParameters(properties)
                        .execute();
            } else {
                result = driver.executableQuery(query)
                        .withConfig(QueryConfig.builder().withDatabase(databaseName).build())
                        .execute();
            }

            final ResultSummary summary = result.summary();

            exchange.getMessage().setHeader(QUERY_RESULT, summary.query().text());
            exchange.getMessage().setHeader(QUERY_RESULT_NODES_CREATED,
                    summary.counters().nodesCreated());
            exchange.getMessage().setHeader(QUERY_RESULT_NODES_DELETED,
                    summary.counters().nodesDeleted());
            exchange.getMessage().setHeader(QUERY_RESULT_CONTAINS_UPDATES,
                    summary.counters().containsUpdates());
            exchange.getMessage().setHeader(QUERY_RESULT_RELATIONSHIPS_CREATED,
                    summary.counters().relationshipsCreated());
            exchange.getMessage().setHeader(QUERY_RESULT_RELATIONSHIPS_DELETED,
                    summary.counters().relationshipsDeleted());
        } catch (Exception error) {
            exchange.setException(new Neo4jOperationException(operation, error));
        }
    }

}
