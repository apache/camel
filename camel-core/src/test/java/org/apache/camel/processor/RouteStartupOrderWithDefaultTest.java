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
package org.apache.camel.processor;

import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision$
 */
public class RouteStartupOrderWithDefaultTest extends ContextTestSupport {

    public void testRouteStartupOrder() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // assert correct order
        DefaultCamelContext dcc = (DefaultCamelContext) context;
        List<Consumer> order = dcc.getRouteStartupOrder();

        assertEquals(5, order.size());
        assertEquals("seda://foo", order.get(0).getEndpoint().getEndpointUri());
        assertEquals("direct://start", order.get(1).getEndpoint().getEndpointUri());
        assertEquals("seda://bar", order.get(2).getEndpoint().getEndpointUri());
        assertEquals("direct://bar", order.get(3).getEndpoint().getEndpointUri());
        // the one with no startup order should be last
        assertEquals("direct://default", order.get(4).getEndpoint().getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").starupOrder(2).to("seda:foo");

                from("seda:foo").starupOrder(1).to("mock:result");

                from("direct:bar").starupOrder(9).to("seda:bar");

                from("seda:bar").starupOrder(5).to("mock:other");

                // has no startup order then it should be last
                from("direct:default").to("mock:default");
            }
        };
    }
}