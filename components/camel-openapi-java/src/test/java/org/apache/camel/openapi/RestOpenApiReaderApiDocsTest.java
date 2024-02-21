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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiReaderApiDocsTest extends CamelTestSupport {

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
                        .dataType("string").description("Who is it").endParam().to("log:hi").get("/bye/{name}").apiDocs(false)
                        .description("Saying bye").param().name("name")
                        .type(RestParamType.path).dataType("string").description("Who is it").endParam().responseMessage()
                        .code(200).message("A reply message").endResponseMessage()
                        .to("log:bye").post("/bye").apiDocs(false).description("To update the greeting message")
                        .consumes("application/xml").produces("application/xml").param()
                        .name("greeting").type(RestParamType.body).dataType("string").description("Message to use as greeting")
                        .endParam().to("log:bye");
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.1", "3.0", "2.0" })
    public void testReaderRead(String version) throws Exception {
        BeanConfig config = getBeanConfig();
        config.setVersion(version);
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        log.info(json);

        if (version.equals("2.0")) {
            assertTrue(json.contains("\"host\" : \"" + config.getHost() + "\""));
            assertTrue(json.contains("\"basePath\" : \"" + config.getBasePath() + "\""));
        } else {
            for (String schema : config.getSchemes()) {
                assertTrue(json.contains("\"url\" : \"" + schema + "://" + config.getHost() + config.getBasePath() + "\""));
            }
        }

        assertFalse(json.contains("\"/hello/bye\""));
        assertFalse(json.contains("\"summary\" : \"To update the greeting message\""));
        assertFalse(json.contains("\"/hello/bye/{name}\""));
        assertTrue(json.contains("\"/hello/hi/{name}\""));

        context.stop();
    }

    protected BeanConfig getBeanConfig() {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");

        return config;
    }
}
