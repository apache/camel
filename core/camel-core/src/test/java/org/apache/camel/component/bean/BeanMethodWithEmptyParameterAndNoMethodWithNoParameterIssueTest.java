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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CAMEL-6455
 */
public class BeanMethodWithEmptyParameterAndNoMethodWithNoParameterIssueTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myBean", new MyBean());
        jndi.bind("myOtherBean", new MyOtherBean());
        return jndi;
    }

    @Test
    public void testBean() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "Camel"),
                "Should have thrown exception");

        MethodNotFoundException cause = assertIsInstanceOf(MethodNotFoundException.class, e.getCause());
        assertEquals("doSomething()", cause.getMethodName());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOtherBean() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:other", "Camel"),
                "Should have thrown exception");

        MethodNotFoundException cause = assertIsInstanceOf(MethodNotFoundException.class, e.getCause());
        assertEquals("doSomething()", cause.getMethodName());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("bean:myBean?method=doSomething()").to("mock:result");

                from("direct:other").to("bean:myOtherBean?method=doSomething()").to("mock:result");
            }
        };
    }

    public static final class MyBean {

        public static void doSomething(Exchange exchange) {
            exchange.getIn().setHeader("foo", "bar");
        }

    }

    public static final class MyOtherBean {

        public static void doSomething(Exchange exchange) {
            exchange.getIn().setHeader("foo", "bar");
        }

        public static void doSomething(Exchange exchange, String foo, String bar) {
            exchange.getIn().setHeader(foo, bar);
        }

    }
}
