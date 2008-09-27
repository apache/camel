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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class IBatisPollingDelayRouteTest extends ContextTestSupport {

    public void testSendAccountBean() throws Exception {
        createTestData();

        long start = System.currentTimeMillis();
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        assertMockEndpointsSatisfied();
        long delta = System.currentTimeMillis() - start;

        assertTrue("Should not take that long: " + delta, delta < 5000);
    }

    private void createTestData() {
        // insert test data
        Account account = new Account();
        account.setId(123);
        account.setFirstName("James");
        account.setLastName("Strachan");
        account.setEmailAddress("TryGuessing@gmail.com");
        template.sendBody("direct:start", account);
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // run this timer every 2nd second, that will select data from the database and send it to the mock endpiont
                from("timer://pollTheDatabase?delay=2000").to("ibatis:selectAllAccounts").to("mock:result");
                // END SNIPPET: e1

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
        IBatisEndpoint endpoint = resolveMandatoryEndpoint("ibatis:selectAllAccounts", IBatisEndpoint.class);
        return endpoint.getSqlClient().getDataSource().getConnection();
    }

}