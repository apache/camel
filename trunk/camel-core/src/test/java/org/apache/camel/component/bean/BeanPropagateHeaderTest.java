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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version 
 */
public class BeanPropagateHeaderTest extends ContextTestSupport {

    public void testBeanInOnlyPropagateHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Order OK for id: 123");
        mock.expectedHeaderReceived("foo", "bar");

        String out = template.requestBody("direct:start", "123", String.class);
        assertEquals("OK", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("order", new MyOrderService());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setHeader("foo", constant("bar"))
                        .convertBodyTo(Integer.class)
                        .to("bean:order")
                        .inOnly("seda:foo")
                        .transform(constant("OK"));

                from("seda:foo").to("mock:result");
            }
        };
    }

    public static class MyOrderService {

        public String confirmOrder(int id) {
            return "Order OK for id: " + id;
        }
    }

}
