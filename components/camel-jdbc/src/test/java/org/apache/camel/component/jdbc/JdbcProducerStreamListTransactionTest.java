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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With {@code outputType=StreamList} and {@code resetAutoCommit=true} (the default) the route reads the result set
 * after the producer, so the transaction of the producer must end when the exchange is done, not right after the
 * statement is executed: a driver may close the result sets at commit (the {@code CLOSE_CURSORS_AT_COMMIT}
 * holdability), as PostgreSQL does with the cursor it reads the rows from with a fetch size.
 */
public class JdbcProducerStreamListTransactionTest extends AbstractJdbcTestSupport {

    private static final String INSERT_RETURNING
            = "select * from final table (insert into customer values ('cust4', 'hadrian'))";

    @BindToRegistry("closeCursorsAtCommit")
    private CloseCursorsAtCommitDataSource closeCursorsAtCommit;

    @EndpointInject("mock:row")
    private MockEndpoint row;

    @Override
    public void setupResources() {
        super.setupResources();
        closeCursorsAtCommit = new CloseCursorsAtCommitDataSource(db);
    }

    @Test
    public void rowsAreReadBeforeTheCommit() throws Exception {
        row.expectedBodiesReceived("cust1", "cust2", "cust3");

        Exchange out = template.send("direct:closeCursorsAtCommit",
                e -> e.getMessage().setBody("select * from customer order by id"));

        assertNull(out.getException());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void commitsWhenTheExchangeIsDone() throws Exception {
        row.expectedBodiesReceived("cust4");

        Exchange out = template.send("direct:stream", e -> e.getMessage().setBody(INSERT_RETURNING));

        assertNull(out.getException());
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(4, customers());
    }

    @Test
    public void rollsBackWhenTheExchangeFails() throws Exception {
        row.expectedBodiesReceived("cust4");

        Exchange out = template.send("direct:streamThenFail", e -> e.getMessage().setBody(INSERT_RETURNING));

        assertInstanceOf(IllegalStateException.class, out.getException());
        MockEndpoint.assertIsSatisfied(context);
        assertEquals(3, customers());
    }

    private int customers() {
        return new JdbcTemplate(db).queryForObject("select count(*) from customer", Integer.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:closeCursorsAtCommit").to("jdbc:closeCursorsAtCommit?outputType=StreamList")
                        .split(body()).streaming()
                            .setBody(simple("${body[ID]}"))
                            .to("mock:row")
                        .end();

                from("direct:stream").to("jdbc:testdb?outputType=StreamList")
                        .split(body()).streaming()
                            .setBody(simple("${body[ID]}"))
                            .to("mock:row")
                        .end();

                from("direct:streamThenFail").to("jdbc:testdb?outputType=StreamList")
                        .split(body()).streaming()
                            .setBody(simple("${body[ID]}"))
                            .to("mock:row")
                        .end()
                        .throwException(new IllegalStateException("Forced"));
            }
        };
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(
                JdbcProducerStreamListTransactionTest.class.getClassLoader(), new Class<?>[] { type }, handler);
    }

    /**
     * Its connections close their result sets when they commit, as JDBC allows with the {@code CLOSE_CURSORS_AT_COMMIT}
     * holdability.
     */
    private static final class CloseCursorsAtCommitDataSource extends DelegatingDataSource {

        private CloseCursorsAtCommitDataSource(DataSource database) {
            super(database);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            List<ResultSet> resultSets = new CopyOnWriteArrayList<>();
            return (Connection) proxy(Connection.class, (proxy, method, args) -> {
                if ("commit".equals(method.getName())) {
                    for (ResultSet resultSet : resultSets) {
                        resultSet.close();
                    }
                }
                Object result = invoke(connection, method, args);
                if (result instanceof Statement statement && method.getReturnType().isInterface()) {
                    return proxy(method.getReturnType(), (p, m, a) -> {
                        Object answer = invoke(statement, m, a);
                        if (answer instanceof ResultSet resultSet) {
                            resultSets.add(resultSet);
                        }
                        return answer;
                    });
                }
                return result;
            });
        }
    }
}
