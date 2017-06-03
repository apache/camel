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
package org.apache.camel.component.jdbc;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JdbcProducerOutputTypeSelectOneOutputClassTest extends AbstractJdbcTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;

    @Test
    public void testOutputTypeSelectOneOutputClass() throws Exception {
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "select * from customer where ID = 'cust1'");

        assertMockEndpointsSatisfied();

        CustomerModel model = assertIsInstanceOf(CustomerModel.class, mock.getReceivedExchanges().get(0).getIn().getBody(CustomerModel.class));
        assertEquals("cust1", model.getId());
        assertEquals("jstrachan", model.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").to("jdbc:testdb?outputType=SelectOne&outputClass=org.apache.camel.component.jdbc.CustomerModel").to("mock:result");
            }
        };
    }
}
