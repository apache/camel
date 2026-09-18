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
package org.apache.camel.component.platform.http;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultConsumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PlatformHttpSharedPathRouteLifecycleTest {

    @Test
    void stoppingSecondConsumerPreservesFirstConsumerOnSamePath() throws Exception {
        RecordingPlatformHttpListener listener = new RecordingPlatformHttpListener();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PlatformHttpComponent component = createComponent(listener, context);
            context.addRoutes(sharedPathRoutes());
            context.start();

            assertEquals(2, component.getHttpEndpoints().size());
            assertEquals(2, listener.registered.size());

            context.getRouteController().stopRoute("shared-post");

            assertEquals(1, component.getHttpEndpoints().size());
            assertEquals(1, listener.registered.size());
            HttpEndpointModel remaining = listener.registered.get(0);
            assertEquals("/shared", remaining.getUri());
            assertEquals("GET", remaining.getVerbs());
        }
    }

    @Test
    void stoppingFirstConsumerPreservesSecondConsumerOnSamePath() throws Exception {
        RecordingPlatformHttpListener listener = new RecordingPlatformHttpListener();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PlatformHttpComponent component = createComponent(listener, context);
            context.addRoutes(sharedPathRoutes());
            context.start();

            context.getRouteController().stopRoute("shared-get");

            assertEquals(1, component.getHttpEndpoints().size());
            assertEquals(1, listener.registered.size());
            HttpEndpointModel remaining = listener.registered.get(0);
            assertEquals("/shared", remaining.getUri());
            assertEquals("POST", remaining.getVerbs());
        }
    }

    @Test
    void restartRouteAfterStopReRegistersEndpoint() throws Exception {
        RecordingPlatformHttpListener listener = new RecordingPlatformHttpListener();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PlatformHttpComponent component = createComponent(listener, context);
            context.addRoutes(sharedPathRoutes());
            context.start();

            context.getRouteController().stopRoute("shared-post");
            assertEquals(1, component.getHttpEndpoints().size());

            context.getRouteController().startRoute("shared-post");
            assertEquals(2, component.getHttpEndpoints().size());
            assertEquals(2, listener.registered.size());
        }
    }

    @Test
    void removeHttpEndpointByUriRemovesAllConsumersOnPath() {
        PlatformHttpComponent component = new PlatformHttpComponent();
        Consumer getConsumer = mock(Consumer.class);
        Consumer postConsumer = mock(Consumer.class);

        component.addHttpEndpoint("/shared", "GET", null, null, getConsumer);
        component.addHttpEndpoint("/shared", "POST", null, null, postConsumer);

        assertEquals(2, component.getHttpEndpoints().size());

        component.removeHttpEndpoint("/shared");

        assertTrue(component.getHttpEndpoints().isEmpty());
    }

    @Test
    void removeHttpEndpointWithNullConsumerIsIgnored() {
        PlatformHttpComponent component = new PlatformHttpComponent();
        Consumer routeConsumer = mock(Consumer.class);

        component.addHttpEndpoint("/static", null, null, null, null);
        component.addHttpEndpoint("/shared", "GET", null, null, routeConsumer);

        component.removeHttpEndpoint((Consumer) null);

        assertEquals(2, component.getHttpEndpoints().size());
    }

    private static PlatformHttpComponent createComponent(RecordingPlatformHttpListener listener, DefaultCamelContext context) {
        PlatformHttpComponent component = new PlatformHttpComponent();
        component.setEngine(new NoopEngine());
        component.addPlatformHttpListener(listener);
        context.addComponent("platform-http", component);
        return component;
    }

    private static RouteBuilder sharedPathRoutes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("platform-http:/shared?httpMethodRestrict=GET")
                        .routeId("shared-get")
                        .setBody().constant("shared-get");
                from("platform-http:/shared?httpMethodRestrict=POST")
                        .routeId("shared-post")
                        .setBody().constant("shared-post");
            }
        };
    }

    private static final class RecordingPlatformHttpListener implements PlatformHttpListener {
        private final List<HttpEndpointModel> registered = new ArrayList<>();

        @Override
        public void registerHttpEndpoint(HttpEndpointModel model) {
            registered.add(model);
        }

        @Override
        public void unregisterHttpEndpoint(HttpEndpointModel model) {
            registered.remove(model);
        }
    }

    private static final class NoopEngine implements PlatformHttpEngine {
        @Override
        public PlatformHttpConsumer createConsumer(PlatformHttpEndpoint platformHttpEndpoint, Processor processor) {
            return new NoopPlatformHttpConsumer(platformHttpEndpoint, processor);
        }
    }

    private static final class NoopPlatformHttpConsumer extends DefaultConsumer implements PlatformHttpConsumer {
        private NoopPlatformHttpConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        public PlatformHttpEndpoint getEndpoint() {
            return (PlatformHttpEndpoint) super.getEndpoint();
        }
    }
}
