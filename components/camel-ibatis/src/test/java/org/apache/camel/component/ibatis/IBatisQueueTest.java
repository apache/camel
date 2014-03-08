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

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class IBatisQueueTest extends IBatisTestSupport {

    protected boolean createTestData() {
        return false;
    }

    @Override
    protected String getStatement() {
        return "create table ACCOUNT ( ACC_ID INTEGER , ACC_FIRST_NAME VARCHAR(255), ACC_LAST_NAME VARCHAR(255), ACC_EMAIL VARCHAR(255), PROCESSED BOOLEAN DEFAULT false)";
    }

    @Test
    public void testConsume() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedMinimumMessageCount(2);
        
        Account account1 = new Account();
        account1.setId(1);
        account1.setFirstName("Bob");
        account1.setLastName("Denver");
        account1.setEmailAddress("TryGuessingGilligan@gmail.com");

        Account account2 = new Account();
        account2.setId(2);
        account2.setFirstName("Alan");
        account2.setLastName("Hale");
        account2.setEmailAddress("TryGuessingSkipper@gmail.com");

        template.sendBody("direct:start", new Account[]{account1, account2});
        
        assertMockEndpointsSatisfied();

        // need a delay here on slower machines
        Thread.sleep(1000);
        
        // now lets poll that the account has been inserted
        List<?> body = template.requestBody("ibatis:selectProcessedAccounts?statementType=QueryForList", null, List.class);

        assertEquals("Wrong size: " + body, 2, body.size());
        Account actual = assertIsInstanceOf(Account.class, body.get(0));

        assertEquals("Account.getFirstName()", "Bob", actual.getFirstName());
        assertEquals("Account.getLastName()", "Denver", actual.getLastName());

        body = template.requestBody("ibatis:selectUnprocessedAccounts?statementType=QueryForList", null, List.class);
        assertEquals("Wrong size: " + body, 0, body.size());
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("ibatis:selectUnprocessedAccounts?consumer.onConsume=consumeAccount").to("mock:results");
                // END SNIPPET: e1

                from("direct:start").to("ibatis:insertAccount?statementType=Insert");
            }
        };
    }
}