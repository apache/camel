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
package org.apache.camel.dsl.jbang.export;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.dsl.jbang.export.common.CamelHome;
import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.camel.dsl.jbang.export.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exports through the Java API, without the CLI: the dependencies come from a resolver that writes the run settings, as
 * the silent run of camel-jbang-core does.
 */
class ProjectExporterTest {

    @TempDir
    Path dir;

    private final List<ExportRequest> resolved = new ArrayList<>();

    private final DependencyResolver resolver = request -> {
        resolved.add(request);
        Path settings = CamelHome.getWorkDir().resolve(ExportHelper.RUN_SETTINGS_FILE);
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, """
                camel.main.routesIncludePattern=file:%s
                dependency=camel:timer
                dependency=camel:log
                """.formatted(request.files.get(0)));
        return 0;
    };

    @Test
    void exportsCamelMainProject() throws Exception {
        Path route = dir.resolve("hello.camel.yaml");
        Files.writeString(route, """
                - from:
                    uri: timer:hello
                    steps:
                      - to: log:hello
                """);
        Path exportDir = dir.resolve("export");

        ExportRequest request = new ExportRequest();
        request.files.add(route.toString());
        request.runtime = RuntimeType.main;
        request.exportDir = exportDir.toString();
        request.mavenWrapper = false;
        request.quiet = true;

        StringPrinter printer = new StringPrinter();
        ExportContext context = new ExportContext().withPrinter(printer).withDependencyResolver(resolver);
        int exit = new ProjectExporter(context).export(request);

        assertThat(exit).as(printer.getOutput()).isZero();
        // the GAV is derived from the name of the first file
        assertThat(request.gav).isEqualTo("org.example.project:hello:1.0-SNAPSHOT");
        // the resolver gets a copy of the request, not the request of the caller
        assertThat(resolved).singleElement().isNotSameAs(request);

        String pom = Files.readString(exportDir.resolve("pom.xml"));
        assertThat(pom).contains("<artifactId>hello</artifactId>", "<artifactId>camel-timer</artifactId>",
                "<artifactId>camel-log</artifactId>");
        assertThat(exportDir.resolve("src/main/resources/camel/hello.camel.yaml")).hasSameTextualContentAs(route);
    }

    @Test
    void requiresRuntime() throws Exception {
        ExportRequest request = new ExportRequest();
        request.files.add(dir.resolve("missing.camel.yaml").toString());

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(resolver))
                .export(request);

        assertThat(exit).isEqualTo(1);
        assertThat(printer.getOutput()).contains("The runtime option must be specified");
        assertThat(resolved).isEmpty();
    }

    @Test
    void failsOnMissingFile() throws Exception {
        ExportRequest request = new ExportRequest();
        request.files.add(dir.resolve("missing.camel.yaml").toString());
        request.runtime = RuntimeType.main;

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer).withDependencyResolver(resolver))
                .export(request);

        assertThat(exit).isEqualTo(1);
        assertThat(printer.getOutput()).contains("Path does not exist");
        assertThat(resolved).isEmpty();
    }

    @Test
    void projectNameAndVersion() {
        assertThat(ProjectExporter.projectName("foo", "com.acme:bar:2.0", List.of("baz.yaml"))).isEqualTo("foo");
        assertThat(ProjectExporter.projectName(null, "com.acme:bar:2.0", List.of("baz.yaml"))).isEqualTo("bar");
        assertThat(ProjectExporter.projectName(null, null, List.of("routes/baz.camel.yaml"))).isEqualTo("baz");
        assertThat(ProjectExporter.projectName(null, null, List.of())).isNull();
        assertThat(ProjectExporter.projectVersion("com.acme:bar:2.0")).isEqualTo("2.0");
        assertThat(ProjectExporter.projectVersion(null)).isEqualTo("1.0-SNAPSHOT");
    }
}
