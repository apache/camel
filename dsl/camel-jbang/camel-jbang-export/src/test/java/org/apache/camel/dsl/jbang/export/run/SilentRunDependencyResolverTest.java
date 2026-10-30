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
package org.apache.camel.dsl.jbang.export.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.dsl.jbang.export.ExportContext;
import org.apache.camel.dsl.jbang.export.ExportRequest;
import org.apache.camel.dsl.jbang.export.ProjectExporter;
import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.dsl.jbang.export.common.PluginExporter;
import org.apache.camel.dsl.jbang.export.common.Printer;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.camel.dsl.jbang.export.common.StringPrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exports with the dependency resolver of this module, which this module's tests run without camel-jbang-core and
 * picocli on the classpath.
 */
class SilentRunDependencyResolverTest {

    @TempDir
    Path dir;

    private Path route;

    @BeforeEach
    void writeRoute() throws Exception {
        route = dir.resolve("hello.camel.yaml");
        Files.writeString(route, """
                - route:
                    from:
                      uri: timer:hello
                      steps:
                        - log:
                            message: Hello
                """);
    }

    @Test
    void exportsWithoutCamelJBangCore() throws Exception {
        assertThatThrownBy(() -> Class.forName("org.apache.camel.dsl.jbang.core.commands.Run"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("picocli.CommandLine"))
                .isInstanceOf(ClassNotFoundException.class);

        ExportRequest request = request();
        StringPrinter printer = new StringPrinter();
        // the default resolver of the export
        ExportContext context = new ExportContext().withPrinter(printer);
        assertThat(context.getDependencyResolver()).isInstanceOf(SilentRunDependencyResolver.class);

        assertThat(new ProjectExporter(context).export(request)).as(printer.getOutput()).isZero();

        assertThat(dir.resolve("export/pom.xml")).content().contains("<artifactId>camel-timer</artifactId>");
        assertThat(request.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE)).content()
                .contains("camel.main.routesIncludePattern=file\\:" + route,
                        "dependency=mvn\\:org.apache.camel\\:camel-timer");
    }

    @Test
    void pluginsCustomizeTheSilentRunAndAddTheirDependencies() throws Exception {
        List<List<String>> customized = new CopyOnWriteArrayList<>();
        PluginExporter exporter = new PluginExporter() {
            @Override
            public Set<String> getDependencies(RuntimeType runtimeType) {
                return Set.of("camel:jackson");
            }

            @Override
            public boolean contributeRuntimeDependencies() {
                return true;
            }

            @Override
            public void addSourceFiles(Path buildDir, String packageName, Printer printer) {
                // noop
            }
        };
        ExportContext context = new ExportContext().withPrinter(new StringPrinter())
                .withPluginRunCustomizers(repos -> List.of((main, files) -> customized.add(files)))
                .withPluginExporters(repos -> List.of(exporter));
        ExportRequest request = request();

        assertThat(new SilentRunDependencyResolver().resolve(request, context)).isZero();

        assertThat(customized).containsExactly(List.of(route.toString()));
        assertThat(request.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE)).content()
                .contains("camel.jbang.dependencies=camel\\:jackson");
    }

    @Test
    void aRouteThatDoesNotLoadIsGivenToTheStartFailureListener() throws Exception {
        Files.writeString(route, """
                - route:
                    from:
                      uri: timer:hello
                      steps:
                        - pollEnrich:
                            uri: file:./order.json
                """);
        List<Exception> failures = new CopyOnWriteArrayList<>();
        List<List<String>> runFiles = new CopyOnWriteArrayList<>();
        ExportContext context = new ExportContext().withPrinter(new StringPrinter())
                .withStartFailureListener((failure, files) -> {
                    failures.add(failure);
                    runFiles.add(files);
                });

        assertThatThrownBy(() -> new SilentRunDependencyResolver().resolve(request(), context)).isNotNull();

        assertThat(failures).hasSize(1);
        assertThat(runFiles).containsExactly(List.of(route.toString()));
    }

    @Test
    void aClipboardFileThatDoesNotExistIsNotFound() throws Exception {
        // camel run reads such a file from the clipboard; the export does not
        ExportRequest request = request();
        request.files.clear();
        request.files.add("clipboard.yaml");
        StringPrinter printer = new StringPrinter();

        assertThat(new SilentRunDependencyResolver().resolve(request, new ExportContext().withPrinter(printer)))
                .isEqualTo(RunPipeline.FILE_NOT_FOUND);
        assertThat(printer.getStderr()).contains("File does not exist: file:clipboard.yaml");
    }

    private ExportRequest request() {
        ExportRequest request = new ExportRequest();
        request.files.add(route.toString());
        request.runtime = RuntimeType.main;
        request.exportDir = dir.resolve("export").toString();
        request.workDir = dir.resolve("work");
        request.mavenWrapper = false;
        request.quiet = true;
        return request;
    }
}
