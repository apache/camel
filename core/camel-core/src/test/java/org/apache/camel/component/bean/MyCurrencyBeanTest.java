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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class MyCurrencyBeanTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myCurrencyBean", new MyCurrencyBean());
        return jndi;
    }

    @Test
    public void testDisplay() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Currency is $");

        template.sendBody("direct:start", new MyCurrency("$"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDisplayPrice() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Price is $123");

        template.sendBodyAndHeader("direct:price", new MyCurrency("$"), "price", 123);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("bean:myCurrencyBean?method=display( ${body} )").to("mock:result");

                from("direct:price").to("bean:myCurrencyBean?method=displayPrice( ${body}, ${header.price} )").to("mock:result");
            }
        };
    }
}
