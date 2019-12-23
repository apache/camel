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
package org.apache.camel.generator.swagger;

import io.swagger.models.Info;
import io.swagger.models.Swagger;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslSourceCodeGeneratorTest {

    @Test
    public void shouldCreatePackageNamesFromHostnames() {
        final Swagger swagger = new Swagger();
        swagger.setHost("api.example.org");

        assertThat(RestDslSourceCodeGenerator.generatePackageName(swagger)).isEqualTo("org.example.api");
    }

    @Test
    public void shouldCreatePackageNamesFromHostnamesWithPorts() {
        final Swagger swagger = new Swagger();
        swagger.setHost("api.example.org:8080");

        assertThat(RestDslSourceCodeGenerator.generatePackageName(swagger)).isEqualTo("org.example.api");
    }

    @Test
    public void shouldGenerateClassNameFromTitle() {
        final Swagger swagger = new Swagger();
        swagger.info(new Info().title("Example API"));

        assertThat(RestDslSourceCodeGenerator.generateClassName(swagger)).isEqualTo("ExampleAPI");
    }

    @Test
    public void shouldGenerateClassNameFromTitleWithNonValidJavaIdentifiers() {
        final Swagger swagger = new Swagger();
        swagger.info(new Info().title("Example-API 2.0"));

        assertThat(RestDslSourceCodeGenerator.generateClassName(swagger)).isEqualTo("ExampleAPI20");
    }

    @Test
    public void shouldUseDefaultClassNameIfInfoOrTitleIsNotPresent() {
        final Swagger swagger = new Swagger();

        assertThat(RestDslSourceCodeGenerator.generateClassName(swagger))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);

        assertThat(RestDslSourceCodeGenerator.generateClassName(swagger.info(new Info())))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);
    }

    @Test
    public void shouldUseDefaultClassNameIfTitleContainsOnlyNonValidJavaIdentifiers() {
        final Swagger swagger = new Swagger();
        swagger.info(new Info().title("\\%/4"));

        assertThat(RestDslSourceCodeGenerator.generateClassName(swagger))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameForLocalhost() {
        final Swagger swagger = new Swagger();
        swagger.setHost("localhost");

        assertThat(RestDslSourceCodeGenerator.generatePackageName(swagger))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameForLocalhostWithPort() {
        final Swagger swagger = new Swagger();
        swagger.setHost("localhost:8080");

        assertThat(RestDslSourceCodeGenerator.generatePackageName(swagger))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameIfNoHostIsSpecified() {
        final Swagger swagger = new Swagger();

        assertThat(RestDslSourceCodeGenerator.generatePackageName(swagger))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }
}
