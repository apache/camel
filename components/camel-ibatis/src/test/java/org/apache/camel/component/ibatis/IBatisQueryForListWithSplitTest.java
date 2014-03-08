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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class IBatisQueryForListWithSplitTest extends IBatisTestSupport {

    @Test
    public void testQueryForList() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();

        Account james = mock.getReceivedExchanges().get(0).getIn().getBody(Account.class);
        Account claus = mock.getReceivedExchanges().get(1).getIn().getBody(Account.class);
        assertEquals("James", james.getFirstName());
        assertEquals("Claus", claus.getFirstName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .to("ibatis:selectAllAccounts?statementType=QueryForList")
                    // just use split body to split the List into individual objects
                    .split(body())
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}