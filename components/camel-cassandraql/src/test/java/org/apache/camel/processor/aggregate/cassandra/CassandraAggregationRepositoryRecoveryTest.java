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
package org.apache.camel.processor.aggregate.cassandra;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The recover task of the Aggregate EIP must only see completed exchanges that were not confirmed: an aggregation that
 * is still open is not recovered, and a completed aggregation whose processing fails is recovered with all its
 * messages. The repository runs against an in-memory table behind a mocked {@link CqlSession}.
 */
public class CassandraAggregationRepositoryRecoveryTest extends CamelTestSupport {

    private final AtomicInteger scans = new AtomicInteger();

    @Test
    public void testOpenAggregationIsNotRecovered() throws Exception {
        CassandraAggregationRepository repository = createRepository();
        addRoute(repository, "open", 3);

        MockEndpoint mock = getMockEndpoint("mock:open");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:open", "a", "id", "group");
        template.sendBodyAndHeader("direct:open", "b", "id", "group");

        // let the recover task run a few times while the aggregation is open
        int scanned = scans.get();
        await().atMost(10, TimeUnit.SECONDS).until(() -> scans.get() >= scanned + 3);

        mock.assertIsSatisfied();
        assertEquals("a+b", repository.get(context, "group").getIn().getBody(String.class));
    }

    @Test
    public void testCompletedAggregationIsRecoveredWithAllMessages() throws Exception {
        CassandraAggregationRepository repository = createRepository();
        addRoute(repository, "completed", 3);

        MockEndpoint mock = getMockEndpoint("mock:completed");
        // the completed aggregation fails once and is then recovered with all three messages
        mock.expectedBodiesReceived("a+b+c", "a+b+c");

        template.sendBodyAndHeader("direct:completed", "a", "id", "group");
        template.sendBodyAndHeader("direct:completed", "b", "id", "group");
        template.sendBodyAndHeader("direct:completed", "c", "id", "group");

        mock.assertIsSatisfied();
        assertNull(mock.getReceivedExchanges().get(0).getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(Boolean.TRUE, mock.getReceivedExchanges().get(1).getIn().getHeader(Exchange.REDELIVERED));
        // confirmed after the successful redelivery
        await().atMost(10, TimeUnit.SECONDS).until(() -> repository.scan(context).isEmpty());
        assertTrue(repository.getKeys().isEmpty());
    }

    private CassandraAggregationRepository createRepository() {
        CassandraAggregationRepository repository
                = new CassandraAggregationRepository(inMemorySession(new ConcurrentHashMap<>())) {
                    @Override
                    public Set<String> scan(CamelContext camelContext) {
                        try {
                            return super.scan(camelContext);
                        } finally {
                            scans.incrementAndGet();
                        }
                    }
                };
        repository.setRecoveryInterval(100);
        return repository;
    }

    private void addRoute(CassandraAggregationRepository repository, String name, int completionSize) throws Exception {
        AtomicInteger failures = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + name)
                        .aggregate(header("id"), (oldExchange, newExchange) -> {
                            if (oldExchange == null) {
                                return newExchange;
                            }
                            String body = oldExchange.getIn().getBody(String.class) + "+"
                                          + newExchange.getIn().getBody(String.class);
                            oldExchange.getIn().setBody(body);
                            return oldExchange;
                        })
                        .aggregationRepository(repository)
                        .completionSize(completionSize)
                        .to("mock:" + name)
                        .process(exchange -> {
                            if (failures.getAndIncrement() == 0) {
                                throw new IllegalStateException("Forced failure after the aggregation");
                            }
                        });
            }
        });
    }

    /**
     * A session backed by a map of KEY to (KEY, EXCHANGE_ID, EXCHANGE), which runs the statements that the repository
     * prepares for the default table (primary key KEY).
     */
    private CqlSession inMemorySession(Map<String, Object[]> table) {
        CqlSession session = mock(CqlSession.class);
        Map<BoundStatement, Object[]> boundValues = new ConcurrentHashMap<>();
        Map<BoundStatement, String> boundQueries = new ConcurrentHashMap<>();
        when(session.prepare(any(SimpleStatement.class))).thenAnswer(invocation -> {
            String query = invocation.getArgument(0, SimpleStatement.class).getQuery().toUpperCase();
            PreparedStatement prepared = mock(PreparedStatement.class);
            when(prepared.bind(any(Object[].class))).thenAnswer(bind -> {
                BoundStatement bound = mock(BoundStatement.class);
                boundValues.put(bound, bind.getArguments());
                boundQueries.put(bound, query);
                return bound;
            });
            return prepared;
        });
        when(session.execute(any(BoundStatement.class))).thenAnswer(invocation -> {
            BoundStatement bound = invocation.getArgument(0);
            return execute(table, boundQueries.remove(bound), boundValues.remove(bound));
        });
        return session;
    }

    private static ResultSet execute(Map<String, Object[]> table, String query, Object[] values) {
        List<Row> rows = new ArrayList<>();
        if (query.startsWith("INSERT")) {
            table.put((String) values[0], values.clone());
        } else if (query.startsWith("DELETE") && query.contains(" IF ")) {
            table.computeIfPresent((String) values[0], (key, row) -> values[1].equals(row[1]) ? null : row);
        } else if (query.startsWith("DELETE")) {
            table.remove((String) values[0]);
        } else if (query.startsWith("SELECT") && query.contains("WHERE")) {
            Object[] row = table.get((String) values[0]);
            if (row != null) {
                rows.add(row(row));
            }
        } else if (query.startsWith("SELECT")) {
            table.values().forEach(row -> rows.add(row(row)));
        } else {
            throw new IllegalArgumentException("Unexpected statement " + query);
        }
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.one()).thenReturn(rows.isEmpty() ? null : rows.get(0));
        when(resultSet.all()).thenReturn(rows);
        return resultSet;
    }

    private static Row row(Object[] values) {
        Row row = mock(Row.class);
        when(row.getString(anyString())).thenAnswer(invocation -> switch (invocation.getArgument(0, String.class)) {
            case "KEY" -> values[0];
            case "EXCHANGE_ID" -> values[1];
            default -> throw new IllegalArgumentException(invocation.getArgument(0, String.class));
        });
        when(row.getByteBuffer("EXCHANGE")).thenAnswer(invocation -> ((ByteBuffer) values[2]).duplicate());
        return row;
    }
}
