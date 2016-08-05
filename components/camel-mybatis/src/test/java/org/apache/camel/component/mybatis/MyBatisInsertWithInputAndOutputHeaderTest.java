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

public class MyBatisInsertWithInputAndOutputHeaderTest extends MyBatisTestSupport {

    private static final String TEST_CASE_INPUT_HEADER_NAME = "testCaseInputHeader";
    private static final String TEST_CASE_OUTPUT_HEADER_NAME = "testCaseOutputHeader";
    private static final String RETAINED_BODY = "not an account";

    @Test
    public void testInsert() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo(RETAINED_BODY);
        mock.message(0).header(TEST_CASE_OUTPUT_HEADER_NAME).isEqualTo(1);

        Account account = new Account();
        account.setId(444);
        account.setFirstName("Willem");
        account.setLastName("Jiang");
        account.setEmailAddress("Faraway@gmail.com");

        template.sendBodyAndHeader("direct:start", RETAINED_BODY, TEST_CASE_INPUT_HEADER_NAME, account);

        assertMockEndpointsSatisfied();

        // there should be 3 rows now
        Integer rows = template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
        assertEquals("There should be 3 rows", 3, rows.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mybatis:insertAccount?statementType=Insert&inputHeader=" + TEST_CASE_INPUT_HEADER_NAME + "&outputHeader=" + TEST_CASE_OUTPUT_HEADER_NAME)
                    .to("mock:result");
            }
        };
    }

}
