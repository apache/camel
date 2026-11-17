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
package org.apache.camel.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Processor;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A route whose endpoint fails to create the consumer when the route is started again: once the endpoint can create it,
 * the route starts and consumes.
 */
class RouteStartAfterFailedCreateConsumerTest extends ContextTestSupport {

    private final AtomicBoolean failCreateConsumer = new AtomicBoolean();

    @Test
    void routeStartsOnceTheConsumerCanBeCreated() throws Exception {
        context.getRouteController().stopRoute("foo");

        failCreateConsumer.set(true);
        assertThrows(FailedToStartRouteException.class, () -> context.getRouteController().startRoute("foo"));
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));

        failCreateConsumer.set(false);
        context.getRouteController().startRoute("foo");
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello");
        template.sendBody("direct:flaky", "Hello");
        mock.assertIsSatisfied();
    }

    @Test
    void routeStartsAfterTwoFailedStarts() throws Exception {
        context.getRouteController().stopRoute("foo");

        failCreateConsumer.set(true);
        assertThrows(FailedToStartRouteException.class, () -> context.getRouteController().startRoute("foo"));
        assertThrows(FailedToStartRouteException.class, () -> context.getRouteController().startRoute("foo"));

        failCreateConsumer.set(false);
        context.getRouteController().startRoute("foo");
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello");
        template.sendBody("direct:flaky", "Hello");
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addEndpoint("direct://flaky",
                        new DirectEndpoint("direct://flaky", context.getComponent("direct", DirectComponent.class)) {
                            @Override
                            public Consumer createConsumer(Processor processor) throws Exception {
                                if (failCreateConsumer.get()) {
                                    throw new IllegalStateException("Simulated failure to create the consumer");
                                }
                                return super.createConsumer(processor);
                            }
                        });
                from("direct:flaky").routeId("foo").to("mock:result");
            }
        };
    }
}
