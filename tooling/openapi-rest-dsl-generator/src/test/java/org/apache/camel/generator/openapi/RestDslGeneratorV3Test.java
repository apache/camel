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

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Document;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslGeneratorV3Test {

    static OpenApi30Document document;

    final Instant generated = Instant.parse("2017-10-17T00:00:00.000Z");

    @Test
    public void shouldCreateDefinitions() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            final RestsDefinition definition = RestDslGenerator.toDefinition(document).generate();
            assertThat(definition).isNotNull();
            assertThat(definition.getRests()).hasSize(1);
            assertThat(definition.getRests().get(0).getPath()).isEqualTo("/api/v3");
        }
    }

    @Test
    public void shouldGenerateSourceCodeWithDefaults() throws Exception {
        final StringBuilder code = new StringBuilder();

        RestDslGenerator.toAppendable(document)
                .withGeneratedTime(generated)
                .generate(code);

        final URI file = RestDslGeneratorV3Test.class.getResource("/OpenApiV3Petstore.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
        assertThat(code.toString()).isEqualTo(expectedContent);
    }

    @Test
    public void shouldGenerateSourceCodeWithFilter() throws Exception {
        final StringBuilder code = new StringBuilder();

        RestDslGenerator.toAppendable(document)
                .withGeneratedTime(generated)
                .withClassName("MyRestRoute")
                .withPackageName("com.example")
                .withIndent("\t")
                .withSourceCodeTimestamps()
                .withOperationFilter("find*,deletePet,updatePet")
                .withDestinationGenerator(o -> "direct:rest-" + o.getOperationId())
                .generate(code);

        final URI file = RestDslGeneratorV3Test.class.getResource("/MyRestRouteFilterV3.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
        assertThat(code.toString()).isEqualTo(expectedContent);
    }

    @Test
    public void shouldGenerateSourceCodeWithOptions() throws Exception {
        final StringBuilder code = new StringBuilder();

        RestDslGenerator.toAppendable(document)
                .withGeneratedTime(generated)
                .withClassName("MyRestRoute")
                .withPackageName("com.example")
                .withIndent("\t")
                .withSourceCodeTimestamps()
                .withDestinationGenerator(o -> "direct:rest-" + o.getOperationId())
                .generate(code);

        final URI file = RestDslGeneratorV3Test.class.getResource("/MyRestRouteV3.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
        assertThat(code.toString()).isEqualTo(expectedContent);
    }

    @Test
    public void shouldGenerateSourceCodeWithRestComponent() throws Exception {
        final StringBuilder code = new StringBuilder();

        RestDslGenerator.toAppendable(document)
                .withGeneratedTime(generated)
                .withRestComponent("servlet")
                .withRestContextPath("/")
                .generate(code);

        final URI file = RestDslGeneratorV3Test.class.getResource("/OpenApiV3PetstoreWithRestComponent.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(code.toString()).isEqualTo(expectedContent);
    }

    @BeforeAll
    public static void readOpenApiDoc() throws Exception {
        try (InputStream is = RestDslGeneratorTest.class.getResourceAsStream("openapi-spec.json")) {
            String json = IOHelper.loadText(is);
            document = (OpenApi30Document) Library.readDocumentFromJSONString(json);
        }
    }
}
