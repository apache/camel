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

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JdbcOptionsTest extends AbstractJdbcTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @SuppressWarnings("rawtypes")
    @Test
    public void testReadSize() throws Exception {
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "select * from customer");

        assertMockEndpointsSatisfied();

        List list = mock.getExchanges().get(0).getIn().getBody(ArrayList.class);
        assertEquals(1, list.size());
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testInsertCommit() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultTx");
        mock.expectedMessageCount(2);
        // insert 2 recs into table
        template.sendBody("direct:startTx", "insert into customer values ('cust4', 'johnsmith')");
        template.sendBody("direct:startTx", "insert into customer values ('cust5', 'hkesler')");

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
        assertEquals(5, list.size());
    }

    @Test
    public void testNoDataSourceInRegistry() throws Exception {
        try {
            template.sendBody("jdbc:xxx", "Hello World");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertEquals("No bean could be found in the registry for: xxx of type: javax.sql.DataSource",
                e.getCause().getMessage());
        }
    }
    
    @Test
    public void testResettingAutoCommitOption() throws Exception {
        Connection connection = db.getConnection();
        assertTrue(connection.getAutoCommit());
        connection.close();
        
        template.sendBody("direct:retrieve", "select * from customer");
        
        connection = db.getConnection();
        assertTrue(connection.getAutoCommit());
        connection.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").to("jdbc:testdb?readSize=1").to("mock:result");
                from("direct:retrieve").to("jdbc:testdb").to("mock:retrieve");
                from("direct:startTx").to("jdbc:testdb?transacted=true").to("mock:resultTx");
            }
        };
    }
}