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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.util.FileUtil;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

class ExportMainJkubeTest {

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
                Arguments.of(RuntimeType.main));
    }

    @ParameterizedTest
    @MethodSource("runtimeProvider")
    public void shouldGenerateProjectWithJib(RuntimeType rt) throws Exception {
        // prepare as we need application.properties that contains jkube settings
        Files.copy(new File("src/test/resources/application-jkube.properties").toPath(), profile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        Export command = new Export(new CamelJBangMain());
        CommandLine.populateCommand(command, "--gav=examples:route:1.0.0", "--dir=" + workingDir,
                "--runtime=%s".formatted(rt.runtime()), "target/test-classes/route.yaml");
        int exit = command.doCall();

        Assertions.assertEquals(0, exit);
        Model model = readMavenModel();
        Assertions.assertEquals("examples", model.getGroupId());
        Assertions.assertEquals("route", model.getArtifactId());
        Assertions.assertEquals("1.0.0", model.getVersion());
        Assertions.assertEquals("17", model.getProperties().getProperty("java.version"));
        Assertions.assertEquals("abc", model.getProperties().getProperty("jib.label"));
        Assertions.assertEquals("eclipse-temurin:17-jre", model.getProperties().getProperty("jib.from.image"));

        // should contain jib and jkube plugin
        Assertions.assertEquals(5, model.getBuild().getPlugins().size());
        Plugin p = model.getBuild().getPlugins().get(3);
        Assertions.assertEquals("com.google.cloud.tools", p.getGroupId());
        Assertions.assertEquals("jib-maven-plugin", p.getArtifactId());
        p = model.getBuild().getPlugins().get(4);
        Assertions.assertEquals("org.eclipse.jkube", p.getGroupId());
        Assertions.assertEquals("kubernetes-maven-plugin", p.getArtifactId());
        Assertions.assertEquals("1.18.1", p.getVersion());

        command.printConfigurationValues("export command");
    }

    private Model readMavenModel() throws Exception {
        File f = workingDir.toPath().resolve("pom.xml").toFile();
        Assertions.assertTrue(f.isFile(), "Not a pom.xml file: " + f);
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model model = mavenReader.read(new FileReader(f));
        model.setPomFile(f);
        return model;
    }

}
