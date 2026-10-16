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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.exceptionhandler.CliExecutionExceptionHandler;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * camel transform route: a route in another DSL, printed or written to a file or directory.
 */
class TransformRouteTest extends CamelCommandBaseTestSupport {

    private static final String YAML = """
            - route:
                id: hello
                from:
                  uri: timer:tick
                  steps:
                    - log: Hello Camel
            """;

    private static final String JAVA = """
            import org.apache.camel.builder.RouteBuilder;

            public class Hello extends RouteBuilder {
                @Override
                public void configure() throws Exception {
                    from("timer:tick").routeId("hello")
                        .log("Hello Camel");
                }
            }
            """;

    @TempDir
    Path dir;

    @Test
    void javaToYamlWithoutCompiling() throws Exception {
        Path java = Files.writeString(dir.resolve("Hello.java"), JAVA);

        assertThat(command(List.of(java.toString())).doCall()).isZero();

        assertThat(printer.getOutput()).contains("- route:", "id: hello", "uri: timer", "Hello Camel");
    }

    @Test
    void yamlToJava() throws Exception {
        Path yaml = Files.writeString(dir.resolve("hello.camel.yaml"), YAML);

        assertThat(command(List.of(yaml.toString()), "--format=java").doCall()).isZero();

        assertThat(printer.getOutput()).contains("extends RouteBuilder", "from(\"timer:tick\")", "Hello Camel");
    }

    @Test
    void yamlToJavaFilesInADirectory() throws Exception {
        Path one = Files.writeString(dir.resolve("hello.camel.yaml"), YAML);
        Path two = Files.writeString(dir.resolve("bye.camel.yaml"), YAML.replace("hello", "bye"));
        Path out = dir.resolve("java");

        assertThat(command(List.of(one.toString(), two.toString()), "--format=java", "--output=" + out).doCall()).isZero();

        try (var files = Files.list(out)) {
            assertThat(files.map(f -> f.getFileName().toString()).toList()).hasSize(2).allMatch(n -> n.endsWith(".java"));
        }
        assertThat(printer.getOutput()).isEmpty();
    }

    @Test
    void javaToXmlFile() throws Exception {
        Path java = Files.writeString(dir.resolve("Hello.java"), JAVA);
        Path out = dir.resolve("hello.xml");

        assertThat(command(List.of(java.toString()), "--format=xml", "--output=" + out).doCall()).isZero();

        assertThat(Files.readString(out)).contains("<route", "id=\"hello\"", "timer:tick");
    }

    @Test
    void unknownFormatIsAUsageError() throws Exception {
        Path yaml = Files.writeString(dir.resolve("hello.camel.yaml"), YAML);

        TransformRoute command = command(List.of(yaml.toString()), "--format=json");

        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.USAGE))
                .hasMessage("--format must be xml, yaml or java, was: json");
    }

    @Test
    void aFileThatDoesNotExistIsNotFound() {
        TransformRoute command = command(List.of(dir.resolve("nope.camel.yaml").toString()), "--format=java");

        Throwable e = catchThrowable(command::doCall);

        assertThat(e).isInstanceOf(NoSuchFileException.class);
        // which the CLI ends with exit code 3
        assertThat(new CliExecutionExceptionHandler(new CamelJBangMain()).getExitCode(e)).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void anInvalidRouteIsAFailure() throws Exception {
        Path yaml = Files.writeString(dir.resolve("bad.camel.yaml"), "- route:\n    from:\n      steps: []\n");

        TransformRoute command = command(List.of(yaml.toString()), "--format=xml");

        // the route is wrong, not the CLI (which exited with 70 and the stack trace)
        assertThatThrownBy(command::doCall)
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessageStartingWith("Cannot transform the routes: ")
                .hasMessageContaining("bad.camel.yaml");
    }

    @Test
    void anInvalidRouteToJavaIsAFailure() throws Exception {
        Path yaml = Files.writeString(dir.resolve("bad.camel.yaml"), "- route:\n    from:\n      steps: []\n");

        assertThat(command(List.of(yaml.toString()), "--format=java").doCall()).isEqualTo(CliExitCode.FAILURE);

        assertThat(printer.getStderr()).isNotEmpty();
    }

    private TransformRoute command(List<String> files, String... args) {
        TransformRoute command = new TransformRoute(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        command.files = Arrays.asList(files.toArray(String[]::new));
        return command;
    }
}
