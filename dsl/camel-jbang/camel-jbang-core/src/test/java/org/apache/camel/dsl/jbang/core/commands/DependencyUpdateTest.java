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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        workingDir = Files.createTempDirectory("camel-dependency-update-tests").toFile();
    }

    @AfterEach
    void end() {
        // force removing, since deleteOnExit is not removing.
        FileUtil.removeDir(workingDir);
    }

    // ==================== Maven dependency update (existing export-based tests) ====================

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    void shouldDependencyUpdate(RuntimeType rt) throws Exception {
        prepareMavenProject(rt);
        checkNoUpdateOnFreshlyGeneratedproject();
        addArangodbToCamelFile();
        checkOneDependencyAddedForArangoDb(rt);
    }

    private void checkOneDependencyAddedForArangoDb(RuntimeType rt) throws Exception {
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

    // ==================== scan-routes with Maven (fixture-based tests) ====================

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    void shouldScanRoutesAddDependency(RuntimeType rt) throws Exception {
        prepareFixtureProject(rt);

        // add arangodb to the route
        addArangodbToCamelFile();

        // run with --scan-routes
        StringPrinter updatePrinter = new StringPrinter();
        DependencyUpdate command = new DependencyUpdate(new CamelJBangMain().withPrinter(updatePrinter));
        CommandLine.populateCommand(command,
                "--scan-routes",
                "--dir=" + workingDir,
                new File(workingDir, "pom.xml").getAbsolutePath());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit, updatePrinter.getLines().toString());

        String pomContent = Files.readString(new File(workingDir, "pom.xml").toPath());
        switch (rt) {
            case quarkus:
                assertThat(pomContent).contains("camel-quarkus-arangodb");
                break;
            case springBoot:
                assertThat(pomContent).contains("camel-arangodb-starter");
                break;
            case main:
                assertThat(pomContent).contains("camel-arangodb<");
                break;
        }
    }

    @Test
    void shouldScanRoutesRemoveUnusedMavenDependency() throws Exception {
        prepareFixtureProject(RuntimeType.main);

        // manually add camel-kafka to the pom.xml (which is not used by the route)
        File pomFile = new File(workingDir, "pom.xml");
        String pomContent = Files.readString(pomFile.toPath());
        // insert before the closing </dependencies> tag
        String kafkaDep = "        <dependency>\n"
                          + "            <groupId>org.apache.camel</groupId>\n"
                          + "            <artifactId>camel-kafka</artifactId>\n"
                          + "        </dependency>\n    ";
        pomContent = pomContent.replaceFirst("(</dependencies>)", kafkaDep + "$1");
        Files.writeString(pomFile.toPath(), pomContent);

        // verify kafka is there
        assertThat(Files.readString(pomFile.toPath())).contains("camel-kafka");

        // run with --scan-routes to sync
        StringPrinter updatePrinter = new StringPrinter();
        DependencyUpdate command = new DependencyUpdate(new CamelJBangMain().withPrinter(updatePrinter));
        CommandLine.populateCommand(command,
                "--scan-routes",
                "--dir=" + workingDir,
                pomFile.getAbsolutePath());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit, updatePrinter.getLines().toString());

        // camel-kafka should be removed (not used in routes)
        String updatedPom = Files.readString(pomFile.toPath());
        assertThat(updatedPom).doesNotContain("camel-kafka");
        // should still have camel-yaml-dsl (used for the route file)
        assertThat(updatedPom).contains("camel-yaml-dsl");
    }

    @Test
    void shouldScanRoutesAddAndRemoveMavenDependency() throws Exception {
        prepareFixtureProject(RuntimeType.main);

        // manually add camel-kafka to the pom.xml (which is not used by the route)
        File pomFile = new File(workingDir, "pom.xml");
        String pomContent = Files.readString(pomFile.toPath());
        String kafkaDep = "        <dependency>\n"
                          + "            <groupId>org.apache.camel</groupId>\n"
                          + "            <artifactId>camel-kafka</artifactId>\n"
                          + "        </dependency>\n    ";
        pomContent = pomContent.replaceFirst("(</dependencies>)", kafkaDep + "$1");
        Files.writeString(pomFile.toPath(), pomContent);

        // add arangodb to the route
        addArangodbToCamelFile();

        // run with --scan-routes to sync
        StringPrinter updatePrinter = new StringPrinter();
        DependencyUpdate command = new DependencyUpdate(new CamelJBangMain().withPrinter(updatePrinter));
        CommandLine.populateCommand(command,
                "--scan-routes",
                "--dir=" + workingDir,
                pomFile.getAbsolutePath());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit, updatePrinter.getLines().toString());

        String updatedPom = Files.readString(pomFile.toPath());
        // camel-kafka should be removed (not used in routes)
        assertThat(updatedPom).doesNotContain("camel-kafka");
        // camel-arangodb should be added (used in the route)
        assertThat(updatedPom).contains("camel-arangodb<");
        // should still have camel-yaml-dsl (used for the route file)
        assertThat(updatedPom).contains("camel-yaml-dsl");
    }

    // ==================== JBang file tests (inline fixtures) ====================

    @Test
    void shouldPreserveNonCamelDepsInJBangFile() throws Exception {
        // create a Java file with mixed Camel and non-Camel //DEPS
        Path javaFile = createFile("MyRoute.java", """
                ///usr/bin/env jbang
                //DEPS org.apache.camel:camel-bom:4.13.0@pom
                //DEPS org.apache.camel:camel-kafka
                //DEPS com.google.guava:guava:33.0.0-jre
                //DEPS io.netty:netty-all:4.1.100.Final
                import org.apache.camel.builder.RouteBuilder;
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick").to("log:info");
                    }
                }
                """);

        // run scan-routes with just the Java file (no separate route files)
        StringPrinter p = new StringPrinter();
        DependencyUpdate command = new DependencyUpdate(new CamelJBangMain().withPrinter(p));
        CommandLine.populateCommand(command,
                "--scan-routes",
                "--dir=" + workingDir,
                javaFile.toAbsolutePath().toString());
        int exit = command.doCall();
        Assertions.assertEquals(0, exit, p.getLines().toString());

        String content = Files.readString(javaFile);
        // non-Camel deps should be preserved
        assertThat(content).contains("//DEPS com.google.guava:guava:33.0.0-jre");
        assertThat(content).contains("//DEPS io.netty:netty-all:4.1.100.Final");
        // should have camel-bom (resolved fresh)
        assertThat(content).contains("//DEPS org.apache.camel:camel-bom:");
        // kafka should be removed (not used in the route definition)
        assertThat(content).doesNotContain("camel-kafka");
    }

    @Test
    void shouldScanRoutesIdempotentJBang() throws Exception {
        Path javaFile = createFile("MyRoute.java", """
                ///usr/bin/env jbang
                //DEPS com.google.guava:guava:33.0.0-jre
                import org.apache.camel.builder.RouteBuilder;
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick").to("log:info");
                    }
                }
                """);

        // run scan-routes twice
        for (int run = 0; run < 2; run++) {
            StringPrinter p = new StringPrinter();
            DependencyUpdate command = new DependencyUpdate(new CamelJBangMain().withPrinter(p));
            CommandLine.populateCommand(command,
                    "--scan-routes",
                    "--dir=" + workingDir,
                    javaFile.toAbsolutePath().toString());
            int exit = command.doCall();
            Assertions.assertEquals(0, exit, p.getLines().toString());
        }

        String content = Files.readString(javaFile);
        // should have exactly one camel-bom line
        long bomCount = content.lines().filter(l -> l.contains("camel-bom")).count();
        assertThat(bomCount).isEqualTo(1);
        // non-Camel deps should still be there
        assertThat(content).contains("//DEPS com.google.guava:guava:33.0.0-jre");
    }

    @Test
    void shouldUpdateMultipleJBangFiles() throws Exception {
        Path java1 = createFile("MyRoute1.java", """
                ///usr/bin/env jbang
                import org.apache.camel.builder.RouteBuilder;
                public class MyRoute1 extends RouteBuilder {
                    public void configure() {
                        from("timer:tick").to("log:info");
                    }
                }
                """);
        Path java2 = createFile("MyRoute2.java", """
                ///usr/bin/env jbang
                import org.apache.camel.builder.RouteBuilder;
                public class MyRoute2 extends RouteBuilder {
                    public void configure() {
                        from("timer:tick").to("log:info");
                    }
                }
                """);

        DependencyUpdate command = new DependencyUpdate(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command,
                "--dir=" + workingDir,
                java1.toAbsolutePath().toString(),
                java2.toAbsolutePath().toString());

        int exit = command.doCall();
        Assertions.assertEquals(0, exit, printer.getLines().toString());

        // both files should have //DEPS
        String content1 = Files.readString(java1);
        String content2 = Files.readString(java2);
        assertThat(content1).contains("//DEPS org.apache.camel:camel-bom:");
        assertThat(content2).contains("//DEPS org.apache.camel:camel-bom:");
    }

    // ==================== Helpers ====================

    private void addArangodbToCamelFile() throws Exception {
        File camelFile = new File(workingDir, "src/main/resources/camel/my.camel.yaml");
        String content = Files.readString(camelFile.toPath());
        content = content.replace("- log: ${body}", """
                - to:
                             uri: arangodb
                             parameters:
                               database: demo
                """);
        Files.writeString(camelFile.toPath(), content);
    }

    /**
     * Prepares a project from fixture files in src/test/resources/dependency-update. Faster than Init+Export since no
     * command invocation is needed.
     */
    private void prepareFixtureProject(RuntimeType rt) throws Exception {
        String pomResource = switch (rt) {
            case quarkus -> "dependency-update/quarkus-pom.xml";
            case springBoot -> "dependency-update/springboot-pom.xml";
            case main -> "dependency-update/main-pom.xml";
        };

        // copy pom.xml
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(pomResource)) {
            Files.copy(in, new File(workingDir, "pom.xml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // copy route file
        Path routeDir = new File(workingDir, "src/main/resources/camel").toPath();
        Files.createDirectories(routeDir);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("dependency-update/route.yaml")) {
            Files.copy(in, routeDir.resolve("my.camel.yaml"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path createFile(String name, String content) throws Exception {
        Path file = workingDir.toPath().resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static Stream<Arguments> runtimeProvider() {
        Stream.Builder<Arguments> builder = Stream.builder();
        builder.add(Arguments.of(RuntimeType.quarkus));
        // camel-spring-boot 4.19+ requires JDK 21 (Spring Boot 4)
        if (Runtime.version().feature() >= 21) {
            builder.add(Arguments.of(RuntimeType.springBoot));
        }
        builder.add(Arguments.of(RuntimeType.main));
        return builder.build();
    }

}
