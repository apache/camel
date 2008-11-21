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

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class JdbcOptionsTest extends ContextTestSupport {
    private String driverClass = "org.hsqldb.jdbcDriver";
    private String url = "jdbc:hsqldb:mem:camel_jdbc";
    private String user = "sa";
    private String password = "";
    private DataSource ds;

    public void testReadSize() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "select * from customer");

        mock.assertIsSatisfied();

        List list = mock.getExchanges().get(0).getIn().getBody(ArrayList.class);
        assertEquals(1, list.size());
    }

    public void testNoDataSourceInRegistry() throws Exception {
        try {
            template.sendBody("jdbc:xxx", "Hello World");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertEquals("DataSource xxx not found in registry", e.getCause().getMessage());
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
                from("direct:start").to("jdbc:testdb?readSize=1").to("mock:result");
            }
        };
    }

    protected void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setDriverClassName(driverClass);
        ds = dataSource;

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table customer (id varchar(15), name varchar(10))");
        jdbc.execute("insert into customer values('cust1','jstrachan')");
        jdbc.execute("insert into customer values('cust2','nsandhu')");
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("drop table customer");
    }

}