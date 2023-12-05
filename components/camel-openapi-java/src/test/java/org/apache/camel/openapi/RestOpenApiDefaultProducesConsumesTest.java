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
package org.apache.camel.openapi;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.openapi.BeanConfig.DEFAULT_MEDIA_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiDefaultProducesConsumesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] OPENAPI_VERSIONS = { "3.1", "3.0", "2.0" };
    private final Logger log = LoggerFactory.getLogger(getClass());

    @ParameterizedTest
    @MethodSource("getOpenApiVersions")
    void testDefaultConsumesConfig(String openApiVersion) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration()
                        .apiProperty("openapi.version", openApiVersion)
                        .apiProperty("api.default.consumes", "application/xml");

                rest("/api")
                        .post("/test")
                        .param()
                        .name("body")
                        .description("The request body")
                        .type(RestParamType.body)
                        .endParam()
                        .to("direct:api");

                from("direct:api").setBody().constant("Hello World");
            }
        });

        RestConfiguration restConfiguration = context.getRestConfiguration();
        RestOpenApiProcessor processor
                = new RestOpenApiProcessor(restConfiguration.getApiProperties(), restConfiguration);
        Exchange exchange = new DefaultExchange(context);
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        log.info(json);

        JsonNode root = MAPPER.readTree(json);
        assertConsumesMatches(root, openApiVersion, "application/xml");
    }

    @ParameterizedTest
    @MethodSource("getOpenApiVersions")
    void testDefaultProducesConfig(String openApiVersion) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration()
                        .apiProperty("openapi.version", openApiVersion)
                        .apiProperty("api.default.produces", "application/xml");

                rest("/api")
                        .post("/test")
                        .outType(String.class)
                        .to("direct:api");

                from("direct:api").setBody().constant("Hello World");
            }
        });

        RestConfiguration restConfiguration = context.getRestConfiguration();
        RestOpenApiProcessor processor
                = new RestOpenApiProcessor(restConfiguration.getApiProperties(), restConfiguration);
        Exchange exchange = new DefaultExchange(context);
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        log.info(json);

        JsonNode root = MAPPER.readTree(json);
        assertProducesMatches(root, openApiVersion, "application/xml");
    }

    @ParameterizedTest
    @MethodSource("getOpenApiVersions")
    void testDefaultConfigMediaType(String openApiVersion) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().apiProperty("openapi.version", openApiVersion);

                rest("/api")
                        .post("/test")
                        .outType(String.class)
                        .param()
                        .name("body")
                        .description("The request body")
                        .type(RestParamType.body)
                        .endParam()
                        .to("direct:api");

                from("direct:api").setBody().constant("Hello World");
            }
        });

        RestConfiguration restConfiguration = context.getRestConfiguration();
        RestOpenApiProcessor processor
                = new RestOpenApiProcessor(restConfiguration.getApiProperties(), restConfiguration);
        Exchange exchange = new DefaultExchange(context);
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        log.info(json);

        JsonNode root = MAPPER.readTree(json);
        if (!openApiVersion.equals("2.0")) {
            JsonNode requestBody = root.findValue("requestBody");
            assertConsumesMatches(requestBody, openApiVersion, DEFAULT_MEDIA_TYPE);

            JsonNode responses = root.findValue("responses");
            assertProducesMatches(responses, openApiVersion, DEFAULT_MEDIA_TYPE);
        } else {
            assertConsumesMatches(root, openApiVersion, DEFAULT_MEDIA_TYPE);
            assertProducesMatches(root, openApiVersion, DEFAULT_MEDIA_TYPE);
        }
    }

    @ParameterizedTest
    @MethodSource("getOpenApiVersions")
    void testVerbConfigOverridesDefaultConfig(String openApiVersion) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().apiProperty("openapi.version", openApiVersion);

                rest("/api")
                        .post("/test")
                        .consumes("text/plain")
                        .produces("application/xml")
                        .outType(String.class)
                        .param()
                        .name("body")
                        .description("The request body")
                        .type(RestParamType.body)
                        .endParam()
                        .to("direct:api");

                from("direct:api").setBody().constant("Hello World");
            }
        });

        RestConfiguration restConfiguration = context.getRestConfiguration();
        RestOpenApiProcessor processor
                = new RestOpenApiProcessor(restConfiguration.getApiProperties(), restConfiguration);
        Exchange exchange = new DefaultExchange(context);
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        log.info(json);

        JsonNode root = MAPPER.readTree(json);
        if (!openApiVersion.equals("2.0")) {
            JsonNode requestBody = root.findValue("requestBody");
            assertConsumesMatches(requestBody, openApiVersion, "text/plain");

            JsonNode responses = root.findValue("responses");
            assertProducesMatches(responses, openApiVersion, "application/xml");
        } else {
            assertConsumesMatches(root, openApiVersion, "text/plain");
            assertProducesMatches(root, openApiVersion, "application/xml");
        }
    }

    @ParameterizedTest
    @MethodSource("getOpenApiVersions")
    void testGlobalConfigOverridesDefaultConfig(String openApiVersion) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().apiProperty("openapi.version", openApiVersion);

                rest("/api")
                        .consumes("text/plain")
                        .produces("application/xml")
                        .post("/test")
                        .outType(String.class)
                        .param()
                        .name("body")
                        .description("The request body")
                        .type(RestParamType.body)
                        .endParam()
                        .to("direct:api");

                from("direct:api").setBody().constant("Hello World");
            }
        });

        RestConfiguration restConfiguration = context.getRestConfiguration();
        RestOpenApiProcessor processor
                = new RestOpenApiProcessor(restConfiguration.getApiProperties(), restConfiguration);
        Exchange exchange = new DefaultExchange(context);
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        log.info(json);

        JsonNode root = MAPPER.readTree(json);
        if (!openApiVersion.equals("2.0")) {
            JsonNode requestBody = root.findValue("requestBody");
            assertConsumesMatches(requestBody, openApiVersion, "text/plain");

            JsonNode responses = root.findValue("responses");
            assertProducesMatches(responses, openApiVersion, "application/xml");
        } else {
            assertConsumesMatches(root, openApiVersion, "text/plain");
            assertProducesMatches(root, openApiVersion, "application/xml");
        }
    }

    static String[] getOpenApiVersions() {
        return OPENAPI_VERSIONS;
    }

    private void assertProducesMatches(JsonNode json, String openApiVersion, String match) {
        String fieldName = !openApiVersion.equals("2.0") ? "content" : "produces";
        assertContentTypeMatches(json, openApiVersion, fieldName, match);
    }

    private void assertConsumesMatches(JsonNode json, String openApiVersion, String match) {
        String fieldName = !openApiVersion.equals("2.0") ? "content" : "consumes";
        assertContentTypeMatches(json, openApiVersion, fieldName, match);
    }

    private void assertContentTypeMatches(JsonNode json, String openApiVersion, String fieldName, String match) {
        List<JsonNode> content = json.findValues(fieldName);
        assertNotNull(content);
        assertEquals(1, content.size());

        JsonNode contentNode = content.get(0);
        if (!openApiVersion.equals("2.0")) {
            assertTrue(contentNode.has(match));
        } else {
            assertEquals(1, contentNode.size());
            JsonNode mediaType = contentNode.get(0);
            assertEquals(match, mediaType.asText());
        }
    }
}
