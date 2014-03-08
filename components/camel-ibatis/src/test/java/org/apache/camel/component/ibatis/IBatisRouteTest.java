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

public class IBatisRouteTest extends IBatisTestSupport {
    
    @Override
    protected boolean createTestData() {
        return false;
    }

    @Test
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
        List<?> body = template.requestBody("ibatis:selectAllAccounts?statementType=QueryForList", null, List.class);

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
                from("timer://pollTheDatabase?delay=2000").to("ibatis:selectAllAccounts?statementType=QueryForList").to("mock:results");

                from("direct:start").to("ibatis:insertAccount?statementType=Insert");
            }
        };
    }
}
