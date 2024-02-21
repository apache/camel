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

import io.apicurio.datamodels.models.openapi.v30.OpenApi30Document;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30DocumentImpl;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Info;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30InfoImpl;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30Server;
import io.apicurio.datamodels.models.openapi.v30.OpenApi30ServerImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDslSourceCodeGeneratorV3Test {

    @Test
    public void shouldCreatePackageNamesFromHostnames() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();
        OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("http://api.example.org/");
        server.setDescription("test server url");
        openapi.addServer(server);

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi)).isEqualTo("org.example.api");
    }

    @Test
    public void shouldCreatePackageNamesFromHostnamesWithPorts() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();
        OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("http://api.example.org:8080/");
        server.setDescription("test server url");
        openapi.addServer(server);

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi)).isEqualTo("org.example.api");
    }

    @Test
    public void shouldGenerateClassNameFromTitle() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();
        final OpenApi30Info info = new OpenApi30InfoImpl();
        info.setTitle("Example API");
        openapi.setInfo(info);

        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi)).isEqualTo("ExampleAPI");
    }

    @Test
    public void shouldGenerateClassNameFromTitleWithNonValidJavaIdentifiers() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();
        final OpenApi30Info info = new OpenApi30InfoImpl();
        info.setTitle("Example-API 2.0");
        openapi.setInfo(info);

        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi)).isEqualTo("ExampleAPI20");
    }

    @Test
    public void shouldUseDefaultClassNameIfInfoOrTitleIsNotPresent() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();

        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);

        openapi.setInfo(new OpenApi30InfoImpl());
        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);
    }

    @Test
    public void shouldUseDefaultClassNameIfTitleContainsOnlyNonValidJavaIdentifiers() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();
        final OpenApi30Info info = new OpenApi30InfoImpl();
        info.setTitle("\\%/4");
        openapi.setInfo(info);

        assertThat(RestDslSourceCodeGenerator.generateClassName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_CLASS_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameForLocalhost() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();
        OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("http://localhost");
        server.setDescription("test server url");
        openapi.addServer(server);

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameForLocalhostWithPort() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();
        OpenApi30Server server = new OpenApi30ServerImpl();
        server.setUrl("http://localhost:8080");
        server.setDescription("test server url");
        openapi.addServer(server);

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }

    @Test
    public void shouldUseDefaultPackageNameIfNoHostIsSpecified() {
        final OpenApi30Document openapi = new OpenApi30DocumentImpl();

        assertThat(RestDslSourceCodeGenerator.generatePackageName(openapi))
                .isEqualTo(RestDslSourceCodeGenerator.DEFAULT_PACKAGE_NAME);
    }
}
