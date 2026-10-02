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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class YamlRouteNodeScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scanSimpleRouteWithSteps() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    id: myRoute",
                "    from:",
                "      uri: timer:tick",
                "      steps:",
                "        - log:",
                "            message: hello",
                "        - to:",
                "            uri: kafka:orders",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanFile(file);

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).kind()).isEqualTo(YamlRouteNodeScanner.EntryKind.ROUTE);
        assertThat(entries.get(0).routeId()).isEqualTo("myRoute");
        assertThat(entries.get(0).fromUri()).isEqualTo("timer:tick");
        assertThat(entries.get(0).lineIndex()).isEqualTo(2);

        assertThat(entries.get(1).kind()).isEqualTo(YamlRouteNodeScanner.EntryKind.PROCESSOR);
        assertThat(entries.get(1).type()).isEqualTo("log");
        assertThat(entries.get(1).label()).isEqualTo("hello");
        assertThat(entries.get(1).lineIndex()).isEqualTo(5);

        assertThat(entries.get(2).type()).isEqualTo("to");
        assertThat(entries.get(2).label()).isEqualTo("kafka:orders");
        assertThat(entries.get(2).lineIndex()).isEqualTo(8);
    }

    @Test
    void scanFlatFromRouteDerivesRouteIdFromUri() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: kafka:my-topic",
                "    steps:",
                "      - log:",
                "          message: ping",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanFile(file);

        assertThat(entries.get(0).routeId()).isEqualTo("my-topic");
        assertThat(entries.get(0).fromUri()).isEqualTo("kafka:my-topic");
    }

    @Test
    void scanInlineFromUri() throws IOException {
        String yaml = String.join("\n",
                "- from: timer:hello",
                "  steps:",
                "    - setBody:",
                "        constant: test",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanFile(file);

        assertThat(entries.get(0).kind()).isEqualTo(YamlRouteNodeScanner.EntryKind.ROUTE);
        assertThat(entries.get(0).lineIndex()).isEqualTo(0);
        assertThat(entries.get(1).type()).isEqualTo("setBody");
        assertThat(entries.get(1).label()).isEqualTo("test");
    }

    @Test
    void processorIdDoesNotOverwriteRouteId() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    id: myRoute",
                "    from:",
                "      uri: timer:tick",
                "      steps:",
                "        - log:",
                "            id: myLog",
                "            message: hello",
                "        - to:",
                "            uri: kafka:orders",
                "");

        Path file = tempDir.resolve("route.camel.yaml");
        Files.writeString(file, yaml);

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanFile(file);

        assertThat(entries.get(0).routeId()).isEqualTo("myRoute");
        assertThat(entries.get(1).routeId()).isEqualTo("myRoute");
        assertThat(entries.get(1).label()).isEqualTo("hello");
        assertThat(entries.get(2).routeId()).isEqualTo("myRoute");
        assertThat(entries.get(2).type()).isEqualTo("to");
    }

    @Test
    void newFromRouteClearsPreviousRouteId() throws IOException {
        String yaml = String.join("\n",
                "- route:",
                "    id: myRoute",
                "    from:",
                "      uri: timer:a",
                "- from:",
                "    uri: timer:b",
                "");

        Path file = tempDir.resolve("routes.camel.yaml");
        Files.writeString(file, yaml);

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanFile(file);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).routeId()).isEqualTo("myRoute");
        assertThat(entries.get(1).routeId()).isEqualTo("b");
    }

    @Test
    void beansSectionNotIndexedAsProcessors() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:a",
                "    steps:",
                "      - log:",
                "          message: hi",
                "- beans:",
                "    - name: myBean",
                "      type: java.lang.String",
                "");

        Path file = tempDir.resolve("routes.camel.yaml");
        Files.writeString(file, yaml);

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanFile(file);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(1).type()).isEqualTo("log");
    }

    @Test
    void isNavigableNodeLineSkipsStructuralKeys() {
        assertThat(YamlRouteNodeScanner.isNavigableNodeLine("    steps:")).isFalse();
        assertThat(YamlRouteNodeScanner.isNavigableNodeLine("      - expression:")).isFalse();
        assertThat(YamlRouteNodeScanner.isNavigableNodeLine("        uri: kafka:foo")).isTrue();
        assertThat(YamlRouteNodeScanner.isNavigableNodeLine("      - log:")).isTrue();
        assertThat(YamlRouteNodeScanner.isNavigableNodeLine("      - to:")).isFalse();
        assertThat(YamlRouteNodeScanner.isNavigableNodeLine("          uri: kafka:foo")).isTrue();
    }

    @Test
    void scanMultipleRoutesInOneFile() throws IOException {
        String yaml = String.join("\n",
                "- from:",
                "    uri: timer:a",
                "    steps:",
                "      - log:",
                "          message: first",
                "- from:",
                "    uri: timer:b",
                "    steps:",
                "      - log:",
                "          message: second",
                "");

        Path file = tempDir.resolve("routes.camel.yaml");
        Files.writeString(file, yaml);

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanFile(file);

        assertThat(entries.stream().filter(e -> e.kind() == YamlRouteNodeScanner.EntryKind.ROUTE))
                .hasSize(2);
        assertThat(entries.stream().filter(e -> e.kind() == YamlRouteNodeScanner.EntryKind.PROCESSOR))
                .hasSize(2);
    }

    /** Each processor as indent type[label] @line (from 1). */
    private static List<String> describe(List<YamlRouteNodeScanner.NodeEntry> entries) {
        return entries.stream()
                .filter(e -> e.kind() == YamlRouteNodeScanner.EntryKind.PROCESSOR)
                .map(e -> e.indent() + " " + e.type() + "[" + e.label() + "] @" + (e.lineIndex() + 1))
                .toList();
    }

    @Test
    void theWhenAndOtherwiseOfAChoiceAreNodes() {
        String yaml = String.join("\n",
                "- route:",
                "    id: shipping",
                "    from:",
                "      uri: seda:shipping",
                "      steps:",
                "        - choice:",
                "            when:",
                "              - expression:",
                "                  simple:",
                "                    expression: \"${body} > 6\"",
                "                steps:",
                "                  - setHeader:",
                "                      name: carrier",
                "              - simple: \"${body} > 3\"",
                "                steps:",
                "                  - log:",
                "                      message: medium",
                "            otherwise:",
                "              steps:",
                "                - setHeader:",
                "                    name: carrier",
                "        - log:",
                "            message: shipped",
                "");

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanLines(List.of(yaml.split("\n")), "f");

        // when and otherwise are siblings under the choice, with their steps under them
        assertThat(describe(entries)).containsExactly(
                "2 choice[] @6",
                "4 when[simple{${body} > 6}] @8",
                "7 setHeader[carrier] @12",
                "4 when[simple{${body} > 3}] @14",
                "7 log[medium] @16",
                "4 otherwise[] @18",
                "6 setHeader[carrier] @20",
                "2 log[shipped] @22");
    }

    @Test
    void aChoiceInAWhenKeepsTheOuterWhens() {
        String yaml = String.join("\n",
                "- route:",
                "    from:",
                "      uri: direct:start",
                "      steps:",
                "        - choice:",
                "            when:",
                "              - simple: \"${body} > 6\"",
                "                steps:",
                "                  - choice:",
                "                      when:",
                "                        - simple: \"${body} > 8\"",
                "                          steps:",
                "                            - to:",
                "                                uri: direct:big",
                "              - simple: \"${body} > 3\"",
                "                steps:",
                "                  - to:",
                "                      uri: direct:medium",
                "");

        List<YamlRouteNodeScanner.NodeEntry> entries = YamlRouteNodeScanner.scanLines(List.of(yaml.split("\n")), "f");

        assertThat(describe(entries)).containsExactly(
                "2 choice[] @5",
                "4 when[simple{${body} > 6}] @7",
                "7 choice[] @9",
                "9 when[simple{${body} > 8}] @11",
                "12 to[direct:big] @14",
                "4 when[simple{${body} > 3}] @15",
                "7 to[direct:medium] @18");
    }

    @Test
    void scanEmptyFileReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("empty.camel.yaml");
        Files.writeString(file, "");

        assertThat(YamlRouteNodeScanner.scanFile(file)).isEmpty();
    }
}
