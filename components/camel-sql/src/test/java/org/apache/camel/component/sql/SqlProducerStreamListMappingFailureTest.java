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
package org.apache.camel.component.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * With {@code outputType=StreamList} and {@code outputClass}, each row is mapped to the output class when the route
 * reads it. When a row cannot be mapped, the iterator must not look finished: a split that is redelivered over it must
 * fail again, not succeed without that row and the rows after it.
 */
public class SqlProducerStreamListMappingFailureTest extends CamelTestSupport {

    private static final String QUERY = "select * from item order by id";

    private EmbeddedDatabase db;

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("create table item (id integer, status varchar(10))");
        // the status of the second row is not a constant of Status, so that row cannot be mapped to Item
        jdbc.execute("insert into item values (1, 'NEW'), (2, 'LOST'), (3, 'DONE')");
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void rowThatCannotBeMappedFailsTheExchangeWhenTheSplitIsRedelivered() throws Exception {
        MockEndpoint row = getMockEndpoint("mock:row");
        row.expectedMessageCount(1);

        Exchange out = template.send("direct:splitWithRedelivery", e -> e.getMessage().setBody("go"));

        // each redelivery of the split fails too, instead of finding no more rows
        assertNotNull(out.getException(), () -> "the exchange succeeded with the rows " + routedIds(row));
        RuntimeCamelException failure = assertInstanceOf(RuntimeCamelException.class, out.getException());
        assertInstanceOf(TypeMismatchException.class, failure.getCause());
        assertEquals(2, out.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class));
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(List.of(1), routedIds(row));
    }

    private static List<Integer> routedIds(MockEndpoint row) {
        return row.getReceivedExchanges().stream().map(e -> e.getMessage().getBody(Item.class).getId()).toList();
    }

    @Test
    public void iteratorKeepsFailingOnceMappingARowHasFailed() throws Exception {
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(QUERY);
            ResultSetIterator rows = new ResultSetIterator(conn, stmt, rs, new BeanPropertyRowMapper<>(Item.class));
            assertEquals(1, ((Item) rows.next()).getId());

            // the first failure is the one of the row mapper, as before
            TypeMismatchException failure = assertThrows(TypeMismatchException.class, rows::next);

            assertSame(failure, assertThrows(RuntimeCamelException.class, rows::hasNext).getCause());
            assertSame(failure, assertThrows(RuntimeCamelException.class, rows::next).getCause());
            // the splitter closes the iterator when the split fails
            rows.close();
            assertSame(failure, assertThrows(RuntimeCamelException.class, rows::hasNext).getCause());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);

                from("direct:splitWithRedelivery")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .to("sql:" + QUERY + "?outputType=StreamList&outputClass=" + Item.class.getName())
                        .split(body()).streaming()
                            .to("mock:row")
                        .end();
            }
        };
    }

    public enum Status {
        NEW,
        DONE
    }

    public static class Item {

        private int id;
        private Status status;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }
    }
}
