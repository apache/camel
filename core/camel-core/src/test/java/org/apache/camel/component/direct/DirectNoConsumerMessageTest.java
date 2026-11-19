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
package org.apache.camel.component.direct;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The message of {@link DirectConsumerNotAvailableException} says why there is no consumer: the route that consumes
 * from the endpoint and its status, or that no route does, how long the producer waited, and the options to change it.
 */
@Timeout(20)
public class DirectNoConsumerMessageTest extends ContextTestSupport {

    private static final String PREFIX = "No consumers available on endpoint: ";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("dummy-rest", new DummyRestConsumerFactory());
        return registry;
    }

    @Test
    public void testNoRouteConsumesFromTheEndpoint() throws Exception {
        addReportRoutes();

        String message = failure("direct:reportReport?timeout=100");

        assertTrue(message.startsWith(PREFIX + "direct://reportReport?timeout=100. "), message);
        assertTrue(message.contains("No route in CamelContext " + context.getName() + " consumes from it"), message);
        assertTrue(message.contains("did you mean direct://reportRoute?"), message);
        assertTrue(message.contains("The producer waited 100 millis for a consumer"), message);
        assertTrue(message.contains("block, timeout and failIfNoConsumers options"), message);
        assertFalse(message.contains("inlineRoutes"), message);
    }

    @Test
    public void testNoRouteConsumesFromTheEndpointSynchronous() throws Exception {
        addReportRoutes();

        String message = failure("direct:reportReport?timeout=100&synchronous=true");

        assertTrue(message.startsWith(PREFIX + "direct://reportReport?synchronous=true&timeout=100. "), message);
        assertTrue(message.contains("No route in CamelContext " + context.getName() + " consumes from it"), message);
        assertTrue(message.contains("did you mean direct://reportRoute?"), message);
        assertTrue(message.contains("The producer waited 100 millis for a consumer"), message);
    }

    @Test
    public void testNoCloseName() throws Exception {
        addReportRoutes();

        String message = failure("direct:invoices?timeout=100");

        assertTrue(message.contains("No route in CamelContext " + context.getName() + " consumes from it"), message);
        assertFalse(message.contains("did you mean"), message);
    }

    @Test
    public void testRouteNotStarted() throws Exception {
        addReportRoutes();

        String message = failure("direct:off?timeout=100");

        assertTrue(message.startsWith(PREFIX + "direct://off?timeout=100. "), message);
        assertTrue(message.contains("Route off in CamelContext " + context.getName() + " consumes from it and is Stopped"),
                message);
        assertTrue(message.contains("The producer waited 100 millis for a consumer"), message);
        assertFalse(message.contains("No route"), message);
    }

    @Test
    public void testRouteSuspended() throws Exception {
        addReportRoutes();
        context.getRouteController().suspendRoute("report");

        String message = failure("direct:reportRoute?timeout=100");

        assertTrue(message.contains("Route report in CamelContext " + context.getName()
                                    + " consumes from it and is Suspended"),
                message);
    }

    @Test
    public void testProducerDoesNotWait() throws Exception {
        addReportRoutes();

        String message = failure("direct:reportReport?block=false");

        assertTrue(message.contains("The producer did not wait for a consumer"), message);
        assertFalse(message.contains("waited"), message);
    }

    @Test
    public void testFromRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("direct:reportReport?timeout=100");
                from("direct:reportRoute").routeId("report").to("mock:report");
            }
        });
        context.start();

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "Hello World"));
        String message = assertIsInstanceOf(DirectConsumerNotAvailableException.class, e.getCause()).getMessage();

        assertTrue(message.startsWith(PREFIX + "direct://reportReport?timeout=100. "), message);
        assertTrue(message.contains("did you mean direct://reportRoute?"), message);
    }

    @Test
    public void testRouteInlinedIntoRestService() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost");
                rest("/orders").get().to("direct:orders");

                from("direct:orders").routeId("orders").transform().constant("All orders");
                from("direct:order").routeId("order").to("direct:orders?timeout=100");
            }
        });
        context.start();

        // the rest service calls the inlined route
        assertTrue(template.requestBody("seda:get-orders", "", String.class).contains("All orders"));

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:order", ""));
        String message = assertIsInstanceOf(DirectConsumerNotAvailableException.class, e.getCause()).getMessage();

        assertTrue(message.startsWith(PREFIX + "direct://orders?timeout=100. "), message);
        assertTrue(message.contains("No route in CamelContext " + context.getName() + " consumes from it"), message);
        assertTrue(message.contains("inlineRoutes"), message);
    }

    @Test
    public void testRouteConsumesFromTheEndpoint() throws Exception {
        addReportRoutes();
        getMockEndpoint("mock:report").expectedBodiesReceived("Hello World");

        template.sendBody("direct:reportRoute?timeout=100", "Hello World");

        assertMockEndpointsSatisfied();
    }

    private void addReportRoutes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:reportRoute").routeId("report").to("mock:report");
                from("direct:off").routeId("off").autoStartup(false).to("mock:off");
            }
        });
        context.start();
    }

    private String failure(String uri) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody(uri, "Hello World"));
        return assertIsInstanceOf(DirectConsumerNotAvailableException.class, e.getCause()).getMessage();
    }
}
