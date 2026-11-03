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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.driver.Driver;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.ExecutableQuery;
import org.neo4j.driver.Query;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRecord;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bodies the producer takes and gives, against a fake driver that records the queries and their parameters and
 * returns the records the test sets: no Neo4j server is needed.
 */
class Neo4jProducerBodyTest extends CamelTestSupport {

    private static final String URI = "neo4j:test?driver=#fakeDriver";
    private static final String CREATE_NODE = URI + "&operation=CREATE_NODE&alias=u&label=User";
    private static final String SEARCH = URI + "&operation=VECTOR_SIMILARITY_SEARCH&vectorIndexName=idx";
    private static final String CYPHER = URI + "&operation=RETRIEVE_NODES_AND_UPDATE_WITH_CYPHER_QUERY";

    // the queries the producer ran, and their parameters
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> parameters = new CopyOnWriteArrayList<>();
    // the records that the queries return
    private volatile List<Record> records = List.of();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("fakeDriver", fakeDriver());
        return context;
    }

    @Test
    void createNodeWithoutBodyFails() {
        Exchange result = fluentTemplate.to(CREATE_NODE).request(Exchange.class);

        InvalidPayloadException e = assertInstanceOf(InvalidPayloadException.class, result.getException());
        assertTrue(e.getMessage().contains("CREATE_NODE needs the properties of the node as a Map or as JSON text"),
                e.getMessage());
        assertEquals(List.of(), queries, "nothing must be sent to Neo4j");
    }

    @Test
    void createNodeWithTheJsonNullFails() {
        Exchange result = fluentTemplate.to(CREATE_NODE).withBody("null").request(Exchange.class);

        InvalidPayloadException e = assertInstanceOf(InvalidPayloadException.class, result.getException());
        assertTrue(e.getMessage().contains("CREATE_NODE needs the properties of the node as a Map or as JSON text"),
                e.getMessage());
        assertEquals(List.of(), queries, "nothing must be sent to Neo4j");
    }

    @Test
    void createNodeWithAMapOrJsonText() {
        Map<String, Object> properties = Map.of("name", "Bob", "age", 25);
        Exchange result = fluentTemplate.to(CREATE_NODE).withBody(properties).request(Exchange.class);
        assertNull(result.getException());

        result = fluentTemplate.to(CREATE_NODE).withBody("{\"name\": \"Bob\", \"age\": 25}").request(Exchange.class);
        assertNull(result.getException());

        assertEquals(List.of("CREATE (u:User $props)", "CREATE (u:User $props)"), queries);
        assertEquals(List.of(Map.of("props", properties), Map.of("props", properties)), parameters);
    }

    @Test
    void createNodeWithTextThatIsNotJsonDoesNotPutTheBodyInTheMessage() {
        Exchange result = fluentTemplate.to(CREATE_NODE).withBody("{\"password\": \"s3cret\"").request(Exchange.class);

        Neo4jOperationException e = assertInstanceOf(Neo4jOperationException.class, result.getException());
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertFalse(e.getCause().getMessage().contains("s3cret"), e.getCause().getMessage());
        assertEquals(List.of(), queries, "nothing must be sent to Neo4j");
    }

    static Stream<Arguments> vectors() {
        return Stream.of(
                Arguments.of(List.of(0.75d, 0.65d)),
                Arguments.of(List.of(0.75f, 0.65f)),
                Arguments.of(new double[] { 0.75d, 0.65d }),
                Arguments.of((Object) new Double[] { 0.75d, 0.65d }),
                Arguments.of(new float[] { 0.75f, 0.65f }),
                Arguments.of(new Neo4jEmbedding("1", "hello", new float[] { 0.75f, 0.65f })));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void similaritySearchTakesAVectorOfNumbers(Object body) {
        Exchange result = fluentTemplate.to(SEARCH).withBody(body).request(Exchange.class);

        assertNull(result.getException(), () -> "a vector as a " + body.getClass().getName() + " must be searched");
        assertArrayEquals(new float[] { 0.75f, 0.65f }, (float[]) parameters.get(0).get("embeddingValue"));
    }

    @Test
    void similaritySearchWithTextFails() {
        Exchange result = fluentTemplate.to(SEARCH).withBody("0.75,0.65").request(Exchange.class);

        InvalidPayloadException e = assertInstanceOf(InvalidPayloadException.class, result.getException());
        assertTrue(e.getMessage().contains("VECTOR_SIMILARITY_SEARCH needs the vector as a Neo4jEmbedding, a float[], or a"
                                           + " List or an array of numbers"),
                e.getMessage());
        assertEquals(List.of(), queries, "nothing must be sent to Neo4j");
    }

    @Test
    void similaritySearchWithAListThatIsNotOfNumbersFails() {
        Exchange result = fluentTemplate.to(SEARCH).withBody(List.of("0.75", "0.65")).request(Exchange.class);

        InvalidPayloadException e = assertInstanceOf(InvalidPayloadException.class, result.getException());
        assertTrue(e.getMessage().contains("the element at index 0 is java.lang.String"), e.getMessage());
        assertFalse(e.getMessage().contains("0.75"), "the values of the body must not be in the message");
        assertEquals(List.of(), queries, "nothing must be sent to Neo4j");
    }

    @Test
    void similaritySearchGivesTheScoreOfEachNode() {
        records = List.of(
                record(List.of("node", "score"), node(Map.of("id", "1", "text", "hello")), Values.value(0.9d)),
                record(List.of("node", "score"), node(Map.of("id", "2", "text", "world")), Values.value(0.8d)));

        Exchange result = fluentTemplate.to(SEARCH).withBody(List.of(0.75d, 0.65d)).request(Exchange.class);

        assertNull(result.getException());
        assertEquals(List.of(
                Map.of("id", "1", "text", "hello", "score", 0.9d),
                Map.of("id", "2", "text", "world", "score", 0.8d)),
                result.getMessage().getBody(List.class));
        assertEquals(2, result.getMessage().getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE));
        assertEquals(records, result.getMessage().getHeader(Neo4jHeaders.QUERY_RETRIEVE_LIST_NEO4J_NODES));
    }

    @Test
    void cypherQueryWhoseFirstColumnIsANodeGivesItsProperties() {
        records = List.of(record(List.of("u"), node(Map.of("name", "Ethan", "age", 41))));

        Exchange result = fluentTemplate.to(CYPHER).withBody("MATCH (u:User {name: 'Ethan'}) SET u.age = 41 RETURN u")
                .request(Exchange.class);

        assertNull(result.getException());
        assertEquals(List.of(Map.of("name", "Ethan", "age", 41L)), result.getMessage().getBody(List.class));
    }

    @Test
    void cypherQueryWhoseFirstColumnIsNotANodeGivesTheRecords() {
        records = List.of(
                record(List.of("name", "age"), Values.value("Ethan"), Values.value(41)),
                record(List.of("name", "age"), Values.value("Diana"), Values.value(30)));

        Exchange result = fluentTemplate.to(CYPHER).withBody("MATCH (u:User) RETURN u.name AS name, u.age AS age")
                .request(Exchange.class);

        assertNull(result.getException());
        assertEquals(List.of(Map.of("name", "Ethan", "age", 41L), Map.of("name", "Diana", "age", 30L)),
                result.getMessage().getBody(List.class));
    }

    @Test
    void cypherQueryThatCountsGivesTheCount() {
        records = List.of(record(List.of("users"), Values.value(5)));

        Exchange result = fluentTemplate.to(CYPHER).withBody("MATCH (u:User) RETURN count(u) AS users")
                .request(Exchange.class);

        assertNull(result.getException());
        assertEquals(List.of(Map.of("users", 5L)), result.getMessage().getBody(List.class));
    }

    @Test
    void theHeadersHaveTheJavaTypeTheirMetadataSays() throws Exception {
        records = List.of(record(List.of("u"), node(Map.of("name", "Ethan"))));
        Exchange retrieved = fluentTemplate.to(CYPHER).withBody("MATCH (u:User) RETURN u").request(Exchange.class);
        Exchange created = fluentTemplate.to(CREATE_NODE).withBody(Map.of("name", "Bob")).request(Exchange.class);

        assertInstanceOf(Integer.class, retrieved.getMessage().getHeader(Neo4jHeaders.QUERY_RETRIEVE_SIZE));
        assertEquals("Integer", metadataOf("QUERY_RETRIEVE_SIZE").javaType());
        assertEquals(records, retrieved.getMessage().getHeader(Neo4jHeaders.QUERY_RETRIEVE_LIST_NEO4J_NODES));
        assertEquals("java.util.List<org.neo4j.driver.Record>", metadataOf("QUERY_RETRIEVE_LIST_NEO4J_NODES").javaType());
        assertTrue(metadataOf("QUERY_RETRIEVE_LIST_NEO4J_NODES").description().contains("Records of the Neo4j driver"));

        for (String counter : List.of("QUERY_RESULT_NODES_CREATED", "QUERY_RESULT_NODES_DELETED",
                "QUERY_RESULT_RELATIONSHIPS_CREATED", "QUERY_RESULT_RELATIONSHIPS_DELETED")) {
            String header = (String) Neo4jHeaders.class.getField(counter).get(null);
            assertInstanceOf(Integer.class, created.getMessage().getHeader(header), counter);
            assertEquals("Integer", metadataOf(counter).javaType(), counter);
        }
    }

    @Test
    void theUnusedLabelHeaderAndQueryOptionAreDeprecated() throws Exception {
        Field label = Neo4jHeaders.class.getField("LABEL");
        assertNotNull(label.getAnnotation(Deprecated.class));
        assertFalse(label.getAnnotation(Metadata.class).deprecationNote().isEmpty());

        Field query = Neo4jConfiguration.class.getDeclaredField("query");
        assertNotNull(query.getAnnotation(Deprecated.class));
        assertFalse(query.getAnnotation(Metadata.class).deprecationNote().isEmpty());
    }

    private static Metadata metadataOf(String constant) throws NoSuchFieldException {
        return Neo4jHeaders.class.getField(constant).getAnnotation(Metadata.class);
    }

    private static Record record(List<String> keys, Value... values) {
        return new InternalRecord(keys, List.of(values));
    }

    private static Value node(Map<String, Object> properties) {
        Map<String, Value> values = new LinkedHashMap<>();
        properties.forEach((key, value) -> values.put(key, Values.value(value)));
        return new InternalNode(1, List.of("User"), values).asValue();
    }

    private Driver fakeDriver() {
        return proxy(Driver.class, (method, args) -> {
            if ("executableQuery".equals(method.getName())) {
                return executableQuery((String) args[0]);
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private ExecutableQuery executableQuery(String query) {
        AtomicReference<ExecutableQuery> self = new AtomicReference<>();
        self.set(proxy(ExecutableQuery.class, (method, args) -> {
            switch (method.getName()) {
                case "withConfig":
                    return self.get();
                case "withParameters":
                    parameters.add((Map<String, Object>) args[0]);
                    return self.get();
                case "execute":
                    if (args == null || args.length == 0) {
                        queries.add(query);
                        return result(query, records);
                    }
                    throw new UnsupportedOperationException("execute with a collector");
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }));
        return self.get();
    }

    private static EagerResult result(String query, List<Record> records) {
        // the counters of a query that created one node
        SummaryCounters counters = proxy(SummaryCounters.class, (method, args) -> {
            if (method.getReturnType() == boolean.class) {
                return true;
            }
            return "nodesCreated".equals(method.getName()) ? 1 : 0;
        });
        ResultSummary summary = proxy(ResultSummary.class, (method, args) -> switch (method.getName()) {
            case "query" -> new Query(query);
            case "counters" -> counters;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        return new EagerResult() {
            @Override
            public List<String> keys() {
                return records.isEmpty() ? List.of() : records.get(0).keys();
            }

            @Override
            public List<Record> records() {
                return records;
            }

            @Override
            public ResultSummary summary() {
                return summary;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<Method, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> type.getSimpleName() + " (test)";
                };
            }
            return handler.apply(method, args);
        });
    }
}
