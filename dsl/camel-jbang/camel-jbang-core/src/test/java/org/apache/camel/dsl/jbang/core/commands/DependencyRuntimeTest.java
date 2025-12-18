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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DependencyRuntimeTest extends CamelCommandBaseTestSupport {

    @TempDir
    File tempDir;

    DependencyRuntime command;
    Path pomFilePath;

    private static Stream<Arguments> testOutputArguments() {
        return Stream.of(
                Arguments.of(TestArguments.MAIN_POM, TestArguments.MAIN_POM_OUTPUT),
                Arguments.of(TestArguments.QUARKUS_POM, TestArguments.QUARKUS_POM_OUTPUT),
                Arguments.of(TestArguments.SPRING_BOOT_POM, TestArguments.SPRING_BOOT_POM_OUTPUT));
    }

    private static Stream<Arguments> testJsonOutputArguments() {
        return Stream.of(
                Arguments.of(TestArguments.MAIN_POM, TestArguments.MAIN_POM_JSON_OUTPUT),
                Arguments.of(TestArguments.QUARKUS_POM, TestArguments.QUARKUS_POM_JSON_OUTPUT),
                Arguments.of(TestArguments.SPRING_BOOT_POM, TestArguments.SPRING_BOOT_POM_JSON_OUTPUT));
    }

    @BeforeEach
    void setUp() {
        command = new DependencyRuntime(new CamelJBangMain().withPrinter(printer));

        pomFilePath = tempDir.toPath().resolve("pom.xml");
        command.pomXml = pomFilePath;
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(pomFilePath);
    }

    @Test
    void testNoPomFile() throws Exception {
        command.pomXml = tempDir.toPath().resolve("non-existing/pom.xml");
        int exit = command.doCall();

        assertEquals(1, exit);
    }

    @ParameterizedTest
    @MethodSource("testOutputArguments")
    void testStringOutput(String testPomXmlFile, String output) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(testPomXmlFile)) {
            if (in == null) {
                throw new IllegalStateException(String.format("Resource not found: %s", testPomXmlFile));
            }
            Files.copy(in, pomFilePath);
        }

        int exit = command.doCall();
        assertEquals(0, exit);
        assertEquals(output, printer.getOutput());
    }

    @ParameterizedTest
    @MethodSource("testJsonOutputArguments")
    void testJsonOutput(String testPomXmlFile, String jsonOutput) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(testPomXmlFile)) {
            if (in == null) {
                throw new IllegalStateException(String.format("Resource not found: %s", testPomXmlFile));
            }
            Files.copy(in, pomFilePath);
        }

        command.jsonOutput = true;
        int exit = command.doCall();

        assertEquals(0, exit);
        assertEquals(jsonOutput, printer.getOutput());
    }

    private static class TestArguments {
        static final String QUARKUS_POM = "pom-xml-files/quarkus-pom.xml";
        static final String QUARKUS_POM_OUTPUT = """
                Runtime: quarkus
                Camel Version: 4.11.0
                Camel Quarkus Version: 3.23.0
                Quarkus Version: 3.23.0""";
        static final String QUARKUS_POM_JSON_OUTPUT
                = "{\"runtime\":\"quarkus\",\"camelVersion\":\"4.11.0\",\"camelQuarkusVersion\":\"3.23.0\",\"quarkusVersion\":\"3.23.0\",\"quarkusBomGroupId\":\"io.quarkus.platform\",\"quarkusBomArtifactId\":\"quarkus-bom\",\"camelQuarkusBomGroupId\":\"io.quarkus.platform\",\"camelQuarkusBomArtifactId\":\"quarkus-camel-bom\"}";

        static final String SPRING_BOOT_POM = "pom-xml-files/springboot-pom.xml";
        static final String SPRING_BOOT_POM_OUTPUT = """
                Runtime: spring-boot
                Camel Version: 4.14.0
                Camel Spring Boot Version: 4.14.0
                Spring Boot Version: 3.5.3""";
        static final String SPRING_BOOT_POM_JSON_OUTPUT
                = "{\"runtime\":\"spring-boot\",\"camelVersion\":\"4.14.0\",\"camelSpringBootVersion\":\"4.14.0\",\"springBootVersion\":\"3.5.3\",\"camelSpringBootBomGroupId\":\"org.apache.camel.springboot\",\"camelSpringBootBomArtifactId\":\"camel-spring-boot-bom\"}";

        static final String MAIN_POM = "pom-xml-files/main-pom.xml";
        static final String MAIN_POM_OUTPUT = """
                Runtime: main
                Camel Version: 4.14.0""";
        static final String MAIN_POM_JSON_OUTPUT
                = "{\"runtime\":\"main\",\"camelVersion\":\"4.14.0\"}";

    }
}
