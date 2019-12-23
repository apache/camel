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
package org.apache.camel.component.seda;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class SedaNoConsumerTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testInOnly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("seda:foo?timeout=1000");
            }
        });

        context.start();
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).waitTime(2000).create();

        // no problem for in only as we do not expect a reply
        template.sendBody("direct:start", "Hello World");

        assertTrue(notify.matchesWaitTime());
    }

    public void testInOut() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("seda:foo?timeout=1000");
            }
        });

        context.start();

        try {
            template.requestBody("direct:start", "Hello World");
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
        }
    }

    @Test
    public void testFailIfNoConsumer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("seda:foo?failIfNoConsumers=true");
            }
        });

        context.start();

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(SedaConsumerNotAvailableException.class, e.getCause());
        }

    }

    @Test
    public void testFailIfNoConsuemerAndMultipleConsumerSetting() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?failIfNoConsumers=true&multipleConsumers=true").to("mock:foo");
                from("seda:foo?failIfNoConsumers=true&multipleConsumers=true").to("mock:bar");
            }
        });

        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();

    }

    @Test
    public void testFailIfNoConsumesrAfterConsumersLeave() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?failIfNoConsumers=true").routeId("stopThisRoute").to("mock:foo");
            }
        });

        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo?failIfNoConsumers=true", "Hello World");

        assertMockEndpointsSatisfied();

        context.getRouteController().stopRoute("stopThisRoute");
        TimeUnit.MILLISECONDS.sleep(100);
        try {
            template.sendBody("seda:foo?failIfNoConsumers=true", "Hello World");
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(SedaConsumerNotAvailableException.class, e.getCause());
        }
    }

    @Test
    public void testFailIfNoConsumersWithValidConsumer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in").to("seda:foo?failIfNoConsumers=true");

                from("seda:foo").to("mock:foo");
            }
        });

        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");

        template.sendBody("direct:in", "Hello World");

        assertMockEndpointsSatisfied();

    }

}
