/**
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
package org.apache.camel.generator.swagger;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslXmlGeneratorTest {

    final Swagger swagger = new SwaggerParser().read("petstore.json");

    @Test
    public void shouldGenerateXml() throws Exception {
        final CamelContext context = new DefaultCamelContext();

        final String xml = RestDslGenerator.toXml(swagger).generate(context);
        assertThat(xml).isNotEmpty();
        assertThat(xml.contains("http://camel.apache.org/schema/spring"));
    }

    @Test
    public void shouldGenerateBlueprintXml() throws Exception {
        final CamelContext context = new DefaultCamelContext();

        final String xml = RestDslGenerator.toXml(swagger).withBlueprint().generate(context);
        assertThat(xml).isNotEmpty();
        assertThat(xml.contains("http://camel.apache.org/schema/blueprint"));
    }

    @Test
    public void shouldGenerateXmlWithDefaults() throws Exception {
        final CamelContext context = new DefaultCamelContext();

        final String xml = RestDslGenerator.toXml(swagger).generate(context);

        final URI file = RestDslGeneratorTest.class.getResource("/SwaggerPetstoreXml.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(xml).isEqualToIgnoringWhitespace(expectedContent);
    }

    @Test
    public void shouldGenerateXmlWithRestComponent() throws Exception {
        final CamelContext context = new DefaultCamelContext();

        final String xml = RestDslGenerator.toXml(swagger).withRestComponent("servlet").withRestContextPath("/foo").generate(context);

        final URI file = RestDslGeneratorTest.class.getResource("/SwaggerPetstoreWithRestComponentXml.txt").toURI();
        final String expectedContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);

        assertThat(xml).isEqualTo(expectedContent);
    }


}
