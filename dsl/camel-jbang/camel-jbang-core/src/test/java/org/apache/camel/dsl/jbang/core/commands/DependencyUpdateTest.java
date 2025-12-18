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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyUpdateTest extends CamelCommandBaseTestSupport {

    private File workingDir;

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-dependency-update-tests").toFile();
    }

    @AfterEach
    void end() {
        // force removing, since deleteOnExit is not removing.
        FileUtil.removeDir(workingDir);
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    void shouldDependencyUpdate(RuntimeType rt) throws Exception {
        prepareMavenProject(rt);
        checkNoUpdateOnFreshlyGeneratedproject();
        addArangodbToCamelFile();
        checkOneDependencyAddedForArangoDb(rt);
    }

    private void checkOneDependencyAddedForArangoDb(RuntimeType rt) throws Exception, IOException {
        StringPrinter secondUpdateCommandPrinter = new StringPrinter();
        DependencyUpdate command = new DependencyUpdate(new CamelJBangMain().withPrinter(secondUpdateCommandPrinter));
        CommandLine.populateCommand(command,
                "--dir=" + workingDir,
                new File(workingDir, "pom.xml").getAbsolutePath());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit, secondUpdateCommandPrinter.getLines().toString());

        List<String> lines = secondUpdateCommandPrinter.getLines();
        Assertions.assertEquals(1, lines.size(), secondUpdateCommandPrinter.getLines().toString());
        Assertions.assertEquals("Updating pom.xml with 1 dependency added", lines.get(0));

        String pomContent = new String(Files.readAllBytes(new File(workingDir, "pom.xml").toPath()));
        switch (rt) {
            case quarkus: {
                assertThat(pomContent).contains("camel-quarkus-arangodb");
                break;
            }
            case springBoot: {
                assertThat(pomContent).contains("camel-arangodb-starter");
                break;
            }
            case main: {
                assertThat(pomContent).contains("camel-arangodb<");
                break;
            }
        }
    }

    private void addArangodbToCamelFile() throws IOException {
        File camelFile = new File(workingDir, "src/main/resources/camel/my.camel.yaml");
        String camelFileContent = new String(Files.readAllBytes(camelFile.toPath()));
        camelFileContent = camelFileContent.replace("- log: ${body}", """
                - to:
                             uri: arangodb
                             parameters:
                               database: demo
                """);
        Files.writeString(camelFile.toPath(), camelFileContent);
    }

    private void checkNoUpdateOnFreshlyGeneratedproject() throws Exception {
        DependencyUpdate command = new DependencyUpdate(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                "--dir=" + workingDir,
                new File(workingDir, "pom.xml").getAbsolutePath());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit, printer.getLines().toString());

        List<String> lines = printer.getLines();
        Assertions.assertEquals(1, lines.size(), printer.getLines().toString());
        Assertions.assertEquals("No updates to pom.xml", lines.get(0));
    }

    private void prepareMavenProject(RuntimeType rt) throws Exception {
        StringPrinter initCommandPrinter = new StringPrinter();
        Init initCommand = new Init(new CamelJBangMain().withPrinter(initCommandPrinter));
        String camelFilePath = new File(workingDir, "my.camel.yaml").getAbsolutePath();
        CommandLine.populateCommand(initCommand, camelFilePath);
        Assertions.assertEquals(0, initCommand.doCall(), initCommandPrinter.getLines().toString());

        StringPrinter exportCommandPrinter = new StringPrinter();
        Export exportCommand = new Export(new CamelJBangMain().withPrinter(exportCommandPrinter));
        CommandLine.populateCommand(exportCommand,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--camel-version=4.13.0",
                "--runtime=" + rt.runtime(),
                camelFilePath);
        Assertions.assertEquals(0, exportCommand.doCall(), exportCommandPrinter.getLines().toString());
    }

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.quarkus),
                Arguments.of(RuntimeType.springBoot),
                Arguments.of(RuntimeType.main));
    }

}
