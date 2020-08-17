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
            public void configure() throws Exception {
                rest().get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, false, null, context.getRestConfiguration());
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
            public void configure() throws Exception {
                rest().get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, false, null, context.getRestConfiguration());
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
    public void testRestOpenApiProcessorOpenApiYamlPath() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest().get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, false, null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/openapi.yaml");
        processor.process(exchange);

        String yaml = exchange.getMessage().getBody(String.class);
        assertNotNull(yaml);
        assertEquals("text/yaml", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(yaml.contains("/foo:"));
        assertTrue(yaml.contains("/bar:"));
        assertTrue(yaml.contains("summary: \"Foo endpoint\""));
        assertTrue(yaml.contains("summary: \"Bar endpoint\""));
    }

    @Test
    public void testRestOpenApiProcessorCustomPath() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest().get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, false, null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/some/custom/path/api.json");
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
    public void testRestOpenApiProcessorAcceptHeaderJson() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest().get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, false, null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/some/custom/path/api");
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
            public void configure() throws Exception {
                rest().get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(null, false, null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/some/custom/path/api");
        exchange.getMessage().setHeader("Accept", "application/yaml");
        processor.process(exchange);

        String yaml = exchange.getMessage().getBody(String.class);
        assertNotNull(yaml);
        assertEquals("text/yaml", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(yaml.contains("/foo:"));
        assertTrue(yaml.contains("/bar:"));
        assertTrue(yaml.contains("summary: \"Foo endpoint\""));
        assertTrue(yaml.contains("summary: \"Bar endpoint\""));
    }

    @Test
    public void testRestOpenApiProcessorContextIdListingEnabledForDefaultPath() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest().get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        context.getRegistry().bind("dummy", new DummyRestConsumerFactory());

        RestOpenApiProcessor processor = new RestOpenApiProcessor(".*camel.*", true, null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/openapi.json");

        context.start();
        try {
            processor.process(exchange);

            String json = exchange.getMessage().getBody(String.class);
            assertNotNull(json);

            assertEquals("[{\"name\":\"" + context.getName() + "\"}]", json.replaceAll("\\s+", ""));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRestOpenApiProcessorContextIdListingForNamePlaceholder() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest().get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor("#name#", false, null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/openapi.json");
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        assertTrue(json.contains("\"/foo\""));
        assertTrue(json.contains("\"/bar\""));
        assertTrue(json.contains("\"summary\" : \"Foo endpoint\""));
        assertTrue(json.contains("\"summary\" : \"Bar endpoint\""));
    }

    @Test
    public void testRestOpenApiProcessorContextIdListingEnabledForCustomPath() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest("/rest").get("/foo").description("Foo endpoint").route().id("foo-route").log("Hello /foo").endRest()
                        .post("/bar").description("Bar endpoint").route().id("bar-route").log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor = new RestOpenApiProcessor(".*camel.*", true, null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/" + context.getName() + "/rest");
        processor.process(exchange);

        String json = exchange.getMessage().getBody(String.class);
        assertNotNull(json);
        assertTrue(json.contains("\"/rest/foo\""));
        assertTrue(json.contains("\"/rest/bar\""));
        assertTrue(json.contains("\"summary\" : \"Foo endpoint\""));
        assertTrue(json.contains("\"summary\" : \"Bar endpoint\""));
    }

    @Test
    public void testRestOpenApiProcessorContextIdPatternNoMatches() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                rest("/").get("/foo").description("Foo endpoint").route().log("Hello /foo").endRest().post("/bar")
                        .description("Bar endpoint").route().log("Hello /foo").endRest();
            }
        });

        RestOpenApiProcessor processor
                = new RestOpenApiProcessor("an-invalid-pattern", false, null, context.getRestConfiguration());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("/some/rest/api/document.json", Exchange.HTTP_PATH);
        processor.process(exchange);

        assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(exchange.getMessage().getBody());
    }
}
