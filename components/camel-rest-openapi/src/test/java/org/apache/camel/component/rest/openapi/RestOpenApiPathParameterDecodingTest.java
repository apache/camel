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
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The value of a path parameter of a contract-first operation is decoded as a path segment (RFC 3986), as the Rest DSL
 * consumers do: percent-encoded octets as UTF-8, a {@code +} is kept, and an encoded {@code /} stays inside its
 * parameter.
 */
class RestOpenApiPathParameterDecodingTest extends ManagedCamelTestSupport {

    private CamelContext camelContext;

    @BeforeEach
    public void createContext() throws Exception {
        initializeContextForComponent("rest-openapi");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:getItem").to("mock:getItem");
                from("direct:getFile").to("mock:getFile");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext(String componentName) {
        camelContext = new DefaultCamelContext();
        camelContext.addComponent("platform-http", mock(PlatformHttpComponent.class));
        return camelContext;
    }

    private RestOpenApiProcessor createProcessor() throws Exception {
        String specificationResource = "path-parameters.yaml";
        OpenAPI openApi = RestOpenApiEndpoint.loadSpecificationFrom(camelContext, specificationResource);
        String basePath = RestOpenApiHelper.determineBasePath(camelContext, null, null, openApi);

        DefaultRestOpenapiProcessorStrategy strategy = new DefaultRestOpenapiProcessorStrategy();
        strategy.setCamelContext(camelContext);

        RestOpenApiComponent component = new RestOpenApiComponent();
        RestOpenApiEndpoint endpoint = new RestOpenApiEndpoint(
                "rest-openapi:" + specificationResource, specificationResource, component, null);

        RestOpenApiProcessor processor = new RestOpenApiProcessor(endpoint, openApi, basePath, null, strategy);
        processor.setCamelContext(camelContext);
        PlatformHttpConsumerAware consumerAware = mock(PlatformHttpConsumerAware.class);
        PlatformHttpConsumer consumer = mock(PlatformHttpConsumer.class);
        PlatformHttpEndpoint platformHttpEndpoint = mock(PlatformHttpEndpoint.class);
        when(consumerAware.getPlatformHttpConsumer()).thenReturn(consumer);
        when(consumer.getEndpoint()).thenReturn(platformHttpEndpoint);
        when(platformHttpEndpoint.getServiceUrl()).thenReturn("http://localhost:8080");
        processor.setPlatformHttpConsumer(consumerAware);
        processor.afterPropertiesConfigured(camelContext);
        return processor;
    }

    private Exchange get(String path) throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, path);
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "GET");
        createProcessor().process(exchange, done -> {
        });
        return exchange;
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/items/A%221|A\"1",
            "/items/X%20Y|X Y",
            "/items/caf%C3%A9|café",
            "/items/a+b|a+b",
            "/items/a%2Bb|a+b",
            "/items/100%25|100%",
            "/items/%2541|%41",
            "/items/100%|100%",
            "/items/plain|plain" })
    void testPathParameterIsDecoded(String path, String expected) throws Exception {
        Exchange exchange = get(path);

        assertEquals(expected, exchange.getMessage().getHeader("id"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/files/a%2Fb/c.txt|a/b|c.txt",
            "/files/my%20dir/x%2By|my dir|x+y" })
    void testEncodedSlashStaysInItsParameter(String path, String dir, String name) throws Exception {
        Exchange exchange = get(path);

        assertEquals(dir, exchange.getMessage().getHeader("dir"));
        assertEquals(name, exchange.getMessage().getHeader("name"));
    }
}
