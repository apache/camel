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

import io.swagger.v3.oas.models.OpenAPI;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiReaderModelBookOrderTest extends CamelTestSupport {

    private Logger log = LoggerFactory.getLogger(getClass());

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @BindToRegistry("bookService")
    private Object dummy = new Object();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest()
                    .securityDefinitions()
                    .oauth2("global")
                    .accessCode(
                        "https://AUTHORIZATION_URL",
                        "https://TOKEN_URL"
                    )
                    .withScope("groups", "Required scopes for Camel REST APIs")
                    .end();

                // this user REST service is json only
                rest("/books").tag("dude").description("Book order service").consumes("application/json")
                        .produces("application/json")

                        .get("/{id}").description("Find order by id").outType(BookOrder.class).responseMessage()
                        .message("The order returned").endResponseMessage().param().name("id")
                        .type(RestParamType.path).description("The id of the order to get").dataType("integer").endParam()
                        .to("bean:bookService?method=getOrder(${header.id})")
                        .get("/books/{id}/line/{lineNum}").outType(LineItem.class)
                        .to("bean:bookService?method=getOrder(${header.id})");
            }
        };
    }

    @Test
    public void testReaderRead() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion("2.0");
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);

        log.info(json);

        assertTrue(json.contains("\"host\" : \"localhost:8080\""));
        assertTrue(json.contains("\"description\" : \"The order returned\""));
        assertTrue(json.contains("\"BookOrder\""));
        assertTrue(json.contains("\"LineItem\""));
        assertTrue(json.contains("\"$ref\" : \"#/definitions/BookOrder\""));
        assertTrue(json.contains("\"$ref\" : \"#/definitions/LineItem\""));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.openapi.BookOrder\""));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.openapi.LineItem\""));

        context.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.1", "3.0" })
    public void testReaderReadV3(String version) throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion(version);
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        log.info(json);

        assertTrue(json.contains("\"url\" : \"http://localhost:8080/api\""));
        assertTrue(json.contains("\"description\" : \"The order returned\""));
        assertTrue(json.contains("\"BookOrder\""));
        assertTrue(json.contains("\"LineItem\""));
        assertTrue(json.contains("\"$ref\" : \"#/components/schemas/BookOrder"));
        assertTrue(json.contains("\"$ref\" : \"#/components/schemas/LineItem"));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.openapi.BookOrder\""));
        assertTrue(json.contains("\"format\" : \"org.apache.camel.openapi.LineItem\""));

        context.stop();
    }

}
