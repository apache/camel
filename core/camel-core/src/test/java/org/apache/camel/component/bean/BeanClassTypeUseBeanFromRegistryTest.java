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
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class BeanClassTypeUseBeanFromRegistryTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOneInstanceInRegistry() throws Exception {
        context.getRegistry().bind("foo", new MyFooBean());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(FooService.class).to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");

        template.sendBody("direct:start", "Camel");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTwoInstancesInRegistry() throws Exception {
        context.getRegistry().bind("foo", new MyFooBean());
        context.getRegistry().bind("bar", new MyFooBean());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(FooService.class).to("mock:result");
            }
        });

        FailedToCreateRouteException e = assertThrows(FailedToCreateRouteException.class,
                () -> context.start(),
                "Should throw exception");
        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    public void testZeroInstancesInRegistry() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(FooService.class).to("mock:result");
            }
        });

        FailedToCreateRouteException e = assertThrows(FailedToCreateRouteException.class,
                () -> context.start(),
                "Should throw exception");
        assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
    }

}
