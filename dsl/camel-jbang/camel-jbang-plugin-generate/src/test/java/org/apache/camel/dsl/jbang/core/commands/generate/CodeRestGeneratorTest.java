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
package org.apache.camel.dsl.jbang.core.commands.generate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CodeRestGeneratorTest {

    private static final String SPEC = Paths.get("src/test/resources/petstore-v3.json").toAbsolutePath().toString();

    private static final List<String> DTOS = List.of(
            "Address.java", "Category.java", "Customer.java", "ModelApiResponse.java", "Order.java", "Pet.java", "Tag.java",
            "User.java");

    @TempDir
    Path dir;

    private final StringPrinter printer = new StringPrinter();

    private Path tempDirectory;

    @Test
    void writesTheDtosOfAPackageWhoseParentDirectoryDoesNotExist() throws Exception {
        Path packageDir = dir.resolve("org").resolve("acme");

        int exitCode = generate(packageDir, "--package=org.acme");

        assertThat(exitCode).as(printer.getOutput()).isZero();
        assertThat(filesOf(packageDir)).containsExactlyInAnyOrderElementsOf(DTOS);
        assertThat(Files.readString(packageDir.resolve("Pet.java"))).contains("package org.acme;");
        assertThat(dir.resolve("rest.yaml")).exists();
        assertThat(tempDirectory).as("the temporary directory of the generator is removed").isNotNull().doesNotExist();
    }

    @Test
    void warnsWhenTheSpecificationHasNoSchemas() throws Exception {
        Path spec = Files.writeString(dir.resolve("no-schemas.json"), """
                {"openapi": "3.0.2", "info": {"title": "No schemas", "version": "1"},
                 "paths": {"/ping": {"get": {"responses": {"200": {"description": "pong"}}}}}}
                """);

        int exitCode = generate(spec.toString(), dir.resolve("model"));

        assertThat(exitCode).as(printer.getOutput()).isZero();
        assertThat(printer.getOutput()).contains("No DTOs were generated");
        assertThat(dir.resolve("model")).doesNotExist();
    }

    @Test
    void replacesTheDtosOfAnEarlierRunAndKeepsTheOtherFiles() throws Exception {
        Path packageDir = dir.resolve("org").resolve("acme");
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve("Pet.java"), "an earlier run");
        Files.writeString(packageDir.resolve("Mine.java"), "not generated");

        int exitCode = generate(packageDir, "--package=org.acme");

        assertThat(exitCode).as(printer.getOutput()).isZero();
        assertThat(Files.readString(packageDir.resolve("Pet.java"))).contains("package org.acme;");
        assertThat(Files.readString(packageDir.resolve("Mine.java"))).isEqualTo("not generated");
    }

    @Test
    void failsWhenTheDtosCannotBeWritten() throws Exception {
        // a file where the directory of the package would be
        Path packageDir = Files.writeString(dir.resolve("model"), "a file");

        int exitCode = generate(packageDir, "--package=model");

        assertThat(exitCode).isEqualTo(1);
        assertThat(printer.getOutput()).contains("ERROR: Failed to write the generated DTOs to " + packageDir);
    }

    @Test
    void copiesADirectoryIntoOneWithoutParents() throws IOException {
        Path source = Files.createDirectories(dir.resolve("source").resolve("sub"));
        Files.writeString(source.resolve("A.java"), "a");
        Path target = dir.resolve("x").resolve("y");

        CodeRestGenerator.copyDirectory(source.getParent(), target);

        assertThat(target.resolve("sub").resolve("A.java")).hasContent("a");
    }

    private int generate(Path packageDir, String... args) throws Exception {
        return generate(SPEC, packageDir, args);
    }

    private int generate(String spec, Path packageDir, String... args) throws Exception {
        CodeRestGenerator command = new CodeRestGenerator(new CamelJBangMain().withPrinter(printer)) {
            @Override
            Path packageDirectory() {
                return packageDir;
            }

            @Override
            Path createTempDirectory() throws IOException {
                // in the directory of the test, which sees whether it is removed
                tempDirectory = Files.createTempDirectory(dir, "gendto");
                return tempDirectory;
            }
        };
        CommandLineTestHelper.parse(command, Stream.concat(
                Stream.of("--input=" + spec, "--dto", "--output=" + dir.resolve("rest.yaml")),
                Stream.of(args)).toArray(String[]::new));
        return command.doCall();
    }

    private static List<String> filesOf(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(f -> f.getFileName().toString()).toList();
        }
    }

}
