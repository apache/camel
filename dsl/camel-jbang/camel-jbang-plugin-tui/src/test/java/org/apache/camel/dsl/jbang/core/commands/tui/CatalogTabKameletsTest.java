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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.ai.KameletDefinitions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25416: the Kamelets in the Catalog view, the project's own first; the Kamelets of the catalog its routes use,
 * and with the full catalog all of them.
 */
class CatalogTabKameletsTest {

    @TempDir
    Path dir;

    private static final Map<String, KameletDefinitions.Definition> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put("timer-source", new KameletDefinitions.Definition(
                "timer-source", "source", null, "Produces periodic events", "the Kamelet catalog 4.22.1",
                List.of(new KameletDefinitions.Property(
                        "message", true, "string", null, "The message to generate",
                        List.of()),
                        new KameletDefinitions.Property(
                                "period", false, "integer", "1000", "The interval",
                                List.of()))));
        CATALOG.put("log-sink", new KameletDefinitions.Definition(
                "log-sink", "sink", null, "Logs the events", "the Kamelet catalog 4.22.1", List.of()));
    }

    @Test
    void theProjectsOwnKameletsAndTheOnesItsRoutesUse() throws Exception {
        Files.writeString(dir.resolve("tag-order-action.kamelet.yaml"), """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: tag-order-action
                  labels:
                    camel.apache.org/kamelet.type: action
                spec:
                  definition:
                    title: Tag Order Action
                    required:
                      - tag
                    properties:
                      tag:
                        title: Tag
                        type: string
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - setBody:
                            simple: "${body} [{{tag}}]"
                """);
        Files.writeString(dir.resolve("orders.camel.yaml"), """
                - route:
                    from:
                      uri: kamelet:timer-source
                      parameters:
                        message: hello
                      steps:
                        - to:
                            uri: kamelet:tag-order-action
                """);

        List<CatalogTab.CatalogEntry> app = CatalogTab.kameletEntries(dir, false, CATALOG);
        assertThat(app).extracting(e -> e.name).containsExactly("tag-order-action", "timer-source");
        assertThat(app.get(0).project).isTrue();
        assertThat(app.get(0).label).isEqualTo("project");
        assertThat(app.get(0).kind).isEqualTo("kamelet");
        assertThat(app.get(1).project).isFalse();
        assertThat(app.get(1).label).isEqualTo("source");
        // kamelet:source in the template is not a Kamelet of the catalog
        assertThat(CatalogTab.usedKamelets(dir)).containsOnly("timer-source", "tag-order-action");

        List<CatalogTab.CatalogEntry> full = CatalogTab.kameletEntries(dir, true, CATALOG);
        assertThat(full).extracting(e -> e.name).containsExactly("tag-order-action", "timer-source", "log-sink");
    }

    @Test
    void theDocOfAKamelet() {
        String md = CatalogTab.kameletMarkdown(CATALOG.get("timer-source"));
        assertThat(md).contains("**Kamelet:** `timer-source` (source)")
                .contains("**From:** the Kamelet catalog 4.22.1")
                .contains("from:\n  uri: kamelet:timer-source\n  parameters:\n    message: ...")
                .contains("| message | yes | string |  | The message to generate |")
                .contains("| period |  | integer | 1000 | The interval |");
        assertThat(CatalogTab.kameletMarkdown(CATALOG.get("log-sink"))).contains("- to:\n    uri: kamelet:log-sink")
                .contains("None.");
    }
}
