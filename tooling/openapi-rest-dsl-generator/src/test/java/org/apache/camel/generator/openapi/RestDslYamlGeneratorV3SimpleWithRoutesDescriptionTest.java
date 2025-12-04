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

package org.apache.camel.generator.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RestDslYamlGeneratorV3SimpleWithRoutesDescriptionTest {

    static OpenAPI document;

    @BeforeAll
    public static void readOpenApiDoc() throws Exception {
        document = new OpenAPIV3Parser()
                .read("src/test/resources/org/apache/camel/generator/openapi/openapi-spec-description.json");
    }

    @Test
    public void shouldGenerateYamlWithDefaults() throws Exception {
        final CamelContext context = new DefaultCamelContext();

        final String yaml = RestDslGenerator.toYaml(document).generate(context, true);
        final URI file = RestDslXmlGeneratorV3Test.class
                .getResource("/OpenApiV3PetstoreSimpleWithRoutesDescriptionYaml.txt")
                .toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(yaml).isEqualTo(expectedContent);
    }
}
