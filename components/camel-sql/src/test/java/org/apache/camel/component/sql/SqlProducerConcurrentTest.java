/**
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * @version 
 */
public class SqlProducerConcurrentTest extends CamelTestSupport {
    protected String driverClass = "org.hsqldb.jdbcDriver";
    protected String url = "jdbc:hsqldb:mem:camel_jdbc";
    protected String user = "sa";
    protected String password = "";
    private DataSource ds;
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        Map<Integer, Future> responses = new ConcurrentHashMap<Integer, Future>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future out = executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    int id = index % 3;
                    return template.requestBody("direct:simple", "" + id);
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied();

        assertEquals(files, responses.size());

        for (int i = 0; i < files; i++) {
            List rows = (List) responses.get(i).get();
            Map columns = (Map) rows.get(0);
            if (i % 3 == 0) {
                assertEquals("Camel", columns.get("PROJECT"));
            } else if (i % 3 == 1) {
                assertEquals("AMQ", columns.get("PROJECT"));
            } else {
                assertEquals("Linux", columns.get("PROJECT"));
            }
        }
        executor.shutdownNow();
    }

    @Before
    public void setUp() throws Exception {
        Class.forName(driverClass);
        super.setUp();

        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("create table projects (id integer primary key,"
                             + "project varchar(10), license varchar(5))");
        jdbcTemplate.execute("insert into projects values (0, 'Camel', 'ASF')");
        jdbcTemplate.execute("insert into projects values (1, 'AMQ', 'ASF')");
        jdbcTemplate.execute("insert into projects values (2, 'Linux', 'XXX')");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("drop table projects");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                ds = new SingleConnectionDataSource(url, user, password, true);
                getContext().getComponent("sql", SqlComponent.class).setDataSource(ds);

                from("direct:simple").to("sql:select * from projects where id = # order by id").to("mock:result");
            }
        };
    }

}