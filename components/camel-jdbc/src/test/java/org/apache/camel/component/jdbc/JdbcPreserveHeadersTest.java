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

import javax.sql.DataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Unit test based on user forum request about this component
 */
public class JdbcPreserveHeadersTest extends CamelTestSupport {
    private String driverClass = "org.hsqldb.jdbcDriver";
    private String url = "jdbc:hsqldb:mem:camel_jdbc";
    private String user = "sa";
    private String password = "";
    private DataSource ds;

    @Test
    public void testPreserveHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived("foo", "bar");
        mock.assertIsSatisfied();
    }

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry reg = super.createRegistry();
        reg.bind("testdb", ds);
        return reg;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("timer://kickoff?period=10000").
                        process(new Processor() {
                            public void process(Exchange e) throws Exception {
                                e.getIn().setHeader("foo", "bar");
                            }
                        }).
                        setBody(constant("select name from customer where id = 'cust1'")).
                        to("jdbc:testdb").
                        to("mock:result");
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
        jdbc.execute("insert into customer values('cust1','jstrachan')");
        jdbc.execute("insert into customer values('cust2','nsandhu')");
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("drop table customer");
    }

}
