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

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiReaderDisabledTest extends CamelTestSupport {

    private Logger log = LoggerFactory.getLogger(getClass());

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest("/hello").consumes("application/json").produces("application/json").get("/hi/{name}")
                        .description("Saying hi").param().name("name").type(RestParamType.path)
                        .dataType("string").description("Who is it").example("Donald Duck").endParam()
                        .param().name("filter").description("Filters to apply to the entity.").type(RestParamType.query)
                        .dataType("array").arrayType("date-time").endParam().to("log:hi")
                        .get("/bye/{name}").disabled().description("Saying bye").param().name("name")
                        .type(RestParamType.path).dataType("string").description("Who is it").example("Donald Duck").endParam()
                        .responseMessage().code(200).message("A reply number")
                        .responseModel(float.class).example("success", "123").example("error", "-1").endResponseMessage()
                        .to("log:bye").post("/bye").disabled("true")
                        .description("To update the greeting message").consumes("application/xml").produces("application/xml")
                        .param().name("greeting").type(RestParamType.body)
                        .dataType("string").description("Message to use as greeting")
                        .example("application/xml", "<hello>Hi</hello>").endParam().to("log:bye");
            }
        };
    }

    @Test
    public void testReaderRead() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setInfo(new Info());
        config.setVersion("2.0");
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPI(openApi, config);
        log.info(json);

        assertTrue(json.contains("\"host\" : \"localhost:8080\""));
        assertTrue(json.contains("\"basePath\" : \"/api\""));
        assertFalse(json.contains("\"/hello/bye\""));
        assertFalse(json.contains("\"summary\" : \"To update the greeting message\""));
        assertFalse(json.contains("\"/hello/bye/{name}\""));
        assertFalse(json.contains("\"/api/hello/bye/{name}\""));
        assertTrue(json.contains("\"/hello/hi/{name}\""));
        assertFalse(json.contains("\"/api/hello/hi/{name}\""));
        assertFalse(json.contains("\"type\" : \"number\""));
        assertFalse(json.contains("\"format\" : \"float\""));
        assertFalse(json.contains("\"application/xml\" : \"<hello>Hi</hello>\""));
        assertTrue(json.contains("\"x-example\" : \"Donald Duck\""));
        assertFalse(json.contains("\"success\" : \"123\""));
        assertFalse(json.contains("\"error\" : \"-1\""));
        assertTrue(json.contains("\"type\" : \"array\""));

        context.stop();
    }

    @Test
    public void testReaderReadV3() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setInfo(new Info());
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = io.swagger.v3.core.util.Json.pretty(openApi);
        log.info(json);

        assertTrue(json.contains("\"url\" : \"http://localhost:8080/api\""));
        assertFalse(json.contains("\"/hello/bye\""));
        assertFalse(json.contains("\"summary\" : \"To update the greeting message\""));
        assertFalse(json.contains("\"/hello/bye/{name}\""));
        assertFalse(json.contains("\"/api/hello/bye/{name}\""));
        assertTrue(json.contains("\"/hello/hi/{name}\""));
        assertFalse(json.contains("\"/api/hello/hi/{name}\""));
        assertFalse(json.contains("\"type\" : \"number\""));
        assertFalse(json.contains("\"format\" : \"float\""));
        assertFalse(json.contains("\"example\" : \"<hello>Hi</hello>\""));
        assertTrue(json.contains("\"example\" : \"Donald Duck\""));
        assertFalse(json.contains("\"success\" : { \"value\" : \"123\" }"));
        assertFalse(json.contains("\"error\" : { \"value\" : \"-1\" }"));
        assertTrue(json.contains("\"type\" : \"array\""));

        context.stop();
    }

}
