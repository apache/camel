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
package org.apache.camel.impl;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class FromMultipleEndpointTest extends ContextTestSupport {

    @Test
    public void testMultipleFromEndpoint() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:results");
        mock.expectedMessageCount(2);

        template.sendBody("direct:foo", "foo");
        template.sendBody("seda:bar", "bar");

        mock.assertIsSatisfied();
        List<Exchange> list = mock.getReceivedExchanges();

        Exchange exchange = list.get(0);
        Endpoint fromEndpoint = exchange.getFromEndpoint();
        assertEquals("fromEndpoint URI", "direct://foo", fromEndpoint.getEndpointUri());

        exchange = list.get(1);
        fromEndpoint = exchange.getFromEndpoint();
        assertEquals("fromEndpoint URI", "seda://bar", fromEndpoint.getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                getContext().setTracing(true);

                from("direct:foo").to("mock:results");
                from("seda:bar").to("mock:results");
            }
        };

    }
}
