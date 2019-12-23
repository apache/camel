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
import org.junit.Test;

public class BeanOverloadedCovariantMethodTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testHelloCovariantOverload() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MySuperBean.class, "hello").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Claus from super class");

        template.sendBody("direct:start", "Claus");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloCovariantOverloadNoNameOrParameters() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MySuperBean.class).to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello null from super class");

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloCovariantOverloadNoParameters() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MySuperBean.class, "hello").to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello null from super class");

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testHelloCovariantOverloadFromParameters() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(MySuperBean.class, "hello(String)").to("mock:result");

            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello null from super class");

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    public static class MyBean {

        public Object hello(String name) {
            return "Hello " + name + " from base class";
        }

    }

    public static class MySuperBean extends MyBean {

        @Override
        public String hello(String name) {
            return "Hello " + name + " from super class";
        }

    }

}
