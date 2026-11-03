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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.types.TypeSystem;

import static org.apache.camel.component.neo4j.Neo4Operation.RETRIEVE_NODES;
import static org.apache.camel.component.neo4j.Neo4Operation.RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY;
import static org.apache.camel.component.neo4j.Neo4Operation.VECTOR_SIMILARITY_SEARCH;
import static org.apache.camel.component.neo4j.Neo4jHeaders.MATCH_PROPERTIES;
import static org.apache.camel.component.neo4j.Neo4jHeaders.QUERY_RESULT;
import static org.apache.camel.component.neo4j.Neo4jHeaders.QUERY_RESULT_CONTAINS_UPDATES;
import static org.apache.camel.component.neo4j.Neo4jHeaders.QUERY_RESULT_NODES_CREATED;
import static org.apache.camel.component.neo4j.Neo4jHeaders.QUERY_RESULT_NODES_DELETED;
import static org.apache.camel.component.neo4j.Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_CREATED;
import static org.apache.camel.component.neo4j.Neo4jHeaders.QUERY_RESULT_RELATIONSHIPS_DELETED;
import static org.apache.camel.component.neo4j.Neo4jHeaders.QUERY_RETRIEVE_LIST_NEO4J_NODES;
import static org.apache.camel.component.neo4j.Neo4jHeaders.QUERY_RETRIEVE_SIZE;

public class Neo4jProducer extends DefaultProducer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    // the key of the score of a node in the result Maps of VECTOR_SIMILARITY_SEARCH
    static final String SCORE = "score";

    private static final String CREATE_NODE_BODY = "CREATE_NODE needs the properties of the node as a Map or as JSON text";
    private static final String VECTOR_BODY
            = "VECTOR_SIMILARITY_SEARCH needs the vector as a Neo4jEmbedding, a float[], or a List or an array of numbers";

    // Only property values are passed as bound parameters; the property name is spliced into the
    // Cypher query text, so it must be restricted to a safe identifier pattern.
    private static final Pattern VALID_PROPERTY_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

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
        // the header wins over the operation option
        final Neo4Operation operation = in.getHeader(Neo4jHeaders.OPERATION, getEndpoint().getConfiguration().getOperation(),
                Neo4Operation.class);

        if (operation == null) {
            throw new NoSuchHeaderException(
                    "The operation is required: set the operation option or the header", exchange,
                    Neo4jHeaders.OPERATION);
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

        // Always use parameterized queries to prevent Cypher injection
        var query = String.format("CREATE (%s:%s $props)", alias, label);
        Object props;

        if (body instanceof String bodyString) {
            try {
                // Convert JSON string to Map for parameterized query
                props = OBJECT_MAPPER.readValue(bodyString, MAP_TYPE_REF);
            } catch (Exception e) {
                // the body is not in the message: it holds the properties of the node, which can be sensitive
                exchange.setException(
                        new Neo4jOperationException(
                                Neo4Operation.CREATE_NODE,
                                new IllegalArgumentException("Failed to parse the body of CREATE_NODE as a JSON object", e)));
                return;
            }
            if (props == null) {
                throw new InvalidPayloadException(
                        exchange, Map.class, exchange.getMessage(), CREATE_NODE_BODY + ", and the JSON text is null");
            }
        } else if (body == null) {
            throw new InvalidPayloadException(exchange, Map.class, exchange.getMessage(), CREATE_NODE_BODY);
        } else {
            // body should be a Map or similar object
            props = body;
        }

        executeWriteQuery(exchange, query, Map.of("props", props), databaseName, Neo4Operation.CREATE_NODE);
    }

    /**
     * Validates a Neo4j property name before it is spliced into a Cypher query. Property values are passed as bound
     * parameters, but the property name is inserted into the query text, so it must be a safe identifier. Names that do
     * not match are rejected with a clear error instead of producing a malformed or unintended query.
     */
    static void validatePropertyName(String name) {
        if (name == null || !VALID_PROPERTY_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Neo4j property name: '" + name + "'. Property names must match "
                                               + VALID_PROPERTY_NAME.pattern());
        }
    }

    private void retrieveNodes(Exchange exchange) throws NoSuchHeaderException {
        final String label = getEndpoint().getConfiguration().getLabel();
        ObjectHelper.notNull(label, "label");

        final String alias = getEndpoint().getConfiguration().getAlias();
        ObjectHelper.notNull(alias, "alias");

        String matchProperties = exchange.getMessage().getHeader(MATCH_PROPERTIES, String.class);

        final String databaseName = getEndpoint().getName();

        String query;
        Map<String, Object> queryParams = null;

        if (matchProperties == null || matchProperties.isEmpty()) {
            // Search all nodes
            query = String.format("MATCH (%s:%s) RETURN %s", alias, label, alias);
        } else {
            try {
                // Convert JSON string to Map and build WHERE clause with parameters
                Map<String, Object> matchMap = OBJECT_MAPPER.readValue(matchProperties, MAP_TYPE_REF);

                if (!matchMap.isEmpty()) {
                    StringBuilder whereClause = new StringBuilder();
                    queryParams = new HashMap<>();
                    int paramIndex = 0;

                    for (Map.Entry<String, Object> entry : matchMap.entrySet()) {
                        if (paramIndex > 0) {
                            whereClause.append(" AND ");
                        }
                        validatePropertyName(entry.getKey());
                        String paramName = "param" + paramIndex;
                        whereClause.append(alias).append(".").append(entry.getKey())
                                .append(" = $").append(paramName);
                        queryParams.put(paramName, entry.getValue());
                        paramIndex++;
                    }

                    query = String.format("MATCH (%s:%s) WHERE %s RETURN %s",
                            alias, label, whereClause.toString(), alias);
                } else {
                    // Empty map, match all nodes
                    query = String.format("MATCH (%s:%s) RETURN %s", alias, label, alias);
                }
            } catch (IllegalArgumentException iae) {
                exchange.setException(new Neo4jOperationException(RETRIEVE_NODES, iae));
                return;
            } catch (Exception e) {
                exchange.setException(
                        new Neo4jOperationException(
                                RETRIEVE_NODES,
                                new IllegalArgumentException(
                                        "Failed to parse MATCH_PROPERTIES as JSON: " + matchProperties,
                                        e)));
                return;
            }
        }

        queryRetriveNodes(exchange, databaseName, queryParams, query, RETRIEVE_NODES);
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
            exchange.getMessage().setBody(
                    operation == VECTOR_SIMILARITY_SEARCH ? similarityHits(records) : nodeProperties(records));
        } catch (Exception error) {
            exchange.setException(new Neo4jOperationException(operation, error));
        }
    }

    /**
     * The properties of the node of each record, when the first column is a node. A record whose first column is not a
     * node, such as a property, a count or a path that a Cypher query returns, is given as a Map of its columns.
     */
    private static List<Map<String, Object>> nodeProperties(List<Record> records) {
        List<Map<String, Object>> answer = new ArrayList<>(records.size());
        for (Record record : records) {
            if (record.size() > 0 && record.get(0).hasType(TypeSystem.getDefault().NODE())) {
                answer.add(record.get(0).asNode().asMap());
            } else {
                answer.add(record.asMap());
            }
        }
        return answer;
    }

    /**
     * The properties of the node of each hit of a similarity search, with the score of the hit under the score key (it
     * replaces a property of the node with that name).
     */
    private static List<Map<String, Object>> similarityHits(List<Record> records) {
        List<Map<String, Object>> answer = new ArrayList<>(records.size());
        for (Record record : records) {
            Map<String, Object> hit = new LinkedHashMap<>(record.get("node").asNode().asMap());
            hit.put(SCORE, record.get(SCORE).asDouble());
            answer.add(hit);
        }
        return answer;
    }

    private void deleteNode(Exchange exchange) throws NoSuchHeaderException {
        final String label = getEndpoint().getConfiguration().getLabel();
        ObjectHelper.notNull(label, "label");

        final String alias = getEndpoint().getConfiguration().getAlias();
        ObjectHelper.notNull(alias, "alias");

        String matchProperties = exchange.getMessage().getHeader(MATCH_PROPERTIES, String.class);

        final String databaseName = getEndpoint().getName();

        final String detached = getEndpoint().getConfiguration().isDetachRelationship() ? "DETACH" : "";

        String query;
        Map<String, Object> queryParams = null;

        if (matchProperties == null || matchProperties.isEmpty()) {
            // Delete all nodes of this label
            query = String.format("MATCH (%s:%s) %s DELETE %s", alias, label, detached, alias);
        } else {
            try {
                // Convert JSON string to Map and build WHERE clause with parameters
                Map<String, Object> matchMap = OBJECT_MAPPER.readValue(matchProperties, MAP_TYPE_REF);

                if (!matchMap.isEmpty()) {
                    StringBuilder whereClause = new StringBuilder();
                    queryParams = new HashMap<>();
                    int paramIndex = 0;

                    for (Map.Entry<String, Object> entry : matchMap.entrySet()) {
                        if (paramIndex > 0) {
                            whereClause.append(" AND ");
                        }
                        validatePropertyName(entry.getKey());
                        String paramName = "param" + paramIndex;
                        whereClause.append(alias).append(".").append(entry.getKey())
                                .append(" = $").append(paramName);
                        queryParams.put(paramName, entry.getValue());
                        paramIndex++;
                    }

                    query = String.format("MATCH (%s:%s) WHERE %s %s DELETE %s",
                            alias, label, whereClause.toString(), detached, alias);
                } else {
                    // Empty map, delete all nodes of this label
                    query = String.format("MATCH (%s:%s) %s DELETE %s", alias, label, detached, alias);
                }
            } catch (IllegalArgumentException iae) {
                exchange.setException(new Neo4jOperationException(Neo4Operation.DELETE_NODE, iae));
                return;
            } catch (Exception e) {
                exchange.setException(
                        new Neo4jOperationException(
                                Neo4Operation.DELETE_NODE,
                                new IllegalArgumentException(
                                        "Failed to parse MATCH_PROPERTIES as JSON: " + matchProperties,
                                        e)));
                return;
            }
        }

        executeWriteQuery(exchange, query, queryParams, databaseName, Neo4Operation.DELETE_NODE);
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

        String query = String.format("""
                CREATE VECTOR INDEX %s IF NOT EXISTS
                FOR (%s:%s)
                ON %s.embedding
                OPTIONS { indexConfig: {
                 `vector.dimensions`: %s,
                 `vector.similarity_function`: '%s'
                }}""",
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
                = getEndpoint().getConfiguration().getAlias() != null ? getEndpoint().getConfiguration().getAlias() : "e";

        final String label
                = getEndpoint().getConfiguration().getLabel() != null
                        ? getEndpoint().getConfiguration().getLabel() : "Embedding";

        String id;
        String text;
        float[] vectors;

        final String databaseName = getEndpoint().getName();

        Object body = exchange.getMessage().getBody();

        if (body instanceof Neo4jEmbedding neo4jEmbed) {
            id = neo4jEmbed.getId();
            text = neo4jEmbed.getText();
            vectors = neo4jEmbed.getVectors();
        } else {
            id = exchange.getMessage().getHeader(Neo4jHeaders.VECTOR_ID, () -> UUID.randomUUID(), String.class);
            vectors = exchange.getMessage().getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                    float[].class);
            text = exchange.getMessage().getBody(String.class);
        }

        ObjectHelper.notNull(text, "text");
        ObjectHelper.notNull(vectors, "vectors");

        String query = String.format("""
                MERGE (%s:%s {id: $id, text: $text})
                WITH %s
                CALL db.create.setNodeVectorProperty(%s, 'embedding', $embedding);
                """, alias, label, alias, alias);

        Map<String, Object> params = Map.of(
                "embedding", Values.value(vectors),
                "id", id,
                "text", text);

        executeWriteQuery(exchange, query, params, databaseName, Neo4Operation.CREATE_VECTOR);
    }

    public void similaritySearch(Exchange exchange) throws InvalidPayloadException {
        final String vectorIndexName = getEndpoint().getConfiguration().getVectorIndexName();
        ObjectHelper.notNull(vectorIndexName, "vectorIndexName");

        final float[] vectors = vectorOf(exchange);

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
                "embeddingValue", vectors,
                "minScore", minScore,
                "maxResults", maxResults);

        queryRetriveNodes(exchange, databaseName, params, query, VECTOR_SIMILARITY_SEARCH);

    }

    /**
     * The vector of VECTOR_SIMILARITY_SEARCH: a Neo4jEmbedding, a float[], or a List (another Collection) or an array
     * of numbers, such as a List of Double. Another body is converted to a float[] by the type converters, and fails
     * when it does not convert, such as text.
     */
    private static float[] vectorOf(Exchange exchange) throws InvalidPayloadException {
        final Message message = exchange.getMessage();
        final Object body = message.getMandatoryBody();
        float[] vector;
        if (body instanceof Neo4jEmbedding neo4jEmbedding) {
            vector = neo4jEmbedding.getVectors();
        } else if (body instanceof float[] floats) {
            vector = floats;
        } else if (body instanceof Collection<?> collection) {
            vector = toFloats(exchange, collection.toArray());
        } else if (body.getClass().isArray()) {
            vector = toFloats(exchange, body);
        } else {
            vector = message.getBody(float[].class);
        }
        if (vector == null) {
            throw new InvalidPayloadException(exchange, float[].class, message, VECTOR_BODY);
        }
        return vector;
    }

    private static float[] toFloats(Exchange exchange, Object array) throws InvalidPayloadException {
        final int length = Array.getLength(array);
        final float[] vector = new float[length];
        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            if (!(element instanceof Number number)) {
                // the type of the element only: the values of the body are not put in the message
                String type = element == null ? "null" : element.getClass().getName();
                throw new InvalidPayloadException(
                        exchange, float[].class, exchange.getMessage(),
                        VECTOR_BODY + ", but the element at index " + i + " is " + type);
            }
            vector[i] = number.floatValue();
        }
        return vector;
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
