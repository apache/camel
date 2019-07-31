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
package org.apache.camel.component.directvm;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class DirectVmNoConsumerTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testInOnly() throws Exception {
        context.getComponent("direct-vm", DirectVmComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:start").to("direct-vm:foo");
            }
        });

        context.start();

        try {
            template.sendBody("direct-vm:start", "Hello World");
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(DirectVmConsumerNotAvailableException.class, e.getCause());
        }
    }

    public void testInOut() throws Exception {
        context.getComponent("direct-vm", DirectVmComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:start").to("direct-vm:foo");
            }
        });

        context.start();

        try {
            template.requestBody("direct-vm:start", "Hello World");
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(DirectVmConsumerNotAvailableException.class, e.getCause());
        }
    }

    @Test
    public void testFailIfNoConsumerFalse() throws Exception {
        context.getComponent("direct-vm", DirectVmComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:start").to("direct-vm:foo?failIfNoConsumers=false");
            }
        });

        context.start();

        try {
            template.sendBody("direct-vm:start", "Hello World");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(DirectVmConsumerNotAvailableException.class, e.getCause());
        }
    }

    @Test
    public void testFailIfNoConsumersAfterConsumersLeave() throws Exception {
        context.getComponent("direct-vm", DirectVmComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:foo").routeId("stopThisRoute").to("mock:foo");
            }
        });

        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");

        template.sendBody("direct-vm:foo", "Hello World");

        assertMockEndpointsSatisfied();

        context.getRouteController().stopRoute("stopThisRoute");
        TimeUnit.MILLISECONDS.sleep(100);
        try {
            template.sendBody("direct-vm:foo", "Hello World");
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(DirectVmConsumerNotAvailableException.class, e.getCause());
        }
    }

    @Test
    public void testFailIfNoConsumersWithValidConsumer() throws Exception {
        context.getComponent("direct-vm", DirectVmComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:in").to("direct-vm:foo");
                from("direct-vm:foo").to("mock:foo");
            }
        });

        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");

        template.sendBody("direct-vm:in", "Hello World");

        assertMockEndpointsSatisfied();

    }

    @Test
    public void testFailIfNoConsumersFalseWithPipeline() throws Exception {
        context.getComponent("direct-vm", DirectVmComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:in").to("direct-vm:foo?failIfNoConsumers=false").to("direct-vm:bar");
                from("direct-vm:bar").to("mock:foo");
            }
        });

        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");

        template.sendBody("direct-vm:in", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConfigOnAConsumer() throws Exception {
        context.getComponent("direct-vm", DirectVmComponent.class).setBlock(false);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:foo?failIfNoConsumers=false").to("log:test");
            }
        });

        context.start();
    }

}
