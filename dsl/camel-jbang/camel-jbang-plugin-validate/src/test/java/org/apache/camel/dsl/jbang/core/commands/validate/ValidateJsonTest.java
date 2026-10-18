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

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * camel validate with --json: one JSON array on stdout with valid, file and errors of each file, as the MCP tool
 * camel_validate_source answers.
 */
class ValidateJsonTest {

    private static final String ROUTE = Path.of("src/test/resources/route.yaml").toString();

    @TempDir
    Path dir;

    private StringPrinter printer;

    @Test
    void validYamlRoute() throws Exception {
        assertThat(run(new YamlValidateCommand(main()), "--json", ROUTE)).isZero();

        JsonArray files = stdoutArray();
        assertThat(files).hasSize(1);
        JsonObject file = files.getMap(0);
        assertThat(file).containsEntry("valid", true).containsEntry("file", ROUTE);
        JsonArray errors = file.getCollection("errors");
        assertThat(errors).isEmpty();
    }

    @Test
    void invalidYamlRouteHasItsErrors() throws Exception {
        Path route = dir.resolve("bad.yaml");
        Files.writeString(route, """
                - from:
                    uri: timer:tick
                    steps:
                      - logg: hello
                """);

        assertThat(run(new YamlValidateCommand(main()), "--json", route.toString())).isEqualTo(1);

        JsonObject file = stdoutArray().getMap(0);
        assertThat(file).containsEntry("valid", false).containsEntry("file", route.toString());
        JsonArray errors = file.getCollection("errors");
        assertThat(errors).isNotEmpty();
    }

    @Test
    void propertiesWithAWrongKey() throws Exception {
        Path properties = dir.resolve("application.properties");
        Files.writeString(properties, "camel.main.name = demo\ncamel.main.no-such-option = true\n");

        assertThat(run(new PropertiesValidateCommand(main()), "--json", properties.toString())).isEqualTo(1);

        JsonObject file = stdoutArray().getMap(0);
        assertThat(file).containsEntry("valid", false);
        JsonArray errors = file.getCollection("errors");
        assertThat(errors).hasSize(1);
    }

    @Test
    void sourceFiles() throws Exception {
        assertThat(run(new SourceValidateCommand(main()), "--json", ROUTE)).isZero();

        JsonObject file = stdoutArray().getMap(0);
        assertThat(file).containsEntry("valid", true);
    }

    @Test
    void skippedFileIsAWarningOnStderr() throws Exception {
        Path text = dir.resolve("notes.txt");
        Files.writeString(text, "not a route");

        // nothing is left to validate, which is not found (the error document is printed by the error handler)
        CliException e = assertThrows(CliException.class,
                () -> run(new YamlValidateCommand(main()), "--json", text.toString()));
        assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(printer.getStderr()).startsWith("WARN: Skipping non-YAML file");
    }

    @Test
    void nothingToValidateIsNotFound() throws Exception {
        Path text = Files.writeString(dir.resolve("notes.txt"), "not a route");

        CliException e = assertThrows(CliException.class, () -> run(new YamlValidateCommand(main()), text.toString()));
        assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo("No YAML files to validate");
        e = assertThrows(CliException.class, () -> run(new PropertiesValidateCommand(main()), text.toString()));
        assertThat(e.getMessage()).isEqualTo("No properties files to validate");
    }

    private CamelJBangMain main() {
        printer = new StringPrinter();
        return new CamelJBangMain().withPrinter(printer);
    }

    private static int run(CamelCommand command, String... args) throws Exception {
        CommandLine.populateCommand(command, args);
        return command.doCall();
    }

    private JsonArray stdoutArray() {
        JsonArray doc = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        assertThat(doc).as("stdout must be one JSON array: " + printer.getStdout()).isNotNull();
        return doc;
    }
}
