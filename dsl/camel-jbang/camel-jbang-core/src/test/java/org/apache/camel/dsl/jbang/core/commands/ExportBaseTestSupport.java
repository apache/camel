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
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ExportBaseTestSupport extends CamelCommandBaseTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ExportBaseTestSupport.class);

    // Use a released Camel version for Spring Boot tests because the Spring Boot catalog provider
    // (camel-catalog-provider-springboot) comes from the separate camel-spring-boot project.
    // Its SNAPSHOT may not be deployed to Apache Snapshots, causing flaky CI failures.
    // Released versions are always available on Maven Central.
    protected static final String RELEASED_CAMEL_VERSION = "4.13.0";

    protected File workingDir;
    protected final List<File> baseDirs = new ArrayList<>();

    @BeforeEach
    @Override
    public void setup() throws Exception {
        super.setup();
        LOG.info("Preparing {}", getClass().getSimpleName());
        useTestWorkDir();
        workingDir = Files.createTempDirectory("camel-export").toFile();
    }

    @AfterEach
    public void end() throws IOException {
        // force removing, since deleteOnExit is not removing.
        FileUtil.removeDir(workingDir);
        baseDirs.forEach(FileUtil::removeDir);
    }

    protected static Stream<Arguments> runtimeProvider() {
        Stream.Builder<Arguments> builder = Stream.builder();
        builder.add(Arguments.of(RuntimeType.quarkus));
        // camel-spring-boot 4.19+ requires JDK 21 (Spring Boot 4)
        if (Runtime.version().feature() >= 21) {
            builder.add(Arguments.of(RuntimeType.springBoot));
        }
        builder.add(Arguments.of(RuntimeType.main));
        return builder.build();
    }

    protected static Stream<Arguments> runtimeAndJavaVersionProvider() {
        return runtimeProvider()
                .flatMap(rt -> Stream.of("21", "25").map(java -> Arguments.of(rt.get()[0], java)));
    }

    protected static Stream<Arguments> consoleArtifactProvider() {
        Stream.Builder<Arguments> builder = Stream.builder();
        builder.add(Arguments.of(RuntimeType.quarkus, "org.apache.camel.quarkus", "camel-quarkus-console"));
        // camel-spring-boot 4.19+ requires JDK 21 (Spring Boot 4)
        if (Runtime.version().feature() >= 21) {
            builder.add(Arguments.of(RuntimeType.springBoot, "org.apache.camel.springboot", "camel-console-starter"));
        }
        return builder.build();
    }

    protected Export createCommand(RuntimeType rt, String[] files, String... args) {
        Export command = new Export(new CamelJBangMain());
        CommandLineTestHelper.parse(command, "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--runtime=%s".formatted(rt.runtime()),
                CamelCommandBaseTestSupport.quarkusExtRegistry());
        // Pin Spring Boot tests to a released version to avoid SNAPSHOT download failures
        if (rt == RuntimeType.springBoot && !containsCamelVersion(args)) {
            CommandLineTestHelper.parse(command, "--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        if (args != null) {
            CommandLineTestHelper.parse(command, args);
        }
        command.files = Arrays.asList(files);
        return command;
    }

    protected static boolean containsCamelVersion(String... args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith("--camel-version=")) {
                return true;
            }
        }
        return false;
    }

    protected Model readMavenModel() throws Exception {
        return readMavenModel(new File(workingDir, "pom.xml"));
    }

    protected Model readMavenModel(File f) throws Exception {
        Assertions.assertTrue(f.isFile(), "Not a pom.xml file: " + f);
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model model = mavenReader.read(new FileReader(f));
        model.setPomFile(f);
        return model;
    }

    protected boolean containsDependency(List<Dependency> deps, String group, String artifact, String version) {
        Dependency dep = new Dependency();
        dep.setGroupId(group);
        dep.setArtifactId(artifact);
        dep.setVersion(version);
        boolean found = false;
        for (int i = 0; i < deps.size() && !found; i++) {
            Dependency d = deps.get(i);
            if (version == null) {
                d.setVersion(null);
            }
            found = toGAV(d).equals(toGAV(dep));
        }
        return found;
    }

    protected static String toGAV(Dependency d) {
        return d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getVersion();
    }

    protected static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertApplicationPropertiesContent(RuntimeType rt, File appProps) throws Exception {
        try (FileInputStream fis = new FileInputStream(appProps)) {
            String content = IOHelper.loadText(fis);
            if (rt == RuntimeType.quarkus) {
                Assertions.assertFalse(content.contains("quarkus.native.resources.includes=camel/route.yaml"),
                        "should not contain quarkus.native.resources.includes property, was " + content);
            }
            Assertions.assertFalse(content.contains("camel.main.routes-include-pattern"),
                    "should not contain camel.main.routes-include-pattern property, was " + content);
            if (rt == RuntimeType.springBoot) {
                Assertions.assertTrue(content.contains("camel.main.run-controller=true"),
                        "should contain camel.main.run-controller property, was " + content);
            }
        }
    }

    protected void assertApplicationPropertiesContentJava(RuntimeType rt, File appProps, String appPackage)
            throws Exception {
        try (FileInputStream fis = new FileInputStream(appProps)) {
            String content = IOHelper.loadText(fis);
            if (rt == RuntimeType.main) {
                Assertions.assertTrue(content.contains("camel.main.basePackageScan=" + appPackage),
                        "should contain camel.main.basePackageScan property, but was " + content);
            } else {
                Assertions.assertFalse(content.contains("camel.main.basePackageScan"),
                        "should not contain camel.main.basePackageScan property, but was " + content);
            }
        }
    }

    protected int exportWithConsole(RuntimeType rt, String... extraArgs) throws Exception {
        return exportWithConsole(null, rt, extraArgs);
    }

    protected int exportWithConsole(Path baseDir, RuntimeType rt, String... extraArgs) throws Exception {
        Export command = new Export(new CamelJBangMain());
        command.exportBaseDir = baseDir;
        List<String> cmdArgs = new ArrayList<>(
                List.of("--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                        "--runtime=%s".formatted(rt.runtime()), "--console", "--port=0"));
        if (rt == RuntimeType.springBoot) {
            cmdArgs.add("--camel-version=" + RELEASED_CAMEL_VERSION);
        }
        cmdArgs.addAll(List.of(extraArgs));
        cmdArgs.add("target/test-classes/route.yaml");
        CommandLineTestHelper.parse(command, cmdArgs.toArray(String[]::new));
        return command.doCall();
    }

    protected Properties loadExportedProperties(String name) throws Exception {
        Properties props = new Properties();
        File f = new File(workingDir, "src/main/resources/" + name);
        Assertions.assertTrue(f.isFile(), "Missing " + name);
        try (FileInputStream fis = new FileInputStream(f)) {
            props.load(fis);
        }
        return props;
    }

    protected void assertConsoleDependencies(Model model, RuntimeType rt) {
        if (rt == RuntimeType.main) {
            for (String artifact : List.of("camel-console", "camel-management", "camel-health",
                    "camel-platform-http-main", "camel-platform-http-jolokia")) {
                Assertions.assertTrue(containsDependency(model.getDependencies(), "org.apache.camel", artifact, null),
                        "Missing dependency " + artifact);
            }
        } else if (rt == RuntimeType.springBoot) {
            for (String artifact : List.of("camel-console-starter", "camel-management-starter")) {
                Assertions.assertTrue(
                        containsDependency(model.getDependencies(), "org.apache.camel.springboot", artifact, null),
                        "Missing dependency " + artifact);
            }
        } else if (rt == RuntimeType.quarkus) {
            for (String artifact : List.of("camel-quarkus-console", "camel-quarkus-management")) {
                Assertions.assertTrue(
                        containsDependency(model.getDependencies(), "org.apache.camel.quarkus", artifact, null),
                        "Missing dependency " + artifact);
            }
        }
    }

    protected static void assertConsoleProperties(Properties props, RuntimeType rt) {
        // the developer console is enabled with the same camel-main option on all runtimes
        Assertions.assertEquals("true", exportedProperty(props, "camel.main.devConsoleEnabled"));
        if (rt == RuntimeType.main) {
            for (String key : List.of("camel.management.enabled", "camel.management.devConsoleEnabled",
                    "camel.management.healthCheckEnabled", "camel.management.infoEnabled",
                    "camel.management.jolokiaEnabled")) {
                Assertions.assertEquals("true", exportedProperty(props, key), "Missing property " + key);
            }
        } else if (rt == RuntimeType.springBoot) {
            // the console is the camel actuator endpoint, which must be exposed over the web
            Assertions.assertEquals("camel", props.getProperty("management.endpoints.web.exposure.include"));
        }
    }

    protected static String exportedProperty(Properties props, String key) {
        String v = props.getProperty(key);
        return v != null ? v : props.getProperty(StringHelper.camelCaseToDash(key));
    }

    protected Path baseDirWithApplicationProperties(String content) throws IOException {
        Path baseDir = Files.createTempDirectory("camel-export-base");
        baseDirs.add(baseDir.toFile());
        Files.writeString(baseDir.resolve("application.properties"), content);
        return baseDir;
    }

    protected static void assertEachArtifactOnce(Model model) {
        List<String> ga = model.getDependencies().stream()
                .map(d -> d.getGroupId() + ":" + d.getArtifactId())
                .collect(Collectors.toList());
        assertThat(ga).as("no duplicate artifacts in dependencies").doesNotHaveDuplicates();
    }

    protected static AutoCloseable systemProperty(String key, String value) {
        String previous = System.getProperty(key);
        System.setProperty(key, value);
        return () -> {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        };
    }
}
