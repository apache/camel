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
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.openapi.model.AllOfFormWrapper;
import org.apache.camel.openapi.model.AnyOfFormWrapper;
import org.apache.camel.openapi.model.OneOfFormWrapper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestOpenApiV3XOfTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RestOpenApiV3XOfTest.class);

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest("/form")
                        .post("/oneOf")
                        .tag("OneOf")
                        .bindingMode(RestBindingMode.json)
                        .description("OneOf rest service")

                        .consumes("application/json")
                        .produces("application/json")
                         .type(OneOfFormWrapper.class)
                        .responseMessage()
                        .code(200).message("Ok")
                        .endResponseMessage()

                        .to("direct:res");

                rest("/form")
                        .post("/allOf")
                        .tag("AllOf")
                        .bindingMode(RestBindingMode.json)
                        .description("AllOf rest service")

                        .consumes("application/json")
                        .produces("application/json")
                        .type(AllOfFormWrapper.class)
                        .responseMessage()
                        .code(200).message("Ok")
                        .endResponseMessage()

                        .to("direct:res");

                rest("/form")
                        .post("/anyOf")
                        .tag("AnyOf")
                        .bindingMode(RestBindingMode.json)
                        .description("AnyOf rest service")

                        .consumes("application/json")
                        .produces("application/json")
                        .type(AnyOfFormWrapper.class)
                        .responseMessage()
                        .code(200).message("Ok")
                        .endResponseMessage()

                        .to("direct:res");

                from("direct:res")
                        .setBody(constant("{\"result\": \"Ok\"}"));
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.0", "3.1" })
    public void testReaderReadOneOf(String version) throws Exception {
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
        LOG.info(json);
        json = json.replace("\n", " ").replaceAll("\\s+", " ");

        assertTrue(json.contains(
                "\"XOfFormA\" : { \"type\" : \"object\", \"properties\" : { \"code\" : { \"type\" : \"string\" }, \"a\" : { \"type\" : \"string\" }, \"b\" : { \"type\" : \"integer\", \"format\" : \"int32\" } },"));
        assertTrue(json.contains(
                "\"XOfFormB\" : { \"type\" : \"object\", \"properties\" : { \"code\" : { \"type\" : \"string\" }, \"x\" : { \"type\" : \"integer\", \"format\" : \"int32\" }, \"y\" : { \"type\" : \"string\" } },"));

        if (config.isOpenApi30()) {
            assertTrue(json.contains(
                    "\"OneOfFormWrapper\" : { \"type\" : \"object\", \"properties\" : { \"formType\" : { \"type\" : \"string\" }, \"form\" : { \"$ref\" : \"#/components/schemas/OneOfForm\" } },"));
        } else if (config.isOpenApi31()) {
            assertTrue(json.contains(
                    "\"OneOfFormWrapper\" : { \"type\" : \"object\", \"properties\" : { \"formType\" : { \"type\" : \"string\" }, \"form\" : { \"discriminator\" : { \"propertyName\" : \"code\", \"mapping\" : { \"a-123\" : \"#/components/schemas/org.apache.camel.openapi.model.XOfFormA\", \"b-456\" : \"#/components/schemas/org.apache.camel.openapi.model.XOfFormB\" } }, \"oneOf\" : [ { \"$ref\" : \"#/components/schemas/XOfFormA\" }, { \"$ref\" : \"#/components/schemas/XOfFormB\" } ], \"x-className\" : { \"format\" : \"org.apache.camel.openapi.model.OneOfForm\", \"type\" : \"string\" } } }, \"x-className\" : { \"format\" : \"org.apache.camel.openapi.model.OneOfFormWrapper\", \"type\" : \"string\" } }"));
        }
        assertTrue(json.contains(
                "\"OneOfForm\" : { \"type\" : \"object\", " +
                                 "\"discriminator\" : { \"propertyName\" : \"code\", \"mapping\" : " +
                                 "{ \"a-123\" : \"#/components/schemas/org.apache.camel.openapi.model.XOfFormA\", " +
                                 "\"b-456\" : \"#/components/schemas/org.apache.camel.openapi.model.XOfFormB\" } }, " +
                                 "\"oneOf\" : [ { \"$ref\" : \"#/components/schemas/XOfFormA\" }, { \"$ref\" : \"#/components/schemas/XOfFormB\" } ],"));

        context.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.0", "3.1" })
    public void testReaderReadAllOf(String version) throws Exception {
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
        LOG.info(json);
        json = json.replace("\n", " ").replaceAll("\\s+", " ");

        assertTrue(json.contains(
                "\"AllOfFormWrapper\" : { \"type\" : \"object\", \"properties\" : { \"fullForm\" : { \"$ref\" : \"#/components/schemas/AllOfForm\" } },"));
        assertTrue(json.contains(
                "\"allOf\" : [ { \"$ref\" : \"#/components/schemas/XOfFormA\" }, { \"$ref\" : \"#/components/schemas/XOfFormB\" } ]"));

        context.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.0", "3.1" })
    public void testReaderReadAnyOf(String version) throws Exception {
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
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        LOG.info(json);
        json = json.replace("\n", " ").replaceAll("\\s+", " ");

        assertTrue(json.contains(
                "\"AnyOfFormWrapper\" : { \"type\" : \"object\", \"properties\" : { \"formElements\" : { \"$ref\" : \"#/components/schemas/AnyOfForm\" } },"));
        assertTrue(json.contains(
                "\"anyOf\" : [ { \"$ref\" : \"#/components/schemas/XOfFormA\" }, { \"$ref\" : \"#/components/schemas/XOfFormB\" } ]"));

        context.stop();
    }

}
