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
package org.apache.camel.dsl.jbang.launcher;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Windows script of the Camel wrapper (camelw.cmd, which {@code camel wrapper} writes into a project) runs java,
 * PowerShell and curl of the system, never a java.bat, powershell.bat or curl.bat in the project: cmd looks for a bare
 * command name in the current directory before PATH.
 */
@EnabledOnOs(OS.WINDOWS)
class CamelwCmdTest {

    private static final Path SCRIPT = Paths.get("../camel-jbang-run/src/main/resources/camel-wrapper/camelw.cmd");
    private static final String VERSION = "9.9.9";

    @Test
    void runsTheJavaOfPathNotOneInTheProject(@TempDir Path base) throws Exception {
        Path project = project(base, "file:///not-used");
        plant(project, "java");
        Path user = Files.createDirectories(base.resolve("user"));
        writeMarkerJar(cachedJar(user));
        Map<String, String> env = environment(user);
        env.put("PATH", currentJavaHome().resolve("bin") + ";" + env.get("PATH"));

        FakeJava.Result r = FakeJava.runIn(project, project.resolve("camelw.cmd"), env, null, "hello");

        assertEquals(0, r.exitCode(), r.stderr());
        assertTrue(r.stdout().contains("RAN=NATIVE-JAVA"), r.stdout());
        assertTrue(r.stdout().contains("ARGS=[hello]"), r.stdout());
        assertFalse(Files.exists(project.resolve("planted-java.txt")), "the java.bat of the project ran");
    }

    @Test
    void failsWhenNoJavaIsOnPath(@TempDir Path base) throws Exception {
        Path project = project(base, "file:///not-used");
        plant(project, "java");
        Path user = Files.createDirectories(base.resolve("user"));
        writeMarkerJar(cachedJar(user));

        FakeJava.Result r = FakeJava.runIn(project, project.resolve("camelw.cmd"), environment(user), null, "hello");

        assertEquals(1, r.exitCode(), r.stdout());
        assertTrue(r.stderr().contains("no java.exe was found on PATH"), r.stderr());
        assertFalse(Files.exists(project.resolve("planted-java.txt")), "the java.bat of the project ran");
    }

    @Test
    void downloadsWithThePowerShellOrCurlOfWindows(@TempDir Path base) throws Exception {
        Path distribution = Files.createDirectories(base.resolve("distribution")).resolve("camel-launcher.jar");
        writeMarkerJar(distribution);
        Path project = project(base, distribution.toUri().toString());
        plant(project, "powershell");
        plant(project, "curl");
        Path user = Files.createDirectories(base.resolve("user"));
        Map<String, String> env = environment(user);
        env.put("PATH", currentJavaHome().resolve("bin") + ";" + env.get("PATH"));

        FakeJava.Result r = FakeJava.runIn(project, project.resolve("camelw.cmd"), env, null, "hello");

        assertEquals(0, r.exitCode(), r.stdout() + r.stderr());
        assertTrue(r.stdout().contains("downloaded successfully"), r.stdout());
        assertTrue(r.stdout().contains("RAN=NATIVE-JAVA"), r.stdout());
        assertTrue(Files.isRegularFile(cachedJar(user)), "the launcher JAR is not in the cache of the wrapper");
        assertFalse(Files.exists(project.resolve("planted-powershell.txt")), "the powershell.bat of the project ran");
        assertFalse(Files.exists(project.resolve("planted-curl.txt")), "the curl.bat of the project ran");
    }

    private static Path project(Path base, String distributionUrl) throws Exception {
        Path project = Files.createDirectories(base.resolve("project"));
        Files.copy(SCRIPT, project.resolve("camelw.cmd"));
        Files.writeString(Files.createDirectories(project.resolve(".camel")).resolve("camel-wrapper.properties"),
                "camel.version=" + VERSION + "\r\ndistributionUrl=" + distributionUrl + "\r\n");
        return project;
    }

    /**
     * A batch file in the project with the name of a tool, which leaves a file behind when it runs.
     */
    private static void plant(Path project, String tool) throws Exception {
        Files.writeString(project.resolve(tool + ".bat"),
                "@echo off\r\necho planted> \"%~dp0planted-" + tool + ".txt\"\r\nexit /b 0\r\n");
    }

    private static Path cachedJar(Path user) throws Exception {
        return Files.createDirectories(user.resolve(".camel").resolve("wrapper"))
                .resolve("camel-launcher-" + VERSION + ".jar");
    }

    private static Map<String, String> environment(Path user) {
        String systemRoot = System.getenv("SystemRoot");
        assertTrue(systemRoot != null && !systemRoot.isBlank(), "Windows test fixture requires SystemRoot");
        Map<String, String> env = new HashMap<>();
        env.put("PATH", Path.of(systemRoot, "System32").toString());
        env.put("USERPROFILE", user.toString());
        env.put("TEMP", System.getProperty("java.io.tmpdir"));
        env.put("TMP", System.getProperty("java.io.tmpdir"));
        return env;
    }

    private static Path currentJavaHome() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        assertTrue(Files.isRegularFile(javaHome.resolve("bin/java.exe")),
                "Windows test fixture requires the current JDK java.exe: " + javaHome);
        return javaHome;
    }

    /**
     * A runnable JAR whose main class prints RAN=NATIVE-JAVA and the arguments (see {@link CamelBatRealJavaMarker}).
     */
    private static void writeMarkerJar(Path jar) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, CamelBatRealJavaMarker.class.getName());
        String entryName = CamelBatRealJavaMarker.class.getName().replace('.', '/') + ".class";
        try (InputStream classBytes = CamelBatRealJavaMarker.class.getResourceAsStream("/" + entryName);
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            assertTrue(classBytes != null, "could not load marker class bytes");
            out.putNextEntry(new JarEntry(entryName));
            classBytes.transferTo(out);
            out.closeEntry();
        }
    }
}
