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

/**
 * @version $Revision$
 */
public class IBatisRouteTest extends ContextTestSupport {

    public void testSendAccountBean() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedMinimumMessageCount(1);

        Account account = new Account();
        account.setId(123);
        account.setFirstName("James");
        account.setLastName("Strachan");
        account.setEmailAddress("TryGuessing@gmail.com");

        template.sendBody("direct:start", account);

        assertMockEndpointsSatisfied();

        // now lets poll that the account has been inserted
        Object answer = template.sendBody("ibatis:selectAllAccounts", null);
        List body = assertIsInstanceOf(List.class, answer);

        assertEquals("Wrong size: " + body, 1, body.size());
        Account actual = assertIsInstanceOf(Account.class, body.get(0));

        assertEquals("Account.getFirstName()", "James", actual.getFirstName());
        assertEquals("Account.getLastName()", "Strachan", actual.getLastName());

        log.info("Found: " + actual);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                //Delaying the query so we will not get the "java.sql.SQLException: Table not found in statement" on the slower box
                from("timer://pollTheDatabase?delay=2000").to("ibatis:selectAllAccounts").to("mock:results");

                from("direct:start").to("ibatis:insertAccount");
            }
        };
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // lets create the database...
        Connection connection = createConnection();
        Statement statement = connection.createStatement();
        statement.execute("create table ACCOUNT ( ACC_ID INTEGER , ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255)  )");
        connection.close();
    }

    @Override
    protected void tearDown() throws Exception {
        Connection connection = createConnection();
        Statement statement = connection.createStatement();
        statement.execute("drop table ACCOUNT");
        connection.close();

        super.tearDown();
    }

    private Connection createConnection() throws Exception {
        IBatisEndpoint endpoint = resolveMandatoryEndpoint("ibatis:Account", IBatisEndpoint.class);
        return endpoint.getSqlClient().getDataSource().getConnection();
    }

}
