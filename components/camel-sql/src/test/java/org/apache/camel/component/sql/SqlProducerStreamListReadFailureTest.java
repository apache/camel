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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * With {@code outputType=StreamList} the rows are read while the route runs. Once reading a row has failed (e.g. the
 * connection is lost), the iterator must not look finished: a split that is redelivered over it must fail again, not
 * succeed with the rows read before the failure.
 */
public class SqlProducerStreamListReadFailureTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private FailingReadsDataSource failing;

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();
        failing = new FailingReadsDataSource(db);
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void readFailureFailsTheExchangeWhenTheSplitIsRedelivered() throws Exception {
        MockEndpoint row = getMockEndpoint("mock:row");
        row.expectedMessageCount(1);

        Exchange out = template.send("direct:splitWithRedelivery", e -> e.getMessage().setBody("go"));

        MockEndpoint.assertIsSatisfied(context);
        // each redelivery of the split fails too, instead of finding no more rows
        assertFailedReadingARow(out.getException());
        assertEquals(2, out.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class));
    }

    @Test
    public void iteratorKeepsFailingOnceReadingARowHasFailed() throws Exception {
        try (Connection conn = failing.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("select * from projects order by id");
            ResultSetIterator rows = new ResultSetIterator(conn, stmt, rs, new ColumnMapRowMapper());
            assertEquals("Camel", ((Map<?, ?>) rows.next()).get("PROJECT"));

            failing.failReads = true;
            RuntimeCamelException failure = assertThrows(RuntimeCamelException.class, rows::next);
            SQLException cause = assertFailedReadingARow(failure);

            assertSame(cause, assertThrows(RuntimeCamelException.class, rows::hasNext).getCause());
            assertSame(cause, assertThrows(RuntimeCamelException.class, rows::next).getCause());
            // the splitter closes the iterator when the split fails
            rows.close();
            assertSame(cause, assertThrows(RuntimeCamelException.class, rows::hasNext).getCause());
        }
    }

    private static SQLException assertFailedReadingARow(Exception exception) {
        RuntimeCamelException failure = assertInstanceOf(RuntimeCamelException.class, exception);
        SQLException cause = assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals("08006", cause.getSQLState());
        return cause;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(failing);

                from("direct:splitWithRedelivery")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .to("sql:select * from projects order by id?outputType=StreamList")
                        .split(body()).streaming()
                            // reading the rows fails after the first one
                            .process(e -> failing.failReads = true)
                            .to("mock:row")
                        .end();
            }
        };
    }

    /**
     * Once {@link #failReads} is set, moving a result set to its next row fails, as when fetching the next rows from
     * the database fails because the connection is lost.
     */
    private static final class FailingReadsDataSource extends DelegatingDataSource {

        private volatile boolean failReads;

        private FailingReadsDataSource(DataSource database) {
            super(database);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return (Connection) wrap(Connection.class, super.getConnection());
        }

        /** Wraps the statements and result sets that the target hands out too. */
        private Object wrap(Class<?> type, Object target) {
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
                if (failReads && target instanceof ResultSet && "next".equals(method.getName())) {
                    throw new SQLException("An I/O error occurred while sending to the backend.", "08006");
                }
                Object result;
                try {
                    result = method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if ((result instanceof Statement || result instanceof ResultSet) && method.getReturnType().isInterface()) {
                    return wrap(method.getReturnType(), result);
                }
                return result;
            });
        }
    }
}
