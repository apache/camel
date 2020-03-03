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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

/**
 *
 */
public class BeanMethodWithStringParameterTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myBean", new MyBean());
        return jndi;
    }

    @Test
    public void testBean() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello WorldHello World");

        template.sendBody("direct:start", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testBeanOther() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        template.sendBody("direct:other", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("bean:myBean?method=doSomething('Hello World', 2)").to("mock:result");

                from("direct:other").to("bean:myBean?method=doSomethingWithExchange('Bye')").to("mock:result");
            }
        };
    }

    public static final class MyBean {

        public static String doSomething(String name, int repeat) {
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < repeat; i++) {
                sb.append(name);
            }

            return sb.toString();
        }

        public static String doSomethingWithExchange(String name, Exchange exchange) {
            return name + " " + exchange.getIn().getBody(String.class);
        }

    }
}
