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
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.FileUtil;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

class ExportTest {

    private File workingDir;

    @BeforeEach
    public void setup() throws IOException {
        workingDir = Files.createTempDirectory("camel-export").toFile();
    }

    @AfterEach
    public void end() throws IOException {
        // force removing, since deleteOnExit is not removing.
        FileUtil.removeDir(workingDir);
    }

    private static Stream<Arguments> runtimeProvider() {
        return Stream.of(
                Arguments.of(RuntimeType.quarkus),
                Arguments.of(RuntimeType.springBoot),
                Arguments.of(RuntimeType.main));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateProject(RuntimeType rt) throws Exception {
        Export command = createCommand(rt, new String[] { "classpath:route.yaml" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateProjectWithBuildProperties(RuntimeType rt) throws Exception {
        Export command = new ExportSpringBoot(new CamelJBangMain());
        CommandLine.populateCommand(command, "--gav=examples:route:1.0.0", "--dir=" + workingDir,
                "--runtime=%s".formatted(rt.runtime()), "--build-property=foo=bar", "target/test-classes/route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
        Assertions.assertEquals("bar", model.getProperties().getProperty("foo"));
        command.printConfigurationValues("export command");
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void testShouldGenerateProjectMultivalue(RuntimeType rt) throws Exception {
        Export command = new ExportSpringBoot(new CamelJBangMain());
        CommandLine.populateCommand(command, "--gav=examples:route:1.0.0", "--dir=" + workingDir,
                "--runtime=%s".formatted(rt.runtime()), "--dep=foo:bar:1.0,jupiter:rocks:2.0",
                // it's important for the --build-property to be the last parameter to test a previous
                // export error when this property had arity=*
                "--build-property=foo=bar", "--build-property=camel=rocks", "target/test-classes/route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
        Assertions.assertEquals("rocks", model.getProperties().getProperty("camel"));
        Assertions.assertEquals("bar", model.getProperties().getProperty("foo"));
        Assertions.assertTrue(containsDependency(model.getDependencies(), "foo", "bar", "1.0"),
                "Generated pom doesn't contain foo:bar:1.0 dependency");
        Assertions.assertTrue(containsDependency(model.getDependencies(), "jupiter", "rocks", "2.0"),
                "Generated pom doesn't contain jupiter:rocks:2.0 dependency");
        command.printConfigurationValues("export command");
    }

    private Export createCommand(RuntimeType rt, String[] files, String... args) {
        Export command = new ExportSpringBoot(new CamelJBangMain());
        CommandLine.populateCommand(command, "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet",
                "--runtime=%s".formatted(rt.runtime()));
        if (args != null) {
            CommandLine.populateCommand(command, args);
        }
        command.files = Arrays.asList(files);
        return command;
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldExportLazyBean(RuntimeType rt) throws Exception {
        Export command = createCommand(rt, new String[] { "classpath:route.yaml", "file:src/test/resources/LazyFoo.java" },
                "--gav=examples:route:1.0.0", "--dir=" + workingDir, "--quiet", "--lazy-bean");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
    }

    private Model readMavenModel() throws Exception {
        File f = workingDir.toPath().resolve("pom.xml").toFile();
        Assertions.assertTrue(f.isFile(), "Not a pom.xml file: " + f);
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model model = mavenReader.read(new FileReader(f));
        model.setPomFile(f);
        return model;
    }

    private boolean containsDependency(List<Dependency> deps, String group, String artifact, String version) {
        Dependency dep = new Dependency();
        dep.setGroupId(group);
        dep.setArtifactId(artifact);
        dep.setVersion(version);
        boolean found = false;
        for (int i = 0; i < deps.size() && !found; i++) {
            Dependency d = deps.get(i);
            found = toGAV(d).equals(toGAV(dep));
        }
        return found;
    }

    private String toGAV(Dependency d) {
        return d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getVersion();
    }
}
