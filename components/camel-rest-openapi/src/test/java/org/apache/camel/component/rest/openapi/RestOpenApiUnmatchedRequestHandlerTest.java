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
import org.apache.camel.spi.RestOpenApiUnmatchedRequestHandler;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestOpenApiUnmatchedRequestHandlerTest extends ManagedCamelTestSupport {

    private CamelContext camelContext;
    private RestOpenApiProcessor openApiProcessor;
    private PlatformHttpComponent platformHttpComponent;

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
        platformHttpComponent = mock(PlatformHttpComponent.class);
        camelContext.addComponent("platform-http", platformHttpComponent);
        return camelContext;
    }

    private void enableServerRequestValidation() {
        when(platformHttpComponent.isServerRequestValidation()).thenReturn(true);
    }

    private RestOpenApiProcessor createProcessor() throws Exception {
        return createProcessor(null);
    }

    private RestOpenApiProcessor createProcessor(String unmatchedRequestHandling) throws Exception {
        return createProcessor("unmatched-request-handler.yaml", unmatchedRequestHandling);
    }

    private RestOpenApiProcessor createProcessor(String specificationResource, String unmatchedRequestHandling)
            throws Exception {
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, specificationResource);
        String basePath = RestOpenApiHelper.determineBasePath(camelContext, null, null, openApi);

        DefaultRestOpenapiProcessorStrategy strategy = new DefaultRestOpenapiProcessorStrategy();
        strategy.setCamelContext(camelContext);

        RestOpenApiComponent component = new RestOpenApiComponent();
        RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:" + specificationResource, specificationResource, component, null);
        if (unmatchedRequestHandling != null) {
            endpoint.setUnmatchedRequestHandling(unmatchedRequestHandling);
        }

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
        return send(processor, path, verb, null, null);
    }

    private Exchange send(
            RestOpenApiProcessor processor, String path, String verb, String contentType,
            String accept)
            throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, path);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, verb);
        if (contentType != null) {
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, contentType);
        }
        if (accept != null) {
            exchange.getMessage().setHeader("Accept", accept);
        }
        exchange.getMessage().setBody("request-payload");
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
    void testWrongContentTypeIsAnswered415ByHandler() throws Exception {
        enableServerRequestValidation();
        RecordingHandler handler = new RecordingHandler();
        camelContext.getRegistry().bind("customHandler", handler);

        // POST /orders consumes application/json according to the specification,
        // so the Content-Type header must not be processed
        RestOpenApiProcessor processor = createProcessor("unmatched-request-handler-content-type.yaml", null);
        Exchange exchange = send(processor, "/orders", "POST", "text/plain", null);

        assertEquals(415, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertEquals("{\"error\":\"not found\"}", exchange.getMessage().getBody(String.class));
        assertTrue(exchange.isRouteStop());
        assertEquals(List.of(415), handler.statusCodes);
        assertEquals(List.of(List.of()), handler.allowedMethods);
    }

    @Test
    void testUnacceptableAcceptIsAnswered406ByHandler() throws Exception {
        enableServerRequestValidation();
        RecordingHandler handler = new RecordingHandler();
        camelContext.getRegistry().bind("customHandler", handler);

        // GET /users produces application/json according to the specification,
        // so the Accept header asking for an unsupported type must not be processed
        RestOpenApiProcessor processor = createProcessor("unmatched-request-handler-content-type.yaml", null);
        Exchange exchange = send(processor, "/users", "GET", "application/json", "text/plain");

        assertEquals(406, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertEquals("{\"error\":\"not found\"}", exchange.getMessage().getBody(String.class));
        assertTrue(exchange.isRouteStop());
        assertEquals(List.of(406), handler.statusCodes);
    }

    @Test
    void testMatchingContentTypeAndAcceptAreProcessed() throws Exception {
        enableServerRequestValidation();
        RecordingHandler handler = new RecordingHandler();
        camelContext.getRegistry().bind("customHandler", handler);

        RestOpenApiProcessor processor = createProcessor("unmatched-request-handler-content-type.yaml", null);

        Exchange matched = send(processor, "/orders", "POST", "application/json", "application/json");
        // allowed as no Content-Type and Accept headers are set
        Exchange withoutHeaders = send(processor, "/users", "GET", null, null);

        // processed as valid operations, not answered by the unmatched request handler
        assertFalse(matched.isRouteStop());
        assertFalse(withoutHeaders.isRouteStop());
        assertEquals(List.of(), handler.statusCodes);
    }

    @Test
    void testContentTypeCheckSkippedWhenServerRequestValidationDisabled() throws Exception {
        // the mocked platform-http component is registered with serverRequestValidation disabled,
        // so Camel processes requests that the HTTP layer did not reject for custom validation
        RestOpenApiProcessor processor = createProcessor("unmatched-request-handler-content-type.yaml", null);
        Exchange exchange = send(processor, "/orders", "POST", "text/plain", null);

        assertFalse(exchange.isRouteStop());
        assertNull(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void testCustomHandlerFromFactoryFinderIsCalled() throws Exception {
        // Since we want to be able to test both a bean registered directly into
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

    @Test
    void testCatchAllRegisteredOnPlatformHttpWhenCamelHandling() throws Exception {
        RestOpenApiProcessor processor = createProcessor("camel");
        PlatformHttpComponent phc = camelContext.getComponent("platform-http", PlatformHttpComponent.class);

        // a catch-all for the api base path (without verbs) must be registered so unmatched requests are routed to Camel
        verify(phc).addHttpEndpoint(eq(""), isNull(), isNull(), isNull(), any(PlatformHttpConsumer.class));
        ServiceHelper.stopService(processor);
        openApiProcessor = null;

        // and removed again when the processor stops
        verify(phc).removeHttpEndpoint(eq(""), any(PlatformHttpConsumer.class));
    }

    @Test
    void testNoCatchAllRegisteredOnPlatformHttpWhenPlatformHandling() throws Exception {
        createProcessor("platform");
        PlatformHttpComponent phc = camelContext.getComponent("platform-http", PlatformHttpComponent.class);
        // only per-operation registrations (with verbs) are expected
        verify(phc, never()).addHttpEndpoint(anyString(), isNull(), any(), any(), any());
    }

    @Test
    void testNoCatchAllRegisteredOnPlatformHttpByDefault() throws Exception {
        createProcessor();
        PlatformHttpComponent phc = camelContext.getComponent("platform-http", PlatformHttpComponent.class);
        // only per-operation registrations (with verbs) are expected
        verify(phc, never()).addHttpEndpoint(anyString(), isNull(), any(), any(), any());
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
