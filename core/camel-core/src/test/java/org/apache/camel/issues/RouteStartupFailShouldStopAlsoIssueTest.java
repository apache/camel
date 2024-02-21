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
package org.apache.camel.issues;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RouteStartupFailShouldStopAlsoIssueTest extends ContextTestSupport {

    private static final List<String> EVENTS = new ArrayList<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testShouldAlsoStop() throws Exception {
        context.addComponent("my", new MyComponent(context));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:bar").routeId("bar").to("mock:bar");

                // the foo route fails to startup but it should be stopped when
                // camel stops
                from("my:foo").routeId("foo").to("mock:foo");
            }
        });

        try {
            context.start();
        } catch (Exception e) {
            // should fail
        }

        assertTrue(context.getRouteController().getRouteStatus("foo").isStopped());
        assertFalse(context.getRouteController().getRouteStatus("foo").isStarted());

        assertTrue(context.getRouteController().getRouteStatus("bar").isStopped());
        assertFalse(context.getRouteController().getRouteStatus("bar").isStarted());

        assertFalse(context.getStatus().isStarted());
        assertTrue(context.getStatus().isStopped());

        assertEquals(3, EVENTS.size());
        assertEquals("constructor", EVENTS.get(0));
        assertEquals("doStart", EVENTS.get(1));
        assertEquals("doStop", EVENTS.get(2));
    }

    private static class MyComponent extends DefaultComponent {

        public MyComponent(CamelContext context) {
            super(context);
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return new MyEndpoint(uri, this);
        }
    }

    private static class MyEndpoint extends DefaultEndpoint {

        public MyEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() throws Exception {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return new MyFailConsumer(this, processor);
        }
    }

    private static class MyFailConsumer extends DefaultConsumer {

        public MyFailConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
            EVENTS.add("constructor");
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();
            EVENTS.add("doStart");
            throw new IllegalStateException("Forced failure");
        }

        @Override
        protected void doStop() throws Exception {
            super.doStop();
            EVENTS.add("doStop");
        }
    }

}
