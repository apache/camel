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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.Parameter;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestSwaggerArrayEnumTest {

    @Test
    public void shouldGenerateEnumValuesForArraysAndNonArrays() throws Exception {
        final RestSwaggerReader reader = new RestSwaggerReader();

        final RestDefinition restDefinition = new RestDefinition();
        restDefinition.get("/operation").param().name("pathParam").type(RestParamType.path).dataType("string")
            .allowableValues("a", "b", "c").endParam()

            .param().name("queryParam").type(RestParamType.query).dataType("int").allowableValues("1", "2", "3")
            .endParam()

            .param().name("headerParam").type(RestParamType.header).dataType("float")
            .allowableValues("1.1", "2.2", "3.3").endParam()

            .param().name("pathArrayParam").type(RestParamType.path).dataType("array").arrayType("string")
            .allowableValues("a", "b", "c").endParam()

            .param().name("queryArrayParam").type(RestParamType.query).dataType("array").arrayType("int")
            .allowableValues("1", "2", "3").endParam()

            .param().name("headerArrayParam").type(RestParamType.header).dataType("array").arrayType("float")
            .allowableValues("1.1", "2.2", "3.3").endParam();

        final Swagger swagger = reader.read(Collections.singletonList(restDefinition), null, new BeanConfig(),
            "camel-1", new DefaultClassResolver());

        assertThat(swagger).isNotNull();
        final Map<String, Path> paths = swagger.getPaths();
        assertThat(paths).containsKey("/operation");
        final Operation getOperation = paths.get("/operation").getGet();
        assertThat(getOperation).isNotNull();
        final List<Parameter> parameters = getOperation.getParameters();
        assertThat(parameters).hasSize(6);

        ParameterAssert.assertThat(parameters.get(0)).hasName("pathParam").isGivenIn("path").isOfType("string")
            .hasEnumSpecifiedWith("a", "b", "c");

        ParameterAssert.assertThat(parameters.get(1)).hasName("queryParam").isGivenIn("query").isOfType("string")
            .hasEnumSpecifiedWith("1", "2", "3");

        ParameterAssert.assertThat(parameters.get(2)).hasName("headerParam").isGivenIn("header").isOfType("string")
            .hasEnumSpecifiedWith("1.1", "2.2", "3.3");

        ParameterAssert.assertThat(parameters.get(3)).hasName("pathArrayParam").isGivenIn("path").isOfType("array")
            .isOfArrayType("string").hasArrayEnumSpecifiedWith("a", "b", "c");

        ParameterAssert.assertThat(parameters.get(4)).hasName("queryParam").isGivenIn("query").isOfType("array")
            .isOfArrayType("int").hasArrayEnumSpecifiedWith(1, 2, 3);

        ParameterAssert.assertThat(parameters.get(5)).hasName("headerParam").isGivenIn("header").isOfType("array")
            .isOfArrayType("float").hasArrayEnumSpecifiedWith(1.1f, 2.2f, 3.3f);
    }

}
