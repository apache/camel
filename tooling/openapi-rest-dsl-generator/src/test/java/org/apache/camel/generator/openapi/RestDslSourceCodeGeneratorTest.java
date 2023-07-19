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

import io.apicurio.datamodels.models.openapi.v20.OpenApi20Document;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20DocumentImpl;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20Info;
import io.apicurio.datamodels.models.openapi.v20.OpenApi20InfoImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslSourceCodeGeneratorTest {

    @Test
    public void shouldCreatePackageNamesFromHostnames() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();
        openapi.setHost("api.example.org");

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi)).isEqualTo("org.example.api");
    }

    @Test
    public void shouldCreatePackageNamesFromHostnamesWithPorts() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();
        openapi.setHost("api.example.org:8080");

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi)).isEqualTo("org.example.api");
    }

    @Test
    public void shouldGenerateClassNameFromTitle() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();
        final OpenApi20Info info = new OpenApi20InfoImpl();
        info.setTitle("Example API");
        openapi.setInfo(info);
        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi)).isEqualTo("ExampleAPI");
    }

    @Test
    public void shouldGenerateClassNameFromTitleWithNonValidJavaIdentifiers() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();
        final OpenApi20Info info = new OpenApi20InfoImpl();
        info.setTitle("Example-API 2.0");
        openapi.setInfo(info);
        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi)).isEqualTo("ExampleAPI20");
    }

    @Test
    public void shouldUseDefaultClassNameIfInfoOrTitleIsNotPresent() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();

        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);

        openapi.setInfo(new OpenApi20InfoImpl());
        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);
    }

    @Test
    public void shouldUseDefaultClassNameIfTitleContainsOnlyNonValidJavaIdentifiers() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();
        final OpenApi20Info info = new OpenApi20InfoImpl();
        info.setTitle("\\%/4");
        openapi.setInfo(info);

        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameForLocalhost() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();
        openapi.setHost("localhost");

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameForLocalhostWithPort() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();
        openapi.setHost("localhost:8080");

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameIfNoHostIsSpecified() {
        final OpenApi20Document openapi = new OpenApi20DocumentImpl();

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }
}
