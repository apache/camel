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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MyBatisSelectOneWithInputAndOutputHeaderTest extends MyBatisTestSupport {

    private static final String TEST_CASE_INPUT_HEADER_NAME = "testCaseInputHeader";
    private static final String TEST_CASE_OUTPUT_HEADER_NAME = "testCaseOutputHeader";
    private static final int TEST_ACCOUNT_ID = 456;
    private static final int TEST_ACCOUNT_ID_BAD = 999;

    @Test
    public void testSelectOne() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo(TEST_ACCOUNT_ID_BAD);
        mock.message(0).header(TEST_CASE_OUTPUT_HEADER_NAME).isInstanceOf(Account.class);

        template.sendBodyAndHeader("direct:start", TEST_ACCOUNT_ID_BAD, TEST_CASE_INPUT_HEADER_NAME, TEST_ACCOUNT_ID);

        assertMockEndpointsSatisfied();

        Account account = mock.getReceivedExchanges().get(0).getIn().getHeader(TEST_CASE_OUTPUT_HEADER_NAME, Account.class);
        assertEquals("Claus", account.getFirstName());
    }

    @Test
    public void tesSelectOneNotFound() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo(TEST_ACCOUNT_ID);
        mock.message(0).header(TEST_CASE_OUTPUT_HEADER_NAME).isNull();

        template.sendBodyAndHeader("direct:start", TEST_ACCOUNT_ID, TEST_CASE_INPUT_HEADER_NAME, TEST_ACCOUNT_ID_BAD);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .to("mybatis:selectAccountById?statementType=SelectOne&inputHeader=" + TEST_CASE_INPUT_HEADER_NAME + "&outputHeader=" + TEST_CASE_OUTPUT_HEADER_NAME)
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
