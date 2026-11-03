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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import javax.sql.DataSource;

import com.pgvector.PGvector;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.PGConnection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26215: the vector of UPSERT and SIMILARITY_SEARCH is a List of numbers, such as the List of Double a JSON array
 * unmarshals to, or a float or double array. A stub DataSource records the vectors the statements are given, so no
 * database is needed.
 */
class PgVectorVectorBodyTest extends CamelTestSupport {

    private static final float[] VECTOR = { 0.1f, 0.2f, 0.3f };

    private final List<float[]> vectors = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        ResultSet noRows = stub(ResultSet.class, (proxy, method, args) -> switch (method.getName()) {
            case "next" -> false;
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        PreparedStatement statement = stub(PreparedStatement.class, (proxy, method, args) -> switch (method.getName()) {
            case "setObject" -> {
                if (args[1] instanceof PGvector vector) {
                    vectors.add(vector.toArray());
                }
                yield null;
            }
            case "setString", "setInt", "close" -> null;
            case "executeUpdate" -> 1;
            case "executeQuery" -> noRows;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        PGConnection pgConnection = stub(PGConnection.class, (proxy, method, args) -> null);
        Connection connection = stub(Connection.class, (proxy, method, args) -> switch (method.getName()) {
            case "unwrap" -> pgConnection;
            case "prepareStatement" -> statement;
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        DataSource dataSource = stub(DataSource.class, (proxy, method, args) -> switch (method.getName()) {
            case "getConnection" -> connection;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        context.getComponent(PgVector.SCHEME, PgVectorComponent.class).getConfiguration().setDataSource(dataSource);
        return context;
    }

    static Stream<Named<Object>> vectorBodies() {
        return Stream.of(
                Named.of("List of Float", List.of(0.1f, 0.2f, 0.3f)),
                Named.of("List of Double", List.of(0.1d, 0.2d, 0.3d)),
                Named.of("List of BigDecimal", List.of(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"))),
                Named.of("float[]", new float[] { 0.1f, 0.2f, 0.3f }),
                Named.of("double[]", new double[] { 0.1d, 0.2d, 0.3d }),
                Named.of("Double[]", new Double[] { 0.1d, 0.2d, 0.3d }));
    }

    @ParameterizedTest
    @MethodSource("vectorBodies")
    void upsertTakesTheVector(Object body) {
        Exchange result = fluentTemplate.to("pgvector:items?action=UPSERT")
                .withHeader(PgVectorHeaders.RECORD_ID, "item-1")
                .withBody(body)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isEqualTo("item-1");
        assertThat(vectors).hasSize(1);
        assertThat(vectors.get(0)).containsExactly(VECTOR);
    }

    @ParameterizedTest
    @MethodSource("vectorBodies")
    void similaritySearchTakesTheVector(Object body) {
        Exchange result = fluentTemplate.to("pgvector:items?action=SIMILARITY_SEARCH")
                .withBody(body)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(List.class)).isEmpty();
        // the vector is bound twice: for the distance and for the order
        assertThat(vectors).hasSize(2);
        assertThat(vectors).allSatisfy(vector -> assertThat(vector).containsExactly(VECTOR));
    }

    @Test
    void aBodyThatIsNotAVectorFails() {
        Exchange text = fluentTemplate.to("pgvector:items?action=UPSERT")
                .withBody("0.1, 0.2, 0.3")
                .request(Exchange.class);
        assertThat(text.getException()).isInstanceOfSatisfying(InvalidPayloadException.class,
                e -> assertThat(e.getType()).isEqualTo(List.class))
                .hasMessageContaining("the vector must be a List of numbers, such as Float or Double, a float[] or a double[]");

        // the message names the type of the element, not its value
        Exchange listOfText = fluentTemplate.to("pgvector:items?action=SIMILARITY_SEARCH")
                .withBody(List.of("secret-0.1", "0.2"))
                .request(Exchange.class);
        assertThat(listOfText.getException()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Vector element at index 0 is a java.lang.String")
                .hasMessageNotContaining("secret");

        Exchange nullElement = fluentTemplate.to("pgvector:items?action=UPSERT")
                .withBody(Arrays.asList(0.1d, null, 0.3d))
                .request(Exchange.class);
        assertThat(nullElement.getException()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Vector element at index 1 is null");

        assertThat(vectors).isEmpty();
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type
                .cast(Proxy.newProxyInstance(PgVectorVectorBodyTest.class.getClassLoader(), new Class<?>[] { type }, handler));
    }
}
