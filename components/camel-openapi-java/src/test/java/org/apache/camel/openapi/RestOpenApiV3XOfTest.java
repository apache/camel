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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.openapi.models.OasDocument;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.openapi.model.AllOfFormWrapper;
import org.apache.camel.openapi.model.AnyOfFormWrapper;
import org.apache.camel.openapi.model.OneOfFormWrapper;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
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

    @Test
    public void testReaderReadOneOf() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("https://www.apache.org/licenses/LICENSE-2.0.html");

        RestOpenApiReader reader = new RestOpenApiReader();
        OasDocument openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);
        assertNotNull(openApi);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Object dump = Library.writeNode(openApi);
        String json = mapper.writeValueAsString(dump);

        LOG.info(json);
        json = json.replace("\n", " ").replaceAll("\\s+", " ");

        assertTrue(json.contains(
                "\"XOfFormA\" : { \"type\" : \"object\", \"properties\" : { \"code\" : { \"type\" : \"string\" }, \"a\" : { \"type\" : \"string\" }, \"b\" : { \"format\" : \"int32\", \"type\" : \"integer\" } },"));
        assertTrue(json.contains(
                "\"XOfFormB\" : { \"type\" : \"object\", \"properties\" : { \"code\" : { \"type\" : \"string\" }, \"x\" : { \"format\" : \"int32\", \"type\" : \"integer\" }, \"y\" : { \"type\" : \"string\" } },"));

        assertTrue(json.contains(
                "\"OneOfFormWrapper\" : { \"type\" : \"object\", \"properties\" : { \"formType\" : { \"type\" : \"string\" }, \"form\" : { \"$ref\" : \"#/components/schemas/OneOfForm\" } },"));
        assertTrue(json.contains(
                "\"OneOfForm\" : { \"oneOf\" : [ { \"$ref\" : \"#/components/schemas/XOfFormA\" }, { \"$ref\" : \"#/components/schemas/XOfFormB\" } ],"
                                 +
                                 " \"discriminator\" : { \"propertyName\" : \"code\", \"mapping\" : " +
                                 "{ \"a-123\" : \"#/components/schemas/org.apache.camel.openapi.model.XOfFormA\", \"b-456\" : \"#/components/schemas/org.apache.camel.openapi.model.XOfFormB\" } },"));

        context.stop();
    }

    @Test
    public void testReaderReadAllOf() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("https://www.apache.org/licenses/LICENSE-2.0.html");

        RestOpenApiReader reader = new RestOpenApiReader();
        OasDocument openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);
        assertNotNull(openApi);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Object dump = Library.writeNode(openApi);
        String json = mapper.writeValueAsString(dump);

        LOG.info(json);
        json = json.replace("\n", " ").replaceAll("\\s+", " ");

        assertTrue(json.contains(
                "\"AllOfFormWrapper\" : { \"type\" : \"object\", \"properties\" : { \"fullForm\" : { \"$ref\" : \"#/components/schemas/AllOfForm\" } },"));
        assertTrue(json.contains(
                "\"AllOfForm\" : { \"allOf\" : [ { \"$ref\" : \"#/components/schemas/XOfFormA\" }, { \"$ref\" : \"#/components/schemas/XOfFormB\" } ],"));

        context.stop();
    }

    @Test
    public void testReaderReadAnyOf() throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setTitle("Camel User store");
        config.setLicense("Apache 2.0");
        config.setLicenseUrl("https://www.apache.org/licenses/LICENSE-2.0.html");

        RestOpenApiReader reader = new RestOpenApiReader();
        OasDocument openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);
        assertNotNull(openApi);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Object dump = Library.writeNode(openApi);
        String json = mapper.writeValueAsString(dump);

        LOG.info(json);
        json = json.replace("\n", " ").replaceAll("\\s+", " ");

        assertTrue(json.contains(
                "\"AnyOfFormWrapper\" : { \"type\" : \"object\", \"properties\" : { \"formElements\" : { \"$ref\" : \"#/components/schemas/AnyOfForm\" } },"));
        assertTrue(json.contains(
                "\"AnyOfForm\" : { \"anyOf\" : [ { \"$ref\" : \"#/components/schemas/XOfFormA\" }, { \"$ref\" : \"#/components/schemas/XOfFormB\" } ],"));

        context.stop();
    }

}
