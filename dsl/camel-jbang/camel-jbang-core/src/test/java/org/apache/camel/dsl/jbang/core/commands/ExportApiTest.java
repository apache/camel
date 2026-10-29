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
package org.apache.camel.dsl.jbang.core.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.dsl.jbang.export.ExportContext;
import org.apache.camel.dsl.jbang.export.ExportRequest;
import org.apache.camel.dsl.jbang.export.ProjectExporter;
import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.camel.dsl.jbang.export.common.StringPrinter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exports through the Java API with the dependency resolver of camel-jbang-core (the silent run), as an application
 * that embeds the export does: the export keeps to its work directory and leaves the logging and the system properties
 * of the JVM as they are.
 */
class ExportApiTest {

    @TempDir
    Path dir;

    @Test
    void exportsWithoutSideEffects() throws Exception {
        Path route = dir.resolve("hello.camel.yaml");
        Files.copy(Path.of("src/test/resources/hello.camel.yaml"), route);
        Path exportDir = dir.resolve("export");

        ExportRequest request = new ExportRequest();
        request.files.add(route.toString());
        request.runtime = RuntimeType.main;
        request.exportDir = exportDir.toString();
        request.workDir = dir.resolve("work");
        request.mavenWrapper = false;
        request.quiet = true;

        Configuration logging = LoggerContext.getContext(false).getConfiguration();
        Map<Object, Object> properties = new HashMap<>(System.getProperties());

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer)).export(request);

        assertThat(exit).as(printer.getOutput()).isZero();
        assertThat(Files.readString(exportDir.resolve("pom.xml"))).contains("<artifactId>camel-timer</artifactId>");
        // the silent run writes its settings to the work directory of the request (the export tests in
        // camel-jbang-export check that the current directory is left alone, which the forks of this module share)
        assertThat(request.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE)).content()
                .contains("camel.main.routesIncludePattern=", "hello.camel.yaml");

        assertThat(LoggerContext.getContext(false).getConfiguration()).isSameAs(logging);
        assertThat(new HashMap<>(System.getProperties())).isEqualTo(properties);
    }
}
