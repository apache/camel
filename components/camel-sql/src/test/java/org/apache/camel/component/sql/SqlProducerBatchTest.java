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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SqlProducerBatchTest extends CamelTestSupport {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbcTemplate;

    @Override
    public void doPreSetup() throws Exception {
        db = new EmbeddedDatabaseBuilder()
                .setName(getClass().getSimpleName())
                .setType(EmbeddedDatabaseType.H2)
                .addScript("sql/createAndPopulateDatabase.sql").build();

        jdbcTemplate = new JdbcTemplate(db);
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (db != null) {
            db.shutdown();
        }
    }

    @Test
    public void testBatchUpdateCount() {
        List<Map<String, Object>> data = Arrays.asList(project(4, "Donald"), project(5, "Goofy"));

        Exchange out = template.request("direct:batch", e -> e.getIn().setBody(data));

        assertEquals(2, out.getMessage().getHeader(SqlConstants.SQL_UPDATE_COUNT, Integer.class));
        assertEquals(5, jdbcTemplate.queryForObject("select count(*) from projects", Integer.class));
    }

    @Test
    public void testBatchUpdateCountWithSuccessNoInfo() {
        List<Map<String, Object>> data = Arrays.asList(project(4, "Donald"), project(5, "Goofy"));

        Exchange out = template.request("direct:noInfo", e -> e.getIn().setBody(data));

        // the driver returns Statement.SUCCESS_NO_INFO (-2) for each item, which must not make the total negative
        assertEquals(0, out.getMessage().getHeader(SqlConstants.SQL_UPDATE_COUNT, Integer.class));
        assertEquals(5, jdbcTemplate.queryForObject("select count(*) from projects", Integer.class));
    }

    @Test
    public void testBatchSingleMapBodyIsOneBatchItem() {
        Exchange out = template.request("direct:batch", e -> e.getIn().setBody(project(4, "Donald")));

        assertNull(out.getException());
        assertEquals(1, out.getMessage().getHeader(SqlConstants.SQL_UPDATE_COUNT, Integer.class));
        assertEquals("Donald", jdbcTemplate.queryForObject("select project from projects where id = 4", String.class));
        assertEquals(4, jdbcTemplate.queryForObject("select count(*) from projects", Integer.class));
    }

    @Test
    public void testBatchSingleMapHeaderIsOneBatchItem() {
        Exchange out = template.request("direct:body", e -> {
            e.getIn().setBody("insert into projects values (:?id, :?project, :?license)");
            e.getIn().setHeader(SqlConstants.SQL_PARAMETERS, project(4, "Donald"));
        });

        assertNull(out.getException());
        assertEquals(1, out.getMessage().getHeader(SqlConstants.SQL_UPDATE_COUNT, Integer.class));
        assertEquals("Donald", jdbcTemplate.queryForObject("select project from projects where id = 4", String.class));
        assertEquals(4, jdbcTemplate.queryForObject("select count(*) from projects", Integer.class));
    }

    private static Map<String, Object> project(int id, String project) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("project", project);
        map.put("license", "DIS");
        return map;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getComponent("sql", SqlComponent.class).setDataSource(db);
                getContext().getRegistry().bind("noInfoDataSource", new SuccessNoInfoDataSource(db));

                from("direct:batch")
                        .to("sql:insert into projects values (:#id, :#project, :#license)?batch=true");

                from("direct:noInfo")
                        .to("sql:insert into projects values (:#id, :#project, :#license)?batch=true"
                            + "&dataSource=#noInfoDataSource");

                from("direct:body")
                        .to("sql:query?batch=true&useMessageBodyForSql=true");
            }
        };
    }

    /**
     * A data source whose prepared statements return Statement.SUCCESS_NO_INFO for each batch item, as some JDBC
     * drivers do.
     */
    private static final class SuccessNoInfoDataSource extends DelegatingDataSource {

        SuccessNoInfoDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection con = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
                    new Delegate(con) {
                        @Override
                        Object invoke(Method method, Object result) {
                            if (result instanceof PreparedStatement ps) {
                                return Proxy.newProxyInstance(getClass().getClassLoader(),
                                        new Class<?>[] { PreparedStatement.class }, new Delegate(ps) {
                                            @Override
                                            Object invoke(Method method, Object result) {
                                                if ("executeBatch".equals(method.getName())) {
                                                    int[] counts = new int[((int[]) result).length];
                                                    Arrays.fill(counts, Statement.SUCCESS_NO_INFO);
                                                    return counts;
                                                }
                                                return result;
                                            }
                                        });
                            }
                            return result;
                        }
                    });
        }
    }

    private abstract static class Delegate implements InvocationHandler {

        private final Object target;

        Delegate(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                return invoke(method, method.invoke(target, args));
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        abstract Object invoke(Method method, Object result);
    }
}
