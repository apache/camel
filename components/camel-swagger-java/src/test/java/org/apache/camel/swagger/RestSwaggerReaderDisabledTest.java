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
package org.apache.camel.swagger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Swagger;
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

public class RestSwaggerReaderDisabledTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(RestSwaggerReaderDisabledTest.class);

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest("/hello").consumes("application/json").produces("application/json").get("/hi/{name}")
                    .description("Saying hi").deprecated().param().name("name").type(RestParamType.path)
                    .dataType("string").description("Who is it").example("Donald Duck").endParam().to("log:hi")
                    .get("/bye/{name}").disabled().description("Saying bye").deprecated().param().name("name")
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
        RestSwaggerReader reader = new RestSwaggerReader();

        Swagger swagger
                = reader.read(context, context.getRestDefinitions(), config, context.getName(), new DefaultClassResolver());
        assertNotNull(swagger);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(swagger);

        LOG.info(json);

        assertTrue(json.contains("\"host\" : \"localhost:8080\""));
        assertTrue(json.contains("\"basePath\" : \"/api\""));
        assertFalse(json.contains("\"/hello/bye\""));
        assertFalse(json.contains("\"summary\" : \"To update the greeting message\""));
        assertFalse(json.contains("\"/hello/bye/{name}\""));
        assertTrue(json.contains("\"/hello/hi/{name}\""));
        assertFalse(json.contains("\"type\" : \"number\""));
        assertFalse(json.contains("\"format\" : \"float\""));
        assertFalse(json.contains("\"application/xml\" : \"<hello>Hi</hello>\""));
        assertTrue(json.contains("\"x-example\" : \"Donald Duck\""));
        assertFalse(json.contains("\"success\" : \"123\""));
        assertFalse(json.contains("\"error\" : \"-1\""));
        assertTrue(json.contains("\"type\" : \"string\""));
        assertTrue(json.contains("\"deprecated\" : true"));

        JsonNode jsonNode = new ObjectMapper().readValue(json, JsonNode.class);
        assertNotNull(jsonNode.get("paths").get("/hello/hi/{name}").get("get").get("deprecated"));
        assertTrue(jsonNode.get("paths").get("/hello/hi/{name}").get("get").get("deprecated").asBoolean());

        context.stop();
    }
}
