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

import io.apicurio.datamodels.openapi.models.OasInfo;
import io.apicurio.datamodels.openapi.v2.models.Oas20Document;
import io.apicurio.datamodels.openapi.v2.models.Oas20Info;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslSourceCodeGeneratorTest {

    @Test
    public void shouldCreatePackageNamesFromHostnames() {
        final Oas20Document openapi = new Oas20Document();
        openapi.host = "api.example.org";

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi)).isEqualTo("org.example.api");
    }

    @Test
    public void shouldCreatePackageNamesFromHostnamesWithPorts() {
        final Oas20Document openapi = new Oas20Document();
        openapi.host = "api.example.org:8080";

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi)).isEqualTo("org.example.api");
    }

    @Test
    public void shouldGenerateClassNameFromTitle() {
        final Oas20Document openapi = new Oas20Document();
        final OasInfo info = new Oas20Info();
        info.title = "Example API";
        openapi.info = info;
        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi)).isEqualTo("ExampleAPI");
    }

    @Test
    public void shouldGenerateClassNameFromTitleWithNonValidJavaIdentifiers() {
        final Oas20Document openapi = new Oas20Document();
        final OasInfo info = new Oas20Info();
        info.title = "Example-API 2.0";
        openapi.info = info;
        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi)).isEqualTo("ExampleAPI20");
    }

    @Test
    public void shouldUseDefaultClassNameIfInfoOrTitleIsNotPresent() {
        final Oas20Document openapi = new Oas20Document();

        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);

        openapi.info = new Oas20Info();
        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);
    }

    @Test
    public void shouldUseDefaultClassNameIfTitleContainsOnlyNonValidJavaIdentifiers() {
        final Oas20Document openapi = new Oas20Document();
        final OasInfo info = new Oas20Info();
        info.title = "\\%/4";
        openapi.info = info;

        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameForLocalhost() {
        final Oas20Document openapi = new Oas20Document();
        openapi.host = "localhost";

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameForLocalhostWithPort() {
        final Oas20Document openapi = new Oas20Document();
        openapi.host = "localhost:8080";

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameIfNoHostIsSpecified() {
        final Oas20Document openapi = new Oas20Document();

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
            .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }
}
