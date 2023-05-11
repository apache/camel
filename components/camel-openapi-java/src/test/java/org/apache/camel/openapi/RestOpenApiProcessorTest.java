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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiProcessorTest {

    @Test
    public void testRestOpenApiProcessor() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                rest().get("/foo").description("Foo endpoint").to("mock:foo")
                        .post("/bar").description("Bar endpoint").to("mock:foo");
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        assertTrue(json.contains("\"/foo\""));
        assertTrue(json.contains("\"/bar\""));
        assertTrue(json.contains("\"summary\" : \"Foo endpoint\""));
        assertTrue(json.contains("\"summary\" : \"Bar endpoint\""));
    }

    @Test
    public void testRestOpenApiProcessorOpenApiJsonPath() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                rest().get("/foo").description("Foo endpoint").to("mock:foo")
                        .post("/bar").description("Bar endpoint").to("mock:foo");
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/openapi.json");
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        assertEquals("application/json", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(json.contains("\"/foo\""));
        assertTrue(json.contains("\"/bar\""));
        assertTrue(json.contains("\"summary\" : \"Foo endpoint\""));
        assertTrue(json.contains("\"summary\" : \"Bar endpoint\""));
    }

    @Test
    public void testRestOpenApiProcessorOpenApiYamlDeprecatedVerb() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                rest().get("/foo").description("Foo endpoint").deprecated().to("mock:foo")
                        .post("/bar").description("Bar endpoint").to("mock:foo");
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/openapi.yaml");
        processor.process(exchange);

        String yaml = exchange.getMessage().getBody(String.class);
        assertNotNull(yaml);
        assertEquals("text/yaml", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(yaml.contains("/foo:"));
        assertTrue(yaml.contains("/bar:"));
        assertTrue(yaml.contains("deprecated: true"));

        JsonNode jsonNode = new ObjectMapper(new YAMLFactory()).readValue(yaml, JsonNode.class);
        assertNull(jsonNode.get("paths").get("/bar").get("post").get("deprecated"));
        assertNotNull(jsonNode.get("paths").get("/foo").get("get").get("deprecated"));
        assertTrue(jsonNode.get("paths").get("/foo").get("get").get("deprecated").asBoolean());
    }

    @Test
    public void testRestOpenApiProcessorOpenApiYamlPath() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                rest().get("/foo").description("Foo endpoint").to("mock:foo")
                        .post("/bar").description("Bar endpoint").to("mock:foo");
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/openapi.yaml");
        processor.process(exchange);

        String yaml = exchange.getMessage().getBody(String.class);
        assertNotNull(yaml);
        assertEquals("text/yaml", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(yaml.contains("/foo:"));
        assertTrue(yaml.contains("/bar:"));
        assertTrue(yaml.contains("summary: Foo endpoint"));
        assertTrue(yaml.contains("summary: Bar endpoint"));
    }

    @Test
    public void testRestOpenApiProcessorAcceptHeaderJson() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                rest().get("/foo").description("Foo endpoint").to("mock:foo")
                        .post("/bar").description("Bar endpoint").to("mock:foo");
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/");
        exchange.getMessage().setHeader("Accept", "application/json");
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        assertEquals("application/json", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(json.contains("\"/foo\""));
        assertTrue(json.contains("\"/bar\""));
        assertTrue(json.contains("\"summary\" : \"Foo endpoint\""));
        assertTrue(json.contains("\"summary\" : \"Bar endpoint\""));
    }

    @Test
    public void testRestOpenApiProcessorAcceptHeaderYaml() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                rest().get("/foo").description("Foo endpoint").to("mock:foo")
                        .post("/bar").description("Bar endpoint").to("mock:foo");
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/");
        exchange.getMessage().setHeader("Accept", "application/yaml");
        processor.process(exchange);

        String yaml = exchange.getMessage().getBody(String.class);
        assertNotNull(yaml);
        assertEquals("text/yaml", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(yaml.contains("/foo:"));
        assertTrue(yaml.contains("/bar:"));
        assertTrue(yaml.contains("summary: Foo endpoint"));
        assertTrue(yaml.contains("summary: Bar endpoint"));
    }

}
