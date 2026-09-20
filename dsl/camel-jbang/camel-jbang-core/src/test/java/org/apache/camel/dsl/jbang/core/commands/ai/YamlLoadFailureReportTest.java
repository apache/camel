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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.impl.event.CamelContextReloadFailureEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** CAMEL-24851: a YAML file that did not load gets the validator's report, which says what to write. */
class YamlLoadFailureReportTest {

    @TempDir
    Path dir;

    private static final String BAD_ROUTE = """
            - route:
                from:
                  uri: timer:tick
                  steps:
                    - pollEnrich:
                        uri: file:./order.json
                    - to:
                        uri: log:done
            """;

    @Test
    void aDeserializationErrorIsAYamlLoadFailure() {
        Exception loader
                = new YamlDeserializationException("Error constructing YAML node id: pollEnrich: unsupported field: uri");
        assertThat(YamlLoadFailureReport.isYamlLoadFailure(new RuntimeCamelException("Error starting Camel", loader))).isTrue();
        assertThat(
                YamlLoadFailureReport.isYamlLoadFailure(new RuntimeCamelException("Error pre-parsing resource: file:x.yaml")))
                .isTrue();
        assertThat(YamlLoadFailureReport.isYamlLoadFailure(new IllegalArgumentException("Invalid directory: archived/${x}")))
                .isFalse();
    }

    @Test
    void theReportSaysWhatToWrite() throws Exception {
        Path route = dir.resolve("orders.camel.yaml");
        Files.writeString(route, BAD_ROUTE);
        List<String> lines = YamlLoadFailureReport.report(List.of(route));
        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).isEqualTo("The route file did not load. camel validate yaml says what to write:");
        assertThat(lines.get(1)).isEqualTo("  orders.camel.yaml:");
        // the schema message (with the CAMEL-24850 hint once it is in: "write pollEnrich: {expression: ...}")
        assertThat(lines.get(2)).startsWith("    ").contains("pollEnrich").contains("'uri'");
        assertThat(lines.get(3)).startsWith("  (camel validate yaml <file> for the full report");
    }

    @Test
    void aFileTheValidatorAcceptsGivesNoReport() throws Exception {
        Path route = dir.resolve("ok.camel.yaml");
        Files.writeString(route, BAD_ROUTE.replace("uri: file:./order.json",
                "expression:\n              constant:\n                expression: \"file:./order.json\""));
        assertThat(YamlLoadFailureReport.report(List.of(route))).isEmpty();
    }

    @Test
    void theRunFilesAreFilteredToLocalYaml() throws Exception {
        Path route = dir.resolve("a.yaml");
        Files.writeString(route, BAD_ROUTE);
        List<Path> files
                = YamlLoadFailureReport.yamlFiles(List.of("file:" + route, route.toString(), dir.resolve("Foo.java").toString(),
                        "github:apache:camel:x.yaml", dir.resolve("missing.yaml").toString()));
        assertThat(files).containsExactly(route);
    }

    @Test
    void theReloadNotifierPrintsTheReportForTheFileThatFailed() throws Exception {
        Path route = dir.resolve("orders.camel.yaml");
        Files.writeString(route, BAD_ROUTE);
        List<String> printed = new ArrayList<>();
        YamlLoadFailureReport.ReloadFailureNotifier notifier = new YamlLoadFailureReport.ReloadFailureNotifier(printed::add);
        // the event the file watcher emits: the file name is the action, the loader's exception the cause
        CamelContextReloadFailureEvent event = new CamelContextReloadFailureEvent(
                new SimpleCamelContext(), route.toString(),
                new YamlDeserializationException("Error constructing YAML node id: pollEnrich: unsupported field: uri"));
        assertThat(notifier.isEnabled(event)).isTrue();
        notifier.notify(event);
        assertThat(printed).hasSize(4);
        assertThat(printed.get(2)).contains("pollEnrich");
    }
}
