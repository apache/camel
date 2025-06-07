package org.apache.camel.dsl.jbang.core.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DependencyRuntimeTest extends CamelCommandBaseTest {

    @TempDir
    File tempDir;

    DependencyRuntime command;
    Path pomFilePath;

    private static Stream<Arguments> testOutputArguments() {
        return Stream.of(Arguments.of(TestArguments.QUARKUS_POM, TestArguments.QUARKUS_POM_OUTPUT));
    }

    private static Stream<Arguments> testJsonOutputArguments() {
        return Stream.of(Arguments.of(TestArguments.QUARKUS_POM, TestArguments.QUARKUS_POM_JSON_OUTPUT));
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
        static final String QUARKUS_POM_JSON_OUTPUT = "{\"type\":\"quarkus\",\"camelVersion\":\"4.11.0\",\"camelQuarkusVersion\":\"3.23.0\",\"quarkusVersion\":\"3.23.0\",\"quarkusGroupId\":\"io.quarkus.platform\"}";
    }
}
