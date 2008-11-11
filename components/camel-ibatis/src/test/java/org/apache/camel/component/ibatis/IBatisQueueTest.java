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
package org.apache.camel.component.ibatis;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class IBatisQueueTest extends ContextTestSupport {

    public void testConsume() throws Exception {

        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedMinimumMessageCount(2);
        
        Account account = new Account();
        account.setId(1);
        account.setFirstName("Bob");
        account.setLastName("Denver");
        account.setEmailAddress("TryGuessingGilligan@gmail.com");

        template.sendBody("direct:start", account);
        
        account = new Account();
        account.setId(2);
        account.setFirstName("Alan");
        account.setLastName("Hale");
        account.setEmailAddress("TryGuessingSkipper@gmail.com");

        template.sendBody("direct:start", account);
        
        assertMockEndpointsSatisfied();
        
        // now lets poll that the account has been inserted
        Object answer = template.sendBody("ibatis:selectProcessedAccounts", null);
        List body = assertIsInstanceOf(List.class, answer);

        assertEquals("Wrong size: " + body, 2, body.size());
        Account actual = assertIsInstanceOf(Account.class, body.get(0));

        assertEquals("Account.getFirstName()", "Bob", actual.getFirstName());
        assertEquals("Account.getLastName()", "Denver", actual.getLastName());

        answer = template.sendBody("ibatis:selectUnprocessedAccounts", null);
        
        body = assertIsInstanceOf(List.class, answer);
        assertEquals("Wrong size: " + body, 0, body.size());
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("ibatis:selectUnprocessedAccounts?consumer.onConsume=consumeAccount").to("mock:results");

                from("direct:start").to("ibatis:insertAccount");
            }
        };
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // lets create the database...
        IBatisEndpoint endpoint = resolveMandatoryEndpoint("ibatis:Account", IBatisEndpoint.class);
        Connection connection = endpoint.getSqlMapClient().getDataSource().getConnection();
        Statement statement = connection.createStatement();
        statement.execute("create table ACCOUNT ( ACC_ID INTEGER , ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255), PROCESSED BOOLEAN DEFAULT false)");
        connection.close();
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        IBatisEndpoint endpoint = resolveMandatoryEndpoint("ibatis:Account", IBatisEndpoint.class);
        Connection connection = endpoint.getSqlMapClient().getDataSource().getConnection();
        Statement statement = connection.createStatement();
        statement.execute("drop table ACCOUNT");
        connection.close();
    }
}
