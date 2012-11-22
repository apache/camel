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

import java.util.Arrays;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class MyBatisDeleteListTest extends MyBatisTestSupport {

    @Test
    public void testDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", Arrays.asList(123, 456));

        assertMockEndpointsSatisfied();

        // there should be 0 rows now
        Integer rows = template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
        assertEquals("There should be 0 rows", 0, rows.intValue());
    }

    @Test
    public void testDeleteNotFound() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", Arrays.asList(999));

        assertMockEndpointsSatisfied();

        // there should be 2 rows now
        Integer rows = template.requestBody("mybatis:count?statementType=SelectOne", null, Integer.class);
        assertEquals("There should be 2 rows", 2, rows.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("mybatis:batchDeleteAccountById?statementType=DeleteList")
                    .to("mock:result");
            }
        };
    }
}
