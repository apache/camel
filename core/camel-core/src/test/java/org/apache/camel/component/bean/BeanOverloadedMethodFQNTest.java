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
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 */
public class BeanOverloadedMethodFQNTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOrderNoFQN() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MyBean.class, "order(MyOrder.class)").to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("OK");

        template.sendBody("direct:start", new MyOrder());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOrderNoFQNUnknown() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MyBean.class, "order(Unknown.class)").to("mock:result");

            }
        });
        context.start();

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", new MyOrder()),
                "Should have thrown an exception");

        NoTypeConversionAvailableException cause
                = assertIsInstanceOf(NoTypeConversionAvailableException.class, e.getCause().getCause());
        assertEquals("Unknown.class", cause.getValue());
    }

    @Test
    public void testOrderNoFQNBoolean() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MyBean.class, "order(MyOrder.class,Boolean.class)").to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("OK;GOLD");

        template.sendBody("direct:start", new MyOrder());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOrderFQN() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .bean(MyBean.class, "order(org.apache.camel.component.bean.BeanOverloadedMethodFQNTest$MyOrder.class)")
                        .to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("OK");

        template.sendBody("direct:start", new MyOrder());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOrderFQNUnknown() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .bean(MyBean.class, "order(org.apache.camel.component.bean.BeanOverloadedMethodFQNTest$Unknown.class)")
                        .to("mock:result");

            }
        });
        context.start();

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", new MyOrder()),
                "Should have thrown an exception");

        NoTypeConversionAvailableException cause
                = assertIsInstanceOf(NoTypeConversionAvailableException.class, e.getCause().getCause());
        assertEquals("org.apache.camel.component.bean.BeanOverloadedMethodFQNTest$Unknown.class", cause.getValue());
    }

    @Test
    public void testOrderFQNBoolean() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .bean(MyBean.class,
                                "order(org.apache.camel.component.bean.BeanOverloadedMethodFQNTest$MyOrder.class,Boolean.class)")
                        .to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("OK;GOLD");

        template.sendBody("direct:start", new MyOrder());

        assertMockEndpointsSatisfied();
    }

    public static final class MyOrder {
    }

    public static final class MyBean {

        public String order(MyOrder order) {
            return "OK";
        }

        public String order(MyOrder order, Boolean gold) {
            return "OK;GOLD";
        }
    }
}
