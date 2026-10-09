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

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The allowable values of an array parameter restrict the items of the array (OpenAPI 3: {@code items.enum}); an
 * {@code enum} on the array schema itself would only accept array values equal to one of the strings or numbers, that
 * is, no array at all.
 */
class RestOpenApiReaderArrayAllowableValuesTest extends CamelTestSupport {

    @BindToRegistry("dummy-rest")
    private DummyRestConsumerFactory factory = new DummyRestConsumerFactory();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                rest("/colors")
                        .get("/filter")
                        .param().name("colors").type(RestParamType.query).dataType("array").arrayType("string")
                        .allowableValues("red", "green", "blue").endParam()
                        .param().name("sizes").type(RestParamType.query).dataType("array").arrayType("integer")
                        .allowableValues("1", "2", "3").endParam()
                        .param().name("mode").type(RestParamType.query).dataType("string")
                        .allowableValues("fast", "slow").endParam()
                        .to("log:filter");
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = { "3.1", "3.0" })
    void testArrayAllowableValuesAreOnItems(String version) throws Exception {
        BeanConfig config = new BeanConfig();
        config.setHost("localhost:8080");
        config.setSchemes(new String[] { "http" });
        config.setBasePath("/api");
        config.setVersion(version);
        RestOpenApiReader reader = new RestOpenApiReader();

        OpenAPI openApi = reader.read(context, context.getRestDefinitions(), config, context.getName(),
                new DefaultClassResolver());
        assertNotNull(openApi);

        List<Parameter> parameters = openApi.getPaths().get("/colors/filter").getGet().getParameters();

        Schema<?> colors = schemaOf(parameters, "colors");
        assertNull(colors.getEnum(), "the array schema of 'colors' must not have an enum");
        assertEquals(List.of("red", "green", "blue"), colors.getItems().getEnum(), "the items of 'colors'");

        Schema<?> sizes = schemaOf(parameters, "sizes");
        assertNull(sizes.getEnum(), "the array schema of 'sizes' must not have an enum");
        assertEquals(List.of(1, 2, 3), sizes.getItems().getEnum(), "the items of 'sizes'");

        // a parameter that is not an array keeps its enum
        assertEquals(List.of("fast", "slow"), schemaOf(parameters, "mode").getEnum());

        context.stop();
    }

    private static Schema<?> schemaOf(List<Parameter> parameters, String name) {
        return parameters.stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow().getSchema();
    }
}
