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
import io.swagger.v3.oas.models.info.Info;
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

public class RestOpenApiReaderTest extends CamelTestSupport {

    private Logger log = LoggerFactory.getLogger(getClass());

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest("/hello")
                        .consumes("application/json")
                        .produces("application/json")

                        .get("/hi/{name}")
                        .description("Saying hi")
                        .param()
                        .name("name")
                        .type(RestParamType.path)
                        .dataType("string")
                        .description("Who is it")
                        .example("Donald Duck")
                        .endParam()
                        .param()
                        .name("filter")
                        .description("Filters to apply to the entity.")
                        .type(RestParamType.query)
                        .dataType("array")
                        .arrayType("date-time")
                        .endParam()

                        .to("log:hi")

                        .get("/bye/{name}")
                        .description("Saying bye")
                        .param()
                        .name("name")
                        .type(RestParamType.path)
                        .dataType("string")
                        .description("Who is it")
                        .example("Donald Duck")
                        .endParam()
                        .responseMessage()
                        .code(200)
                        .message("A reply number")
                        .responseModel(float.class)
                        .example("success", "123")
                        .example("error", "-1")
                        .endResponseMessage()
                        .to("log:bye")

                        .get("/array/params")
                        .description("Array params")
                        .param()
                        .name("string_array")
                        .dataType("array")
                        .arrayType("string")
                        .allowableValues("A", "B", "C")
                        .endParam()
                        .param()
                        .name("int_array")
                        .dataType("array")
                        .arrayType("int")
                        .allowableValues("1", "2", "3")
                        .endParam()
                        .param()
                        .name("integer_array")
                        .dataType("array")
                        .arrayType("integer")
                        .allowableValues("1", "2", "3")
                        .endParam()
                        .param()
                        .name("long_array")
                        .dataType("array")
                        .arrayType("long")
                        .allowableValues("1", "2", "3")
                        .endParam()
                        .param()
                        .name("float_array")
                        .dataType("array")
                        .arrayType("float")
                        .allowableValues("1.0", "2.0", "3.0")
                        .endParam()
                        .param()
                        .name("double_array")
                        .dataType("array")
                        .arrayType("double")
                        .allowableValues("1.0", "2.0", "3.0")
                        .endParam()
                        .param()
                        .name("boolean_array")
                        .dataType("array")
                        .arrayType("boolean")
                        .allowableValues("true", "false")
                        .endParam()
                        .param()
                        .name("byte_array")
                        .dataType("array")
                        .arrayType("byte")
                        .allowableValues("1", "2", "3")
                        .endParam()
                        .param()
                        .name("binary_array")
                        .dataType("array")
                        .arrayType("binary")
                        .allowableValues("1", "2", "3")
                        .endParam()
                        .param()
                        .name("date_array")
                        .dataType("array")
                        .arrayType("date")
                        .allowableValues("2023-01-01", "2023-02-02", "2023-03-03")
                        .endParam()
                        .param()
                        .name("datetime_array")
                        .dataType("array")
                        .arrayType("date-time")
                        .allowableValues("2011-12-03T10:15:30+01:00")
                        .endParam()
                        .param()
                        .name("password_array")
                        .dataType("array")
                        .arrayType("password")
                        .allowableValues("foo", "bar", "cheese")
                        .endParam()
                        .to("log:array")

                        .post("/bye")
                        .description("To update the greeting message")
                        .consumes("application/xml")
                        .produces("application/xml")
                        .outType(String.class)
                        .param()
                        .name("greeting")
                        .type(RestParamType.body)
                        .dataType("string")
                        .description("Message to use as greeting")
                        .example("application/xml", "<hello>Hi</hello>")
                        .endParam()
                        .to("log:bye");

                rest("/tag")
                        .get("single")
                        .tag("Organisation")
                        .outType(String.class)
                        .param()
                        .name("body")
                        .type(RestParamType.body)
                        .dataType("string")
                        .description("Message body")
                        .endParam()
                        .to("log:bye");

                rest("/tag")
                        .get("multiple/a")
                        .tag("Organisation,Group A")
                        .outType(String.class)
                        .param()
                        .name("body")
                        .type(RestParamType.body)
                        .dataType("string")
                        .description("Message body")
                        .endParam()

                        .to("log:bye");

                rest("/tag")
                        .get("multiple/b")
                        .tag("Organisation,Group B")
                        .outType(String.class)
                        .param()
                        .name("body")
                        .type(RestParamType.body)
                        .dataType("string")
                        .description("Message body")
                        .endParam()
                        .to("log:bye");

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

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        String flatJson = json.replace("\n", " ").replaceAll("\\s+", " ");

        log.info(json);

        assertTrue(json.contains("\"host\" : \"localhost:8080\""));
        assertTrue(json.contains("\"basePath\" : \"/api\""));
        assertTrue(json.contains("\"/hello/bye\""));
        assertTrue(json.contains("\"summary\" : \"To update the greeting message\""));
        assertTrue(json.contains("\"/hello/bye/{name}\""));
        assertTrue(json.contains("\"/hello/hi/{name}\""));
        assertTrue(json.contains("\"type\" : \"number\""));
        assertTrue(json.contains("\"format\" : \"float\""));
        assertTrue(json.contains("\"application/xml\" : \"<hello>Hi</hello>\""));
        assertTrue(json.contains("\"x-example\" : \"Donald Duck\""));
        assertTrue(json.contains("\"success\" : \"123\""));
        assertTrue(json.contains("\"error\" : \"-1\""));
        assertTrue(json.contains("\"type\" : \"array\""));
        assertTrue(json.contains("\"enum\" : [ \"A\", \"B\", \"C\" ]"));
        assertTrue(json.contains("\"enum\" : [ 1, 2, 3 ]"));
        assertTrue(json.contains("\"enum\" : [ 1.0, 2.0, 3.0 ]"));
        assertTrue(json.contains("\"enum\" : [ \"true\", \"false\" ]"));
        assertTrue(json.contains("\"enum\" : [ \"MQ==\", \"Mg==\", \"Mw==\" ]"));
        assertTrue(json.contains("\"enum\" : [ \"2023-01-01\", \"2023-02-02\", \"2023-03-03\" ]"));
        assertTrue(json.contains("\"enum\" : [ \"2011-12-03T10:15:30+01:00\" ]"));
        assertTrue(json.contains("\"enum\" : [ \"foo\", \"bar\", \"cheese\" ]"));

        flatJson = flatJson.replaceAll("\"operationId\" : \"[^\\\"]*\", ", "").replaceAll("\"summary\" : \"[^\\\"]*\", ", "");
        log.info(flatJson);
        assertTrue(flatJson.contains(
                "\"/hello/bye\" : { \"post\" : { \"tags\" : [ \"/hello\" ], \"consumes\" : [ \"application/xml\" ], \"produces\" : [ \"application/xml\" ], "));
        assertTrue(flatJson.contains(
                "\"/tag/single\" : { \"get\" : { \"tags\" : [ \"Organisation\" ], \"consumes\" : [ \"application/json\" ], \"produces\" : [ \"application/json\" ], "));
        assertTrue(flatJson.contains(
                "\"/tag/multiple/a\" : { \"get\" : { \"tags\" : [ \"Organisation\", \"Group A\" ], \"consumes\" : [ \"application/json\" ], \"produces\" : [ \"application/json\" ], "));
        assertTrue(flatJson.contains(
                "\"/tag/multiple/b\" : { \"get\" : { \"tags\" : [ \"Organisation\", \"Group B\" ], \"consumes\" : [ \"application/json\" ], \"produces\" : [ \"application/json\" ], "));
        assertTrue(flatJson.contains(
                "\"tags\" : [ { \"name\" : \"/hello\" }, { \"name\" : \"Organisation\" }, { \"name\" : \"Group A\" }, { \"name\" : \"Group B\" } ]"));

        context.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.0", "3.1" })
    public void testReaderReadV3(String version) throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        Info info = new Info();
        config.setInfo(info);
        config.setVersion(version);

        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        String json = RestOpenApiSupport.getJsonFromOpenAPIAsString(openApi, config);
        log.info(json);
        json = json.replace("\n", " ").replaceAll("\\s+", " ");

        assertTrue(json.contains("\"openapi\" : \"" + config.getVersion() + "\""));
        assertTrue(json.contains("\"url\" : \"http://localhost:8080/api\""));
        assertTrue(json.contains("\"/hello/bye\""));
        assertTrue(json.contains("\"summary\" : \"To update the greeting message\""));
        assertTrue(json.contains("\"/hello/bye/{name}\""));
        assertTrue(json.contains("\"/hello/hi/{name}\""));
        assertTrue(json.contains("\"type\" : \"number\""));
        assertTrue(json.contains("\"format\" : \"float\""));
        assertTrue(json.contains("\"example\" : \"<hello>Hi</hello>\""));
        assertTrue(json.contains("\"example\" : \"Donald Duck\""));
        assertTrue(json.contains("\"success\" : { \"value\" : \"123\" }"));
        assertTrue(json.contains("\"error\" : { \"value\" : \"-1\" }"));
        assertTrue(json.contains("\"type\" : \"array\""));
        assertTrue(json.contains("\"format\" : \"date-time\""));
        assertTrue(json.contains("\"enum\" : [ \"A\", \"B\", \"C\" ]"));
        assertTrue(json.contains("\"enum\" : [ 1, 2, 3 ]"));
        assertTrue(json.contains("\"enum\" : [ 1.0, 2.0, 3.0 ]"));
        assertTrue(json.contains("\"enum\" : [ true, false ]"));
        assertTrue(json.contains("\"enum\" : [ \"MQ==\", \"Mg==\", \"Mw==\" ]"));
        assertTrue(json.contains("\"enum\" : [ \"2023-01-01\", \"2023-02-02\", \"2023-03-03\" ]"));
        assertTrue(json.contains("\"enum\" : [ \"2011-12-03T10:15:30+01:00\" ]"));
        assertTrue(json.contains("\"enum\" : [ \"foo\", \"bar\", \"cheese\" ]"));

        assertTrue(json.contains("\"/hello/bye/{name}\" : { \"get\" : { \"tags\" : [ \"/hello\" ],"));
        assertTrue(json.matches(".*\"/tag/single\" : \\{ \"get\" : .* \"tags\" : \\[ \"Organisation\" ],.*"));
        assertTrue(
                json.matches(".*\"/tag/multiple/a\" : \\{ \"get\" : .* \"tags\" : \\[ \"Organisation\", \"Group A\" ],.*"));
        assertTrue(
                json.matches(".*\"/tag/multiple/b\" : \\{ \"get\" : .*\"tags\" : \\[ \"Organisation\", \"Group B\" ],.*"));
        assertTrue(json.contains(
                "\"tags\" : [ { \"name\" : \"/hello\" }, { \"name\" : \"Organisation\" }, { \"name\" : \"Group A\" }, { \"name\" : \"Group B\" } ]"));
        context.stop();
    }

}
