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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiModelApiSecurityRequirementsTest extends CamelTestSupport {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest()
                    .securityDefinitions()
                        .oauth2("petstore_auth")
                            .authorizationUrl("https://petstore.swagger.io/oauth/dialog")
                        .end()
                    .apiKey("api_key")
                        .withHeader("myHeader").end()
                    .end()
                    .security("petstore_auth", "read, write")
                    .security("api_key");
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
        config.setLicenseUrl("https://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion("2.0");
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);
        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        log.info(json);

        assertTrue(json.contains("\"securityDefinitions\" : {"));
        assertTrue(json.contains("\"type\" : \"oauth2\""));
        assertTrue(json.contains("\"authorizationUrl\" : \"https://petstore.swagger.io/oauth/dialog\""));
        assertTrue(json.contains("\"flow\" : \"implicit\""));
        assertTrue(json.contains("\"type\" : \"apiKey\","));
        assertTrue(json.contains("\"security\" : [ {"));
        assertTrue(json.contains("\"petstore_auth\" : [ \"read\", \"write\" ]"));
        assertTrue(json.contains("\"api_key\" : [ ]"));
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
        config.setLicenseUrl("https://www.apache.org/licenses/LICENSE-2.0.html");
        config.setVersion(version);
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        log.info(json);

        assertTrue(json.contains("securitySchemes"));
        assertTrue(json.contains("\"type\" : \"oauth2\""));
        assertTrue(json.contains("\"authorizationUrl\" : \"https://petstore.swagger.io/oauth/dialog\""));
        assertTrue(json.contains("\"flows\" : {"));
        assertTrue(json.contains("\"implicit\""));
        assertTrue(json.contains("\"security\" : [ {"));
        assertTrue(json.contains("\"petstore_auth\" : [ \"read\", \"write\" ]"));
        assertTrue(json.contains("\"api_key\" : [ ]"));
    }
}
