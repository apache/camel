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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With outputType=StreamList the producer takes a raw connection from the DataSource so it can stay open while the
 * Exchange is routed. When the executed statement is not a query (INSERT/UPDATE/DELETE), the connection must still be
 * returned to the pool once the statement has executed.
 */
public class SqlProducerStreamListNonSelectConnectionLeakTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private ConnectionCountingDataSource dataSource;

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();
        dataSource = new ConnectionCountingDataSource(db);
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void testNonSelectStreamListDoesNotLeakConnection() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(SqlConstants.SQL_UPDATE_COUNT, 1);

        template.sendBody("direct:update", "go");

        mock.assertIsSatisfied();

        // the update was executed
        assertEquals("PROP", new JdbcTemplate(db)
                .queryForObject("select license from projects where id = 2", String.class));

        // the exchange is fully routed and completed here, so every connection
        // taken from the pool must have been closed again
        assertEquals(0, dataSource.openConnections(),
                "connection leaked by outputType=StreamList with a non-SELECT statement");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(dataSource);

                from("direct:update")
                        .to("sql:update projects set license = 'PROP' where id = 2?outputType=StreamList")
                        .to("mock:result");
            }
        };
    }

    private static final class ConnectionCountingDataSource extends DelegatingDataSource {

        private final AtomicInteger open = new AtomicInteger();

        private ConnectionCountingDataSource(DataSource target) {
            super(target);
        }

        int openConnections() {
            return open.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            return track(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return track(super.getConnection(username, password));
        }

        private Connection track(Connection connection) {
            open.incrementAndGet();
            AtomicBoolean closed = new AtomicBoolean();
            InvocationHandler handler = (proxy, method, args) -> {
                if ("close".equals(method.getName()) && closed.compareAndSet(false, true)) {
                    open.decrementAndGet();
                }
                try {
                    return method.invoke(connection, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            };
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] { Connection.class }, handler);
        }
    }
}
