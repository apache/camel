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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MyBatisRouteEmptyResultSetTest extends MyBatisTestSupport {

    @Test
    public void testRouteEmptyResultSet() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();

        // should be an empty list
        assertEquals("Should be an empty list", 0, endpoint.getReceivedExchanges().get(0).getIn().getBody(ArrayList.class).size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("mybatis:selectAllAccounts?consumer.useIterator=false&consumer.routeEmptyResultSet=true").to("mock:results");
            }
        };
    }

    @Override
    protected boolean createTestData() {
        // no test data so an empty resultset
        return false;
    }

}
