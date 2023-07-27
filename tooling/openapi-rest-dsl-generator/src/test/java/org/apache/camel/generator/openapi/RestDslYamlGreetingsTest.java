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

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.openapi.OpenApiDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslYamlGreetingsTest {

    static OpenApiDocument document;

    @BeforeAll
    public static void readOpenApiDoc() throws Exception {
        try (InputStream is = RestDslYamlGreetingsTest.class.getResourceAsStream("greetings-spec.json")) {
            String json = IOHelper.loadText(is);
            document = (OpenApiDocument) Library.readDocumentFromJSONString(json);
        }
    }

    @Test
    public void shouldGenerateYamlWithDefaults() throws Exception {
        final CamelContext context = new DefaultCamelContext();

        final String yaml = RestDslGenerator.toYaml(document).generate(context);
        final URI file = RestDslGeneratorTest.class.getResource("/GreetingsYaml.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(yaml).isEqualTo(expectedContent);
    }

}
