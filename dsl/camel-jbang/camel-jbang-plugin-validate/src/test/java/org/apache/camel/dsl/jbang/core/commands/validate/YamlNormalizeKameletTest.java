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
package org.apache.camel.dsl.jbang.core.commands.validate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25381: camel validate normalize on a Kamelet file normalizes its template and keeps the rest of the file.
 */
class YamlNormalizeKameletTest {

    private static final Path KAMELET = Path.of("src/test/resources/kamelets/tag-order-action.kamelet.yaml");
    private static final Path ROUTE = Path.of("src/test/resources/route.yaml");

    private static StringPrinter printer;

    private static int normalize(String... args) throws Exception {
        printer = new StringPrinter();
        YamlNormalizeCommand cmd = new YamlNormalizeCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, args);
        return cmd.doCall();
    }

    @Test
    void aKameletIsNormalizedInItsTemplateOnly(@TempDir Path out) throws Exception {
        assertThat(normalize("--output=" + out, KAMELET.toString())).isZero();
        String original = Files.readString(KAMELET);
        String normalized = Files.readString(out.resolve("tag-order-action.kamelet.yaml"));

        // the license, metadata and definition (with its comment) as written, and what follows the template
        assertThat(normalized).startsWith(original.substring(0, original.indexOf("  template:\n") + 12));
        assertThat(normalized).endsWith(original.substring(original.indexOf("  dependencies:")));
        // the template in canonical form: expressions under expression:, endpoints as uri:
        String template = normalized.substring(normalized.indexOf("  template:\n"), normalized.indexOf("  dependencies:"));
        assertThat(template.replaceAll("\\s+", " "))
                .contains("- setBody: expression: simple: expression: \"${body} [{{tag}}]\"")
                .contains("- expression: simple: expression: \"${header.priority} == true\"");
        assertThat(template).contains("uri: kamelet:source").contains("uri: kamelet:sink")
                .doesNotContain("templateId");
        // the bean properties in the order they were written
        assertThat(template.indexOf("zeta:")).isLessThan(template.indexOf("alpha:"));
    }

    @Test
    void aKameletIsPrintedWhenThereIsNoOutput() throws Exception {
        assertThat(normalize(KAMELET.toString())).isZero();
        assertThat(printer.getOutput()).contains("kind: Kamelet").contains("name: tag-order-action")
                .doesNotContain("routeTemplate");
    }

    @Test
    void aKameletAndARouteAreTwoDocuments() throws Exception {
        assertThat(normalize(KAMELET.toString(), ROUTE.toString())).isZero();
        String output = printer.getOutput();
        assertThat(output).contains("\n---\n").contains("kind: Kamelet").contains("- route:")
                .doesNotContain("routeTemplate");
    }

    @Test
    void theTemplateIsReplacedInTheKameletText() {
        String kamelet = """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: my-action
                spec:
                  definition:
                    title: Mine
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - setBody:
                            constant: hi

                  types:
                    out:
                      mediaType: text/plain
                """;
        String dump = """
                - routeTemplate:
                    id: other
                    route:
                      from:
                        uri: timer
                - routeTemplate:
                    id: my-action
                    parameters:
                      - name: x
                    route:
                      from:
                        uri: kamelet:source
                        steps:
                          - setBody:
                              expression:
                                constant:
                                  expression: hi
                - route:
                    from:
                      uri: direct
                """;
        String normalized = KameletNormalizer.normalize(kamelet, dump, "my-action");
        assertThat(normalized).isEqualTo("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: my-action
                spec:
                  definition:
                    title: Mine
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - setBody:
                            expression:
                              constant:
                                expression: hi

                  types:
                    out:
                      mediaType: text/plain
                """);
        assertThat(KameletNormalizer.withoutTemplates(dump, Set.of("my-action")))
                .contains("id: other").contains("- route:").doesNotContain("my-action");
        assertThat(KameletNormalizer.normalize(kamelet, dump, "missing")).isNull();
    }

    @Test
    void theCommentsOfTheTemplateAreKept() {
        // review of the camel-kamelets PR: the comments of 16 templates (several on security decisions) were dropped
        String kamelet = """
                kind: Kamelet
                metadata:
                  name: my-sink
                spec:
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        # drop the headers from upstream
                        - removeHeader:
                            name: Subject
                        - to:
                            uri: "mail:smtp"
                            parameters:
                              host: "{{host}}"
                              # needed, else the Subject header is ignored
                              useHeaderSubject: true
                        # the end
                """;
        String dump = """
                - routeTemplate:
                    id: my-sink
                    route:
                      from:
                        uri: kamelet:source
                        steps:
                          - removeHeader:
                              name: Subject
                          - to:
                              uri: mail
                              parameters:
                                protocol: smtp
                                host: "{{host}}"
                                useHeaderSubject: true
                """;
        List<String> lost = new ArrayList<>();
        String normalized = KameletNormalizer.normalize(kamelet, dump, "my-sink", lost);
        assertThat(lost).isEmpty();
        assertThat(normalized).isEqualTo("""
                kind: Kamelet
                metadata:
                  name: my-sink
                spec:
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        # drop the headers from upstream
                        - removeHeader:
                            name: Subject
                        - to:
                            uri: mail
                            parameters:
                              protocol: smtp
                              host: "{{host}}"
                              # needed, else the Subject header is ignored
                              useHeaderSubject: true
                              # the end
                """);
    }

    @Test
    void theNameOfAKamelet() {
        assertThat(KameletNormalizer.kameletName("kind: Kamelet\nmetadata:\n  name: abc\nspec:\n", "x.kamelet.yaml"))
                .isEqualTo("abc");
        assertThat(KameletNormalizer.kameletName("kind: Kamelet\nspec:\n", "dir/xyz.kamelet.yaml")).isEqualTo("xyz");
        assertThat(KameletNormalizer.isKamelet("apiVersion: camel.apache.org/v1\nkind: Kamelet\n")).isTrue();
        assertThat(KameletNormalizer.isKamelet("- route:\n    from:\n")).isFalse();
    }
}
