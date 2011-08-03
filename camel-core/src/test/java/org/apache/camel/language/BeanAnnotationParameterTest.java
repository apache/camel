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
package org.apache.camel.language;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 *
 */
public class BeanAnnotationParameterTest extends ContextTestSupport {

    public void testBeanAnnotationOne() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:one", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBeanAnnotationTwo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:two", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBeanAnnotationThree() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:three", "World");

        assertMockEndpointsSatisfied();
    }

    public void testBeanAnnotationFour() throws Exception {
        getMockEndpoint("mock:middle").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:four", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("GreetingService", new GreetingService());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:one")
                    .bean(MyBean.class)
                    .to("mock:result");

                from("direct:two")
                    .bean(MyBean.class, "callA")
                    .to("mock:result");

                from("direct:three")
                    .setHeader(Exchange.BEAN_METHOD_NAME, constant("callA"))
                    .bean(MyBean.class)
                    .to("mock:result");

                from("direct:four")
                    .bean(MyBean.class, "callA")
                    .to("mock:middle")
                    .bean(MyBean.class, "callB")
                    .to("mock:result");
            }
        };
    }

    public static final class MyBean {

        public String callA(@Bean(ref = "GreetingService", method = "english") String greeting, String body) {
            return greeting + " " + body;
        }

        public String callB() {
            return "Bye World";
        }

    }

    public static final class GreetingService {

        public String callA() {
            throw new IllegalArgumentException("Should not callA");
        }

        public String callB() {
            throw new IllegalArgumentException("Should not callB");
        }

        public String english() {
            return "Hello";
        }

        public String french() {
            return "Bonjour";
        }

        public String german() {
            return "Hallo";
        }
    }
}
