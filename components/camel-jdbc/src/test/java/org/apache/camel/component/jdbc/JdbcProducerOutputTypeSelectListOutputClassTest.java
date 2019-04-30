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
package org.apache.camel.component.jdbc;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JdbcProducerOutputTypeSelectListOutputClassTest extends AbstractJdbcTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void testOutputTypeSelectListOutputClass() throws Exception {
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "select * from customer order by ID");

        assertMockEndpointsSatisfied();

        List list = assertIsInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody(List.class));
        assertNotNull(list);
        assertEquals(3, list.size());

        CustomerModel cust1 = (CustomerModel) list.get(0);
        assertEquals("cust1", cust1.getId());
        assertEquals("jstrachan", cust1.getName());
        CustomerModel cust2 = (CustomerModel) list.get(1);
        assertEquals("cust2", cust2.getId());
        assertEquals("nsandhu", cust2.getName());
        CustomerModel cust3 = (CustomerModel) list.get(2);
        assertEquals("cust3", cust3.getId());
        assertEquals("willem", cust3.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").to("jdbc:testdb?outputType=SelectList&outputClass=org.apache.camel.component.jdbc.CustomerModel").to("mock:result");
            }
        };
    }
}
