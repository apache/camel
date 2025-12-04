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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

class DependencyListTest extends CamelCommandBaseTest {

    private File workingDir;

    @BeforeEach
    public void setup() throws Exception {
        super.setup();
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-export").toFile();
    }

    @AfterEach
    public void end() throws IOException {
        // force removing, since deleteOnExit is not removing.
        FileUtil.removeDir(workingDir);
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldDependencyList(RuntimeType rt) throws Exception {
        DependencyList command = createCommand(
                rt,
                new String[] {"classpath:route.yaml"},
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                "--camel-version=4.11.0",
                "--quarkus-version=3.22.2",
                "--spring-boot-version=3.4.5");
        int exit = command.doCall();
        Assertions.assertEquals(0, exit);

        List<String> lines = printer.getLines();

        Assertions.assertTrue(lines.size() >= 4);
        if (rt == RuntimeType.quarkus) {
            Assertions.assertEquals("org.apache.camel.quarkus:camel-quarkus-core:3.22.2", lines.get(0));
        } else if (rt == RuntimeType.springBoot) {
            Assertions.assertEquals("org.springframework.boot:spring-boot-starter-actuator:3.4.5", lines.get(0));
            Assertions.assertEquals("org.apache.camel.springboot:camel-spring-boot-starter:4.11.0", lines.get(1));
        } else {
            Assertions.assertEquals("org.apache.camel:camel-main:4.11.0", lines.get(0));
        }
    }

    private DependencyList createCommand(RuntimeType rt, String[] files, String... args) {
        DependencyList command = new DependencyList(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(
                command,
                "--gav=examples:route:1.0.0",
                "--dir=" + workingDir,
                "--quiet",
                "--runtime=%s".formatted(rt.runtime()));
        if (args != null) {
            CommandLine.populateCommand(command, args);
        }
        command.files = Arrays.asList(files);
        return command;
    }

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.quarkus),
                Arguments.of(RuntimeType.springBoot),
                Arguments.of(RuntimeType.main));
    }
}
