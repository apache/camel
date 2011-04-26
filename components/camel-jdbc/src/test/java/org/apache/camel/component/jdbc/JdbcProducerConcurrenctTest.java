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
package org.apache.camel.component.jdbc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * @version 
 */
public class JdbcProducerConcurrenctTest extends CamelTestSupport {

    protected DataSource ds;
    private String driverClass = "org.hsqldb.jdbcDriver";
    private String url = "jdbc:hsqldb:mem:camel_jdbc";
    private String user = "sa";
    private String password = "";

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    @SuppressWarnings("rawtypes")
    private void doSendMessages(int files, int poolSize) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(files);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        Map<Integer, Future<Object>> responses = new ConcurrentHashMap<Integer, Future<Object>>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future<Object> out = executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    int id = index % 2;
                    return template.requestBody("direct:start", "select * from customer where id = " + id);
                }
            });
            responses.put(index, out);
        }

        assertMockEndpointsSatisfied();

        assertEquals(files, responses.size());

        for (int i = 0; i < files; i++) {
            List rows = (List) responses.get(i).get();
            Map columns = (Map) rows.get(0);
            if (i % 2 == 0) {
                assertEquals("jstrachan", columns.get("NAME"));
            } else {
                assertEquals("nsandhu", columns.get("NAME"));
            }
        }
    }

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry reg = super.createRegistry();
        reg.bind("testdb", ds);
        return reg;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").to("jdbc:testdb").to("mock:result");
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setDriverClassName(driverClass);
        ds = dataSource;

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table customer (id varchar(15), name varchar(10))");
        jdbc.execute("insert into customer values('0','jstrachan')");
        jdbc.execute("insert into customer values('1','nsandhu')");
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("drop table customer");
    }

}
