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
package org.apache.camel.component.mybatis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MyBatisUpdateListTest extends MyBatisTestSupport {

    @Test
    public void testUpdateList() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Account account1 = new Account();
        account1.setId(123);
        account1.setFirstName("James");
        account1.setLastName("Strachan");
        account1.setEmailAddress("TryGuessing@gmail.com");

        Account account2 = new Account();
        account2.setId(456);
        account2.setFirstName("Claus");
        account2.setLastName("Ibsen");
        account2.setEmailAddress("Noname@gmail.com");

        Map<String, Object> params = new HashMap<>();
        params.put("list", Arrays.asList(account1, account2));
        params.put("emailAddress", "Other@gmail.com");
        template.sendBody("direct:start", params);

        assertMockEndpointsSatisfied();

        // there should be 2 rows now
        Integer rows = template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
        assertEquals("There should be 2 rows", 2, rows.intValue());

        Account james = template.requestBody("mybatis:selectAccountById?statementType=SelectOne", 123, Account.class);
        assertEquals("James", james.getFirstName());
        assertEquals("Strachan", james.getLastName());
        assertEquals("Other@gmail.com", james.getEmailAddress());

        Account claus = template.requestBody("mybatis:selectAccountById?statementType=SelectOne", 456, Account.class);
        assertEquals("Claus", claus.getFirstName());
        assertEquals("Ibsen", claus.getLastName());
        assertEquals("Other@gmail.com", claus.getEmailAddress());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .to("mybatis:batchUpdateAccount?statementType=UpdateList")
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}
