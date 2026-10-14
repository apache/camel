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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25555: <code>camel validate normalize --output &lt;dir&gt;</code> writes one file per input file, so the
 * top-level onException of a file keeps applying to the routes of that file only.
 */
// camel run empties its work directory (.camel-jbang) when it starts, so two normalizations must not run at the same time
@Isolated
class YamlNormalizePerFileTest {

    private static final String A = """
            - route:
                id: fromA
                from:
                  uri: timer:a
                  steps:
                    - log:
                        message: "A"
            """;

    private static final String B = """
            - onException:
                exception:
                  - java.lang.Exception
                handled:
                  constant: "true"
                steps:
                  - log:
                      message: "B failed"
            - route:
                id: fromB
                from:
                  uri: timer:b
                  steps:
                    - log:
                        message: "B"
            """;

    @TempDir
    Path dir;

    private StringPrinter printer;
    private String stderr;
    private Path a;
    private Path b;

    @BeforeEach
    void writeInputs() throws Exception {
        a = Files.writeString(dir.resolve("a.yaml"), A);
        b = Files.writeString(dir.resolve("b.yaml"), B);
    }

    private int normalize(String... args) throws Exception {
        printer = new StringPrinter();
        YamlNormalizeCommand cmd = new YamlNormalizeCommand(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(cmd, args);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        cmd.stderr = new PrintStream(err, true, StandardCharsets.UTF_8);
        int answer = cmd.doCall();
        stderr = err.toString(StandardCharsets.UTF_8);
        return answer;
    }

    private static String kamelet(String name) {
        return """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: %s
                spec:
                  definition:
                    title: %s
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - setBody:
                            constant: %s
                """.formatted(name, name, name);
    }

    private static String validate(Path... files) throws Exception {
        StringPrinter out = new StringPrinter();
        YamlValidateCommand cmd = new YamlValidateCommand(new CamelJBangMain().withPrinter(out));
        String[] args = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            args[i] = files[i].toString();
        }
        CommandLine.populateCommand(cmd, args);
        cmd.doCall();
        return out.getOutput();
    }

    @Test
    void aDirectoryGetsOneFilePerInputFile() throws Exception {
        Path out = Files.createDirectory(dir.resolve("out"));
        assertThat(normalize("--output=" + out, a.toString(), b.toString())).isZero();

        assertThat(out.resolve("normalized.yaml")).doesNotExist();
        String na = Files.readString(out.resolve("a.yaml"));
        String nb = Files.readString(out.resolve("b.yaml"));
        assertThat(na).contains("id: fromA").doesNotContain("onException").doesNotContain("fromB");
        assertThat(nb).contains("id: fromB").contains("- onException:").contains("B failed").doesNotContain("fromA");
        // the onException stays top-level, before the route of its file
        assertThat(nb.indexOf("- onException:")).isLessThan(nb.indexOf("- route:"));
        assertThat(printer.getOutput()).doesNotContain("WARN");

        // and both load as YAML DSL files
        assertThat(validate(out.resolve("a.yaml"), out.resolve("b.yaml"))).contains("Validation success (files:2)");
    }

    @Test
    void theMergedOutputWarnsThatTheOnExceptionAppliesToTheOtherFiles() throws Exception {
        Path out = dir.resolve("merged.yaml");
        assertThat(normalize("--output=" + out, a.toString(), b.toString())).isZero();

        String merged = Files.readString(out);
        assertThat(merged).contains("id: fromA").contains("id: fromB").contains("- onException:");
        assertThat(stderr)
                .contains("WARN: the top-level onException of " + b + " applies to the routes of " + a + " too")
                .contains("use --output with a directory");
        assertThat(validate(out)).contains("Validation success (files:1)");
    }

    @Test
    void theConsoleOutputWarnsToo() throws Exception {
        assertThat(normalize(a.toString(), b.toString())).isZero();
        assertThat(stderr).contains("WARN: the top-level onException of " + b);
        // stdout has only the YAML, so that it can be redirected to a file
        String yaml = printer.getOutput();
        assertThat(yaml).startsWith("- ").doesNotContain("WARN").contains("id: fromA").contains("id: fromB");
        assertThat(validate(Files.writeString(dir.resolve("stdout.yaml"), yaml))).contains("Validation success (files:1)");

        // a single file: its onException applies to its own routes only
        assertThat(normalize(b.toString())).isZero();
        assertThat(stderr).doesNotContain("WARN");
        assertThat(printer.getOutput()).contains("- onException:");
    }

    @Test
    void theFilesWithTheSameNameArePrefixedWithTheirParentDirectory() throws Exception {
        Path orders = Files.createDirectory(dir.resolve("orders"));
        Path billing = Files.createDirectory(dir.resolve("billing"));
        Path ordersRoutes = Files.writeString(orders.resolve("routes.yaml"), A);
        Path billingRoutes = Files.writeString(billing.resolve("routes.yaml"), B);
        // a directory that does not exist yet, given with a trailing /
        Path out = dir.resolve("out");

        assertThat(normalize("--output=" + out + "/", ordersRoutes.toString(), billingRoutes.toString())).isZero();
        assertThat(out).isDirectory();
        assertThat(out.resolve("orders-routes.yaml")).exists();
        assertThat(out.resolve("billing-routes.yaml")).exists();
        assertThat(Files.readString(out.resolve("orders-routes.yaml"))).contains("id: fromA")
                .doesNotContain("onException");
        assertThat(Files.readString(out.resolve("billing-routes.yaml"))).contains("id: fromB").contains("- onException:");
    }

    @Test
    void theInputFilesAreNotOverwritten() throws Exception {
        String before = Files.readString(b);
        // the directory of the inputs
        assertThat(normalize("--output=" + dir, a.toString(), b.toString())).isOne();
        assertThat(printer.getOutput()).contains("--output would overwrite the input file " + a);
        // an input file as the merged output
        assertThat(normalize("--output=" + b, a.toString(), b.toString())).isOne();
        assertThat(printer.getOutput()).contains("--output would overwrite the input file " + b);
        assertThat(Files.readString(a)).isEqualTo(A);
        assertThat(Files.readString(b)).isEqualTo(before);
    }

    @Test
    void theGeneratedNamesDoNotReplaceAnotherFile() throws Exception {
        // review of #27818: the numbered names of two files were the name of a third file, whose routes got lost
        Path x = Files.createDirectories(dir.resolve("x/orders"));
        Path y = Files.createDirectories(dir.resolve("y/orders"));
        Path kx = Files.writeString(x.resolve("action.kamelet.yaml"), kamelet("action-x"));
        Path ky = Files.writeString(y.resolve("action.kamelet.yaml"), kamelet("action-y"));
        Path kz = Files.writeString(dir.resolve("action-1.kamelet.yaml"), kamelet("action-z"));
        Path rx = Files.writeString(x.resolve("routes.yaml"), A);
        Path ry = Files.writeString(y.resolve("routes.yaml"), B);
        Path rc = Files.writeString(dir.resolve("routes-1.yaml"), A.replace("fromA", "routeC"));
        Path out = dir.resolve("out");

        assertThat(normalize("--output=" + out + "/", kx.toString(), ky.toString(), kz.toString(), rx.toString(),
                ry.toString(), rc.toString())).isZero();
        assertThat(Files.readString(out.resolve("action-1.kamelet.yaml"))).contains("name: action-z");
        assertThat(Files.readString(out.resolve("action-2.kamelet.yaml"))).contains("name: action-x");
        assertThat(Files.readString(out.resolve("action-3.kamelet.yaml"))).contains("name: action-y");
        assertThat(Files.readString(out.resolve("routes-1.yaml"))).contains("id: routeC").doesNotContain("fromA");
        assertThat(Files.readString(out.resolve("routes-2.yaml"))).contains("id: fromA").doesNotContain("onException");
        assertThat(Files.readString(out.resolve("routes-3.yaml"))).contains("id: fromB").contains("- onException:");
        try (var files = Files.list(out)) {
            assertThat(files).hasSize(6);
        }
    }

    @Test
    void theOutputNames() {
        Map<Path, String> names = YamlNormalizeCommand.outputNames(
                List.of("a.yaml", "x/routes.yaml", "y/routes.yaml", "p/z/r.yml", "q/z/r.yml", "file:b.yml"));
        assertThat(names).containsValues("a.yaml", "x-routes.yaml", "y-routes.yaml", "r-1.yml", "r-2.yml", "b.yml");
        assertThat(names.get(Path.of("q/z/r.yml").toAbsolutePath())).isEqualTo("r-2.yml");

        // a numbered name is not the name of another input file
        Map<Path, String> three = YamlNormalizeCommand.outputNames(
                List.of("x/orders/routes.yaml", "y/orders/routes.yaml", "routes-1.yaml"));
        assertThat(three).containsValues("routes-1.yaml", "routes-2.yaml", "routes-3.yaml");
        assertThat(three.get(Path.of("routes-1.yaml").toAbsolutePath())).isEqualTo("routes-1.yaml");
        // the number goes before all the extensions, and every name is distinct
        assertThat(YamlNormalizeCommand.outputNames(
                List.of("x/orders/action.kamelet.yaml", "y/orders/action.kamelet.yaml", "action.kamelet-1.yaml")))
                .containsValues("action.kamelet-1.yaml", "action-1.kamelet.yaml", "action-2.kamelet.yaml");
        // nor the prefixed name of another input file
        assertThat(YamlNormalizeCommand.outputNames(List.of("x/routes.yaml", "y/routes.yaml", "x-routes.yaml")))
                .containsValues("x-routes.yaml", "routes-1.yaml", "routes-2.yaml");

        assertThat(YamlNormalizeCommand.unattributedName(Set.of("a.yaml"))).isEqualTo("normalized.yaml");
        assertThat(YamlNormalizeCommand.unattributedName(Set.of("normalized.yaml", "normalized-1.yaml")))
                .isEqualTo("normalized-2.yaml");
    }

}
