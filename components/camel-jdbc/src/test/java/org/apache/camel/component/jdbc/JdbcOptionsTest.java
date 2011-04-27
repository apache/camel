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

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class JdbcOptionsTest extends CamelTestSupport {
    private String driverClass = "org.hsqldb.jdbcDriver";
    private String url = "jdbc:hsqldb:mem:camel_jdbc";
    private String user = "sa";
    private String password = "";
    private DataSource ds;

    @SuppressWarnings("rawtypes")
    @Test
    public void testReadSize() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "select * from customer");

        mock.assertIsSatisfied();

        List list = mock.getExchanges().get(0).getIn().getBody(ArrayList.class);
        assertEquals(1, list.size());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testInsertCommit() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultTx");
        mock.expectedMessageCount(1);
        // insert 2 recs into table
        template.sendBody("direct:startTx", "insert into customer values ('cust3', 'johnsmith');insert into customer values ('cust4', 'hkesler') ");

        mock.assertIsSatisfied();

        String body = mock.getExchanges().get(0).getIn().getBody(String.class);
        assertNull(body);

        // now test to see that they were inserted and committed properly
        MockEndpoint mockTest = getMockEndpoint("mock:retrieve");
        mockTest.expectedMessageCount(1);

        template.sendBody("direct:retrieve", "select * from customer");

        mockTest.assertIsSatisfied();

        List list = mockTest.getExchanges().get(0).getIn().getBody(ArrayList.class);
        // both records were committed
        assertEquals(4, list.size());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testInsertRollback() throws Exception {
        // insert 2 records
        try{
            template.sendBody("direct:startTx", "insert into customer values ('cust3', 'johnsmith');insert into customer values ('cust3', 'hkesler')");
            fail("Should have thrown a CamelExecutionException");
        } catch (CamelExecutionException e) {
            if (!e.getCause().getMessage().contains("Violation of unique constraint")) {
                fail("Test did not throw the expected Constraint Violation Exception");
            }
        }

        // check to see that they failed by getting a rec count from table
        MockEndpoint mockTest = getMockEndpoint("mock:retrieve");
        mockTest.expectedMessageCount(1);

        template.sendBody("direct:retrieve", "select * from customer");

        mockTest.assertIsSatisfied();

        List list = mockTest.getExchanges().get(0).getIn().getBody(ArrayList.class);
        // all recs failed to insert
        assertEquals(2, list.size());
    }

    @Test
    public void testNoDataSourceInRegistry() throws Exception {
        try {
            template.sendBody("jdbc:xxx", "Hello World");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertEquals("registry entry called xxx of type javax.sql.DataSource must be specified", 
                e.getCause().getMessage());
        }
    }
    
    @Test
    public void testResettingAutoCommitOption() throws Exception {
        Connection connection = ds.getConnection();
        assertTrue(connection.getAutoCommit());
        connection.close();
        
        template.sendBody("direct:retrieve", "select * from customer");
        
        connection = ds.getConnection();
        assertTrue(connection.getAutoCommit());
        connection.close();
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
                from("direct:retrieve").to("jdbc:testdb").to("mock:retrieve");
                from("direct:startTx").to("jdbc:testdb?transacted=true").to("mock:resultTx");
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        Properties connectionProperties = new Properties();
        connectionProperties.put("autoCommit", Boolean.TRUE);
        
        DriverManagerDataSource dataSource = new SingleConnectionDataSource(url, user, password, true);
        dataSource.setDriverClassName(driverClass);
        dataSource.setConnectionProperties(connectionProperties);
        ds = dataSource;

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table customer (id varchar(15) PRIMARY KEY, name varchar(10))");
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