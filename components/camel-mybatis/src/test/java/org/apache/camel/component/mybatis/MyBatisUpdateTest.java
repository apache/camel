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
package org.apache.camel.component.mybatis;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MyBatisUpdateTest extends MyBatisTestSupport {

    @Test
    public void testUpdate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Account account = new Account();
        account.setId(456);
        account.setFirstName("Claus");
        account.setLastName("Jensen");
        account.setEmailAddress("Other@gmail.com");

        template.sendBody("direct:start", account);

        assertMockEndpointsSatisfied();

        // there should be 2 rows now
        Integer rows = template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
        assertEquals("There should be 2 rows", 2, rows.intValue());

        Account claus = template.requestBody("mybatis:selectAccountById?statementType=SelectOne", 456, Account.class);
        assertEquals("Claus", claus.getFirstName());
        assertEquals("Jensen", claus.getLastName());
        assertEquals("Other@gmail.com", claus.getEmailAddress());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .to("mybatis:updateAccount?statementType=Update")
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}
