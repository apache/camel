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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MyBatisInsertListTest extends MyBatisTestSupport {

    @Test
    public void testInsertList() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        Account account1 = new Account();
        account1.setId(444);
        account1.setFirstName("Willem");
        account1.setLastName("Jiang");
        account1.setEmailAddress("Faraway@gmail.com");

        Account account2 = new Account();
        account2.setId(555);
        account2.setFirstName("Aaron");
        account2.setLastName("Daubman");
        account2.setEmailAddress("ReadTheDevList@gmail.com");

        List<Account> accountList = new ArrayList<Account>(2);

        accountList.add(account1);
        accountList.add(account2);

        // insert 2 new rows
        template.sendBody("direct:start", accountList);

        assertMockEndpointsSatisfied();

        // there should be 4 rows now
        Integer rows = template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
        assertEquals("There should be 4 rows", 4, rows.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .to("mybatis:batchInsertAccount?statementType=InsertList")
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}
