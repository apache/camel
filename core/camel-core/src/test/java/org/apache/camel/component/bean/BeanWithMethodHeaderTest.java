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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BeanWithMethodHeaderTest extends ContextTestSupport {

    private MyBean bean;

    @Test
    public void testEcho() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("echo Hello World");

        template.sendBody("direct:echo", "Hello World");

        assertMockEndpointsSatisfied();
        assertNull(mock.getExchanges().get(0).getIn().getHeader(Exchange.BEAN_METHOD_NAME),
                "There should no Bean_METHOD_NAME header");
    }

    @Test
    public void testEchoWithMethodHeaderHi() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hi Hello World");
        // header should be removed after usage
        mock.message(0).header(Exchange.BEAN_METHOD_NAME).isNull();

        // header overrule endpoint configuration, so we should invoke the hi
        // method
        template.sendBodyAndHeader("direct:echo", ExchangePattern.InOut, "Hello World", Exchange.BEAN_METHOD_NAME, "hi");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMixedBeanEndpoints() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hi hi Hello World");
        // header should be removed after usage
        mock.message(0).header(Exchange.BEAN_METHOD_NAME).isNull();

        // header overrule endpoint configuration, so we should invoke the hi
        // method
        template.sendBodyAndHeader("direct:mixed", ExchangePattern.InOut, "Hello World", Exchange.BEAN_METHOD_NAME, "hi");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHi() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hi Hello World");

        template.sendBody("direct:hi", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFail() throws Exception {

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:fail", "Hello World"),
                "Should throw an exception");

        assertIsInstanceOf(AmbiguousMethodCallException.class, e.getCause());
        AmbiguousMethodCallException ace = (AmbiguousMethodCallException) e.getCause();
        assertEquals(2, ace.getMethods().size());
    }

    @Test
    public void testMethodNotExists() throws Exception {

        Exception e = assertThrows(Exception.class,
                () -> {
                    context.addRoutes(new RouteBuilder() {
                        @Override
                        public void configure() throws Exception {
                            from("direct:typo").bean("myBean", "ups").to("mock:result");
                        }
                    });
                }, "Should throw an exception");

        MethodNotFoundException mnfe = assertIsInstanceOf(MethodNotFoundException.class, e.getCause());
        assertEquals("ups", mnfe.getMethodName());
        assertSame(bean, mnfe.getBean());
    }

    @Test
    public void testMethodNotExistsOnInstance() throws Exception {
        final MyBean myBean = new MyBean();

        Exception e = assertThrows(Exception.class,
                () -> {
                    context.addRoutes(new RouteBuilder() {
                        @Override
                        public void configure() throws Exception {
                            from("direct:typo").bean(myBean, "ups").to("mock:result");
                        }
                    });
                }, "Should throw an exception");

        MethodNotFoundException mnfe = assertIsInstanceOf(MethodNotFoundException.class, e.getCause());
        assertEquals("ups", mnfe.getMethodName());
        assertSame(myBean, mnfe.getBean());
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        bean = new MyBean();
        answer.bind("myBean", bean);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:echo").bean("myBean", "echo").to("mock:result");

                from("direct:hi").bean("myBean", "hi").to("mock:result");

                from("direct:mixed").bean("myBean", "echo").bean("myBean", "hi").to("mock:result");

                from("direct:fail").bean("myBean").to("mock:result");
            }
        };
    }

    public static class MyBean {

        public String hi(String s) {
            return "hi " + s;
        }

        public String echo(String s) {
            return "echo " + s;
        }
    }

}
