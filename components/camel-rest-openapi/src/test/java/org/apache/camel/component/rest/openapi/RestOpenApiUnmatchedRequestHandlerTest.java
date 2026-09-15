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
package org.apache.camel.component.rest.openapi;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumer;
import org.apache.camel.component.platform.http.spi.PlatformHttpConsumerAware;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestOpenApiUnmatchedRequestHandlerTest extends ManagedCamelTestSupport {

    private CamelContext camelContext;
    private RestOpenApiProcessor openApiProcessor;

    @BeforeEach
    public void createMocks() throws Exception {
        initializeContextForComponent("rest-openapi");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listUsers").to("mock:listUsers");
                from("direct:listOrders").to("mock:listOrders");
                from("direct:createOrder").to("mock:createOrder");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext(String componentName) {
        camelContext = new DefaultCamelContext();
        PlatformHttpComponent httpCmpn = mock(PlatformHttpComponent.class);
        camelContext.addComponent("platform-http", httpCmpn);
        return camelContext;
    }

    private RestOpenApiProcessor createProcessor() throws Exception {
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, "unmatched-request-handler.yaml");
        String basePath = RestOpenApiHelper.determineBasePath(camelContext, null, null, openApi);

        DefaultRestOpenapiProcessorStrategy strategy = new DefaultRestOpenapiProcessorStrategy();
        strategy.setCamelContext(camelContext);

        RestOpenApiComponent component = new RestOpenApiComponent();
        RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:unmatched-request-handler.yaml", "unmatched-request-handler.yaml", component, null);

        RestOpenApiProcessor processor = new RestOpenApiProcessor(endpoint, openApi, basePath, null, strategy);
        processor.setCamelContext(camelContext);
        processor.setPlatformHttpConsumer(createMockPlatformHttpConsumerAware());
        processor.afterPropertiesConfigured(camelContext);
        openApiProcessor = processor;
        return processor;
    }

    private PlatformHttpConsumerAware createMockPlatformHttpConsumerAware() {
        PlatformHttpConsumerAware platformHttpConsumerAware = mock(PlatformHttpConsumerAware.class);
        PlatformHttpConsumer platformHttpConsumer = mock(PlatformHttpConsumer.class);
        PlatformHttpEndpoint platformHttpEndpoint = mock(PlatformHttpEndpoint.class);
        when(platformHttpConsumerAware.getPlatformHttpConsumer()).thenReturn(platformHttpConsumer);
        when(platformHttpConsumer.getEndpoint()).thenReturn(platformHttpEndpoint);
        when(platformHttpEndpoint.getServiceUrl()).thenReturn("http://localhost:8080");
        return platformHttpConsumerAware;
    }

    private Exchange send(RestOpenApiProcessor processor, String path, String verb) throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, path);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, verb);
        processor.process(exchange, done -> {
        });
        return exchange;
    }

    @Test
    void testDefaultHandlerReturns404WithEmptyBody() throws Exception {
        RestOpenApiProcessor processor = createProcessor();
        Exchange exchange = send(processor, "/unknown", "GET");

        assertEquals(404, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertNull(exchange.getMessage().getBody());
        assertTrue(exchange.isRouteStop());
    }

    @Test
    void testDefaultHandlerReturns405WithAllowHeader() throws Exception {
        RestOpenApiProcessor processor = createProcessor();
        Exchange exchange = send(processor, "/orders", "PUT");

        assertEquals(405, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertEquals("GET, POST", exchange.getMessage().getHeader("Allow", String.class));
        assertNull(exchange.getMessage().getBody());
        assertTrue(exchange.isRouteStop());
    }

    @Test
    void testCustomHandlerFromRegistryIsCalled() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        camelContext.getRegistry().bind("customHandler", handler);

        RestOpenApiProcessor processor = createProcessor();
        Exchange exchange = send(processor, "/unknown", "GET");

        assertEquals(404, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertEquals("{\"error\":\"not found\"}", exchange.getMessage().getBody(String.class));
        assertEquals(List.of(404), handler.statusCodes);
    }

    @Test
    void testCustomHandlerReceivesCorrectStatusCode() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        camelContext.getRegistry().bind("customHandler", handler);

        RestOpenApiProcessor processor = createProcessor();

        Exchange notFound = send(processor, "/unknown", "GET");
        Exchange methodNotAllowed = send(processor, "/orders", "PUT");

        assertEquals(List.of(404, 405), handler.statusCodes);
        assertEquals(404, notFound.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertEquals(405, methodNotAllowed.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
    }

    @Test
    void testCustomHandlerReceivesCorrectAllowedMethods() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        camelContext.getRegistry().bind("customHandler", handler);

        RestOpenApiProcessor processor = createProcessor();

        send(processor, "/unknown", "GET");
        send(processor, "/orders", "PUT");

        assertEquals(List.of(List.of(), List.of("GET", "POST")), handler.allowedMethods);
    }

    @Test
    void testCustomHandlerFromFactoryFinderIsCalled() throws Exception {
        // Since we want to be ablet to test both a bean regitered directly into
        // the registry and the factory finder we can not just put the factory
        // file into src/test/resources/META-INF/services that breaks other tests
        ClassResolver classResolver = mock(ClassResolver.class);
        String properties = "class=" + FactoryFoundHandler.class.getName();
        when(classResolver.loadResourceAsStream(
                FactoryFinder.DEFAULT_PATH + RestOpenApiUnmatchedRequestHandler.FACTORY))
                .thenAnswer(invocation -> new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8)));
        when(classResolver.resolveClass(FactoryFoundHandler.class.getName()))
                .thenAnswer(invocation -> FactoryFoundHandler.class);

        FactoryFinder realFinder = camelContext.getCamelContextExtension().getBootstrapFactoryFinder();
        FactoryFinder factoryFinder = new DefaultFactoryFinder(classResolver, FactoryFinder.DEFAULT_PATH) {
            @Override
            public Optional<Class<?>> findOptionalClass(String key) {
                return RestOpenApiUnmatchedRequestHandler.FACTORY.equals(key)
                        ? super.findOptionalClass(key)
                        : realFinder.findOptionalClass(key);
            }
        };
        camelContext.getCamelContextExtension().setBootstrapFactoryFinder(factoryFinder);

        RestOpenApiProcessor processor = createProcessor();
        Exchange exchange = send(processor, "/unknown", "GET");

        assertEquals(404, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertEquals("{\"error\":\"from factory finder\"}", exchange.getMessage().getBody(String.class));
    }

    @AfterEach
    void stopProcessor() throws Exception {
        if (openApiProcessor != null) {
            ServiceHelper.stopService(openApiProcessor);
            openApiProcessor = null;
        }
    }

    static final class RecordingHandler implements RestOpenApiUnmatchedRequestHandler {

        final List<Integer> statusCodes = new ArrayList<>();
        final List<List<String>> allowedMethods = new ArrayList<>();

        @Override
        public void handle(Exchange exchange, int statusCode, List<String> allowedMethods) {
            statusCodes.add(statusCode);
            this.allowedMethods.add(List.copyOf(allowedMethods));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
            if (!allowedMethods.isEmpty()) {
                exchange.getMessage().setHeader("Allow", String.join(", ", allowedMethods));
            }
            exchange.getMessage().setBody("{\"error\":\"not found\"}");
        }
    }

    public static final class FactoryFoundHandler implements RestOpenApiUnmatchedRequestHandler {

        @Override
        public void handle(Exchange exchange, int statusCode, List<String> allowedMethods) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
            exchange.getMessage().setBody("{\"error\":\"from factory finder\"}");
        }
    }
}
