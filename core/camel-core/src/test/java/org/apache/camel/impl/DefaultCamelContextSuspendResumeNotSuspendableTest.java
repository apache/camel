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

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suspending the CamelContext stops the routes whose consumer does not support suspension, and resuming the
 * CamelContext starts them again (with their consumer).
 */
public class DefaultCamelContextSuspendResumeNotSuspendableTest extends ContextTestSupport {

    @Test
    public void testSuspendResume() throws Exception {
        assertTrue(ServiceHelper.isStarted(context.getRoute("foo").getConsumer()));
        assertFalse(context.getRoute("foo").supportsSuspension());

        context.suspend();

        assertTrue(context.isSuspended());
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));
        assertTrue(ServiceHelper.isStopped(context.getRoute("foo").getConsumer()));

        context.resume();

        assertTrue(context.getStatus().isStarted());
        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));
        assertTrue(ServiceHelper.isStarted(context.getRoute("foo").getConsumer()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("my", new MyComponent());

                from("my:foo").routeId("foo").to("mock:result");
            }
        };
    }

    private static final class MyComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new MyEndpoint(uri, this);
        }
    }

    private static final class MyEndpoint extends DefaultEndpoint {

        MyEndpoint(String uri, MyComponent component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            // a consumer that does not implement Suspendable, so the route does not support suspension
            return new DefaultConsumer(this, processor);
        }
    }
}
