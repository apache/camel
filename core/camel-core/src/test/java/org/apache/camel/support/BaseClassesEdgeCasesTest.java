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
package org.apache.camel.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BaseClassesEdgeCasesTest extends ContextTestSupport {

    private final AtomicInteger onException = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static class MyComponent extends DefaultComponent {
        private Map<String, Object> parameters;

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            this.parameters = new HashMap<>(parameters);
            parameters.clear();
            return new MyEndpoint(uri, this);
        }
    }

    private static class MyEndpoint extends DefaultEndpoint {
        MyEndpoint(String uri, DefaultComponent component) {
            super(uri, component);
        }

        MyEndpoint() {
        }

        @Override
        public Producer createProducer() {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return null;
        }

        @Override
        protected String createEndpointUri() {
            return "my:endpoint";
        }
    }

    @Test
    public void testBridgeErrorHandlerRouteFailureHandledOnce() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).process(e -> onException.incrementAndGet());

                from("timer:foo?repeatCount=1&delay=1&bridgeErrorHandler=true")
                        .throwException(new IllegalStateException("Forced"));
            }
        });
        context.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> onException.get() >= 1);
        // give a potential second (bridged) error handling time to happen
        await().pollDelay(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).until(() -> true);
        assertEquals(1, onException.get());
    }

    @Test
    public void testHashParameterIsKept() throws Exception {
        MyComponent component = new MyComponent();
        context.addComponent("my", component);
        context.start();

        context.getEndpoint("my:foo?hash=abc&x=1");
        assertEquals("abc", component.parameters.get("hash"));
        assertEquals("1", component.parameters.get("x"));
    }

    @Test
    public void testBridgeErrorHandlerHasPrecedenceOverExceptionHandler() throws Exception {
        ExceptionHandler handler = new LoggingExceptionHandler(context, getClass());
        context.getRegistry().bind("myHandler", handler);
        context.start();

        Endpoint endpoint = context.getEndpoint("timer:bar?bridgeErrorHandler=true&exceptionHandler=#myHandler");
        DefaultConsumer consumer = (DefaultConsumer) endpoint.createConsumer(e -> {
        });
        assertInstanceOf(BridgeExceptionHandlerToErrorHandler.class, consumer.getExceptionHandler());
    }

    @Test
    public void testEndpointWithoutComponent() throws Exception {
        MyEndpoint endpoint = new MyEndpoint();
        endpoint.setCamelContext(context);
        Map<String, Object> props = new HashMap<>();
        props.put("exchangePattern", "InOut");
        assertDoesNotThrow(() -> endpoint.configureProperties(props));
        assertEquals(ExchangePattern.InOut, endpoint.getExchangePattern());
    }

    @Test
    public void testReferenceParameterDefaultValue() throws Exception {
        MyComponent component = new MyComponent();
        component.setCamelContext(context);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", new Object());
        assertEquals(5, component.getAndRemoveOrResolveReferenceParameter(parameters, "foo", Integer.class, 5));
    }

    @Test
    public void testReferenceListParameterAsList() throws Exception {
        context.getRegistry().bind("a", "A");
        context.getRegistry().bind("b", "B");
        MyComponent component = new MyComponent();
        component.setCamelContext(context);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", List.of("#a", "#b"));
        List<String> list = component.resolveAndRemoveReferenceListParameter(parameters, "foo", String.class, null);
        assertEquals(List.of("A", "B"), list);
        assertTrue(parameters.isEmpty());
    }
}
