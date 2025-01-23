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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
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

class ExportMainApplicationProperties {

    private File workingDir;
    private File profile = new File(".", "application.properties");

    @BeforeEach
    public void setup() throws IOException {
        Path base = Paths.get("target");
        workingDir = Files.createTempDirectory(base, "camel-export").toFile();
    }

    @AfterEach
    public void end() throws IOException {
        // force removing, since deleteOnExit is not removing.
        FileUtil.removeDir(workingDir);
        FileUtil.deleteFile(profile);
    }

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.quarkus),
                Arguments.of(RuntimeType.springBoot),
                Arguments.of(RuntimeType.main));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportUserPropertyOverride(RuntimeType rt) throws Exception {
        // prepare as we need application.properties that contains configuration to be overridden
        Files.copy(
                new File("src/test/resources/sample-application.properties").toPath(),
                profile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        // We need a real file as we want to test the generated content
        Export command = createCommand(rt,
                new String[] { "src/test/resources/route.yaml", "src/test/resources/sample-application.properties" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--property", "hello=test");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        // Application properties
        File appProperties = new File(workingDir + "/src/main/resources", "application.properties");
        Assertions.assertTrue(appProperties.exists(), "Missing application properties");
        Properties appProps = new Properties();
        appProps.load(new FileInputStream(appProperties));
        Assertions.assertEquals("test", appProps.getProperty("hello"));
        Assertions.assertEquals("true", appProps.getProperty("another.property"));
    }

    private Export createCommand(RuntimeType rt, String[] files, String... args) {
        Export command = new Export(new CamelJBangMain());
        CommandLine.populateCommand(command, "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--runtime=%s".formatted(rt.runtime()));
        if (args != null) {
            CommandLine.populateCommand(command, args);
        }
        command.files = Arrays.asList(files);
        return command;
    }

}
