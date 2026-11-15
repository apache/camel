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
package org.apache.camel.component.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A driver closes its connection itself after a fatal error, e.g. when the database session has ended (pgjdbc on an I/O
 * error or a terminated session): {@link Connection#isClosed()} then returns true although nobody called
 * {@link Connection#close()}. A pool whose connection reports that state (Agroal delegates {@code isClosed()} to the
 * physical connection) gets the connection back only from {@code close()}, so the producer must call it anyway.
 */
public class JdbcProducerConnectionClosedByDriverTest extends AbstractJdbcTestSupport {

    @BindToRegistry("pool")
    private BrokenSessionsDataSource pool;

    @EndpointInject("mock:row")
    private MockEndpoint row;

    @Override
    public void setupResources() {
        super.setupResources();
        pool = new BrokenSessionsDataSource(db);
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:resetAutoCommit", "direct:keepAutoCommit" })
    public void connectionClosedByTheDriverGoesBackToThePool(String uri) {
        Exchange insert = template.send(uri, e -> e.getMessage().setBody("insert into customer values('cust3', 'willem')"));
        assertNull(insert.getException());
        assertEquals(0, pool.checkedOut.get(), "connections not given back to the pool");

        pool.sessionsEnded = true;

        Exchange broken = template.send(uri, e -> e.getMessage().setBody("insert into customer values('cust4', 'hadrian')"));
        SQLException error = assertInstanceOf(SQLException.class, broken.getException());
        assertEquals("08006", error.getSQLState());
        assertEquals(0, pool.checkedOut.get(), "connections not given back to the pool");
    }

    @Test
    public void connectionClosedByTheDriverWhileStreamingGoesBackToThePool() throws Exception {
        row.expectedMessageCount(1);

        Exchange select = template.send("direct:streamList", e -> e.getMessage().setBody("select * from customer"));

        MockEndpoint.assertIsSatisfied(context);
        SQLException error = assertInstanceOf(SQLException.class, select.getException().getCause());
        assertEquals("08006", error.getSQLState());
        assertEquals(0, pool.checkedOut.get(), "connections not given back to the pool");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:resetAutoCommit").to("jdbc:pool");
                from("direct:keepAutoCommit").to("jdbc:pool?resetAutoCommit=false");
                from("direct:streamList").to("jdbc:pool?outputType=StreamList")
                        .split(body()).streaming()
                            // the sessions end after the first row: fetching the next one fails
                            .process(e -> pool.sessionsEnded = true)
                            .to("mock:row")
                        .end();
            }
        };
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                JdbcProducerConnectionClosedByDriverTest.class.getClassLoader(), new Class<?>[] { type }, handler));
    }

    /**
     * Hands out connections as a pool does: {@code close()} gives the connection back, and {@code isClosed()} reports
     * the state of the physical connection. Once the sessions have ended, the next call of a connection that talks to
     * the database fails with SQLState {@code 08006}, and the driver closes the physical connection itself.
     */
    private static final class BrokenSessionsDataSource extends DelegatingDataSource {

        private final AtomicInteger checkedOut = new AtomicInteger();
        private volatile boolean sessionsEnded;

        private BrokenSessionsDataSource(DataSource database) {
            super(database);
        }

        @Override
        public Connection getConnection() throws SQLException {
            PooledConnection connection = new PooledConnection(this, super.getConnection());
            checkedOut.incrementAndGet();
            return connection.proxy;
        }
    }

    private static final class PooledConnection implements InvocationHandler {

        private final BrokenSessionsDataSource pool;
        private final Connection physical;
        private final Connection proxy;
        private boolean returned;

        private PooledConnection(BrokenSessionsDataSource pool, Connection physical) {
            this.pool = pool;
            this.physical = physical;
            this.proxy = proxy(Connection.class, this);
        }

        private void talkToDatabase() throws SQLException {
            if (physical.isClosed()) {
                throw new SQLException("This connection has been closed.", "08003");
            }
            if (pool.sessionsEnded) {
                physical.close();
                throw new SQLException("An I/O error occurred while sending to the backend.", "08006");
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "close":
                    if (!returned) {
                        returned = true;
                        pool.checkedOut.decrementAndGet();
                    }
                    physical.close();
                    return null;
                case "isClosed":
                    return physical.isClosed();
                case "commit", "rollback":
                    talkToDatabase();
                    return call(physical, method, args);
                case "createStatement", "prepareStatement":
                    Statement statement = (Statement) call(physical, method, args);
                    return new StatementHandler(this, statement, method.getReturnType()).proxy;
                default:
                    return call(physical, method, args);
            }
        }
    }

    private static final class StatementHandler implements InvocationHandler {

        private final PooledConnection connection;
        private final Statement statement;
        private final Object proxy;

        private StatementHandler(PooledConnection connection, Statement statement, Class<?> type) {
            this.connection = connection;
            this.statement = statement;
            this.proxy = proxy(type, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getConnection")) {
                return connection.proxy;
            }
            if (method.getName().startsWith("execute")) {
                connection.talkToDatabase();
            }
            Object result = call(statement, method, args);
            if (result instanceof ResultSet resultSet) {
                return proxy(ResultSet.class, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getStatement":
                            return this.proxy;
                        case "next":
                            // as if the next row needs a fetch from the database
                            connection.talkToDatabase();
                            return call(resultSet, m, a);
                        default:
                            return call(resultSet, m, a);
                    }
                });
            }
            return result;
        }
    }
}
