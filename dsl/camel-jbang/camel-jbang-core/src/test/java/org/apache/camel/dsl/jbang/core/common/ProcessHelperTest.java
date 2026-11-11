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
package org.apache.camel.dsl.jbang.core.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The name the CLI shows for a running integration (camel ps, camel log and the other commands).
 */
class ProcessHelperTest {

    private static final String MAVEN
            = "/usr/bin/java -classpath plexus-classworlds-2.8.0.jar org.codehaus.plexus.classworlds.launcher.Launcher";

    // the pid of the processes whose command line is in the proc directory of the test
    private static final long PID = 4242;

    // a classpath as long as the one of camel run started through jbang (about 15 KB)
    private static final String CLASSPATH = classpath();

    @TempDir
    Path proc;

    @BeforeEach
    void useProc() {
        ProcessHelper.useProcRoot(proc);
    }

    @AfterEach
    void restoreProc() {
        ProcessHelper.useProcRoot(null);
    }

    @Test
    void mainClassOfTheIntegration() {
        JsonObject root = root("camel-1");
        root.put("runtime", new JsonObject(Map.of("mainClass", "com.acme.OrdersApplication")));

        assertThat(ProcessHelper.extractName(root, process("java -jar orders.jar", null)))
                .isEqualTo("com.acme.OrdersApplication");
    }

    @Test
    void routeFilesOfCamelRun() {
        ProcessHandle ph = process(
                "java -cp camel-launcher.jar main.CamelJBang run src/hello.camel.yaml README.md Foo.java application.properties",
                null);

        // only the route files, without their directory
        assertThat(ProcessHelper.extractName(root("CamelJBang"), ph)).isEqualTo("hello.camel.yaml Foo.java");
    }

    @Test
    void mainClassOnTheCommandLine() {
        assertThat(ProcessHelper.extractName(root("camel-1"), process("java -cp lib/* com.acme.Main", null)))
                .isEqualTo("com.acme.Main");
    }

    @Test
    void nameOfTheCamelContextWithoutACommandLine() {
        assertThat(ProcessHelper.extractName(root("orders"), process(null, null))).isEqualTo("orders");
        assertThat(ProcessHelper.extractName(root("orders"), null)).isEqualTo("orders");
        assertThat(ProcessHelper.extractName(null, null)).isEmpty();
    }

    @Test
    void springBootRunIsTheProcessMavenStarted() {
        ProcessHandle maven = process(MAVEN + " spring-boot:run", null);
        ProcessHandle app = process("java -cp camel-spring-boot-4.24.0.jar com.acme.Application", maven);
        when(maven.children()).thenAnswer(inv -> Stream.of(app));

        // Camel runs in the process Maven started, so Maven itself has no name to be listed with
        assertThat(ProcessHelper.extractName(null, maven)).isEmpty();
        assertThat(ProcessHelper.extractName(null, app)).isEqualTo("com.acme.Application");
    }

    @Test
    void quarkusDevIsNamedAfterTheMavenGoal() {
        ProcessHandle maven = process(MAVEN + " quarkus:dev", null);
        ProcessHandle app = process("java -cp camel-quarkus-core-3.30.0.jar -jar quarkus-run.jar", maven);

        assertThat(ProcessHelper.extractName(null, app)).isEqualTo("quarkus:dev");
    }

    /**
     * CAMEL-26397: on Linux the JDK reads one page (4 KB) of /proc/&lt;pid&gt;/cmdline, so a longer command line has no
     * arguments, and the command line it gives is cut short. The whole command line is read from that file then.
     */
    @Test
    void theWholeCommandLineOfAProcessWhoseCommandLineTheJdkCutShort() throws Exception {
        String full = "/opt/java/bin/java -classpath " + CLASSPATH + " main.CamelJBang run hello.camel.yaml --dev";
        ProcessHandle ph = cutShort(full);
        cmdline(PID, "java", "-classpath", CLASSPATH, "main.CamelJBang", "run", "hello.camel.yaml", "--dev");

        // the command is the executable the JDK gives, as for a command line it has all of
        assertThat(ProcessHelper.commandLine(ph))
                .containsExactly("/opt/java/bin/java", "-classpath", CLASSPATH, "main.CamelJBang", "run",
                        "hello.camel.yaml", "--dev");
        assertThat(ProcessHelper.commandLineText(ph)).isEqualTo(full);
        // the route files after main.CamelJBang run, which the cut command line does not reach
        assertThat(ProcessHelper.extractName(root("CamelJBang"), ph)).isEqualTo("hello.camel.yaml");
    }

    @Test
    void theCommandLineTheJdkHasAllOf() throws Exception {
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(info.command()).thenReturn(Optional.of("/usr/bin/java"));
        when(info.arguments()).thenReturn(Optional.of(new String[] { "-jar", "app.jar" }));
        ProcessHandle ph = mock(ProcessHandle.class);
        when(ph.info()).thenReturn(info);
        // not read
        cmdline(PID, "other");

        assertThat(ProcessHelper.commandLine(ph)).containsExactly("/usr/bin/java", "-jar", "app.jar");
    }

    @Test
    void theArgumentsAreSeparatedByANulByte() throws Exception {
        ProcessHandle ph = withoutArguments("/bin/sh", "sh -c  true");
        // an empty argument (the JDK gives no arguments of a process that has one), and a last one without its NUL
        Path dir = Files.createDirectories(proc.resolve(Long.toString(PID)));
        Files.write(dir.resolve("cmdline"), "sh\0-c\0\0true".getBytes(StandardCharsets.UTF_8));

        assertThat(ProcessHelper.commandLine(ph)).containsExactly("/bin/sh", "-c", "", "true");
    }

    @Test
    void aCommandLineCutShortIsNotUsed() throws Exception {
        String full = "/opt/java/bin/java -classpath " + CLASSPATH + " main.CamelJBang run hello.camel.yaml";
        ProcessHandle ph = cutShort(full);

        // no /proc/<pid>/cmdline: the process has ended, or another OS than Linux
        assertThat(ProcessHelper.commandLine(ph)).isNull();
        assertThat(ProcessHelper.commandLineText(ph)).isEmpty();
        // the end of a cut command line is the middle of the classpath, not a name
        assertThat(ProcessHelper.extractName(root("orders"), ph)).isEqualTo("orders");

        // an empty file
        cmdline(PID);
        assertThat(ProcessHelper.commandLine(ph)).isNull();
    }

    @Test
    void aCommandLineIsCutShortAtTheSizeOfAPage() {
        assertThat(ProcessHelper.isCut("x".repeat(4096))).isTrue();
        assertThat(ProcessHelper.isCut("x".repeat(16384))).isTrue();
        assertThat(ProcessHelper.isCut("x".repeat(4095))).isFalse();
        assertThat(ProcessHelper.isCut("x".repeat(4097))).isFalse();
        assertThat(ProcessHelper.isCut("x".repeat(6000))).isFalse();
    }

    @Test
    void nothingIsReadOfAProcessTheJdkKnowsNothingOf() throws Exception {
        ProcessHandle ph = process(null, null);
        when(ph.pid()).thenReturn(PID);
        cmdline(PID, "java", "-jar", "app.jar");

        assertThat(ProcessHelper.commandLine(ph)).isNull();
    }

    /** CAMEL-26397: a process with a command line longer than a page, as the JDK and the system give it. */
    @Test
    @EnabledOnOs(OS.LINUX)
    void theWholeCommandLineOfAProcessOnLinux() throws Exception {
        ProcessHelper.useProcRoot(null);
        String longArgument = "x".repeat(5000);
        // two commands, so the shell does not replace itself with sleep
        Process p = new ProcessBuilder("sh", "-c", "sleep 60; exit 0", "sh", longArgument).start();
        try {
            ProcessHandle ph = p.toHandle();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(ProcessHelper.commandLine(ph))
                    .containsExactly(ph.info().command().orElse("sh"), "-c", "sleep 60; exit 0", "sh", longArgument));
        } finally {
            p.destroyForcibly();
        }
    }

    // a java process whose command line the JDK cut short: no arguments, and the first page of the command line
    private static ProcessHandle cutShort(String commandLine) {
        return withoutArguments("/opt/java/bin/java", commandLine.substring(0, 4096));
    }

    // a process the JDK gives the command and the command line of, but no arguments
    private static ProcessHandle withoutArguments(String command, String commandLine) {
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(info.command()).thenReturn(Optional.of(command));
        when(info.arguments()).thenReturn(Optional.empty());
        when(info.commandLine()).thenReturn(Optional.of(commandLine));
        ProcessHandle ph = mock(ProcessHandle.class);
        when(ph.pid()).thenReturn(PID);
        when(ph.info()).thenReturn(info);
        when(ph.parent()).thenReturn(Optional.empty());
        when(ph.children()).thenAnswer(inv -> Stream.empty());
        return ph;
    }

    // the command line of the process in the proc directory, as /proc/<pid>/cmdline has it
    private void cmdline(long pid, String... args) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            sb.append(arg).append('\0');
        }
        Path dir = Files.createDirectories(proc.resolve(Long.toString(pid)));
        Files.writeString(dir.resolve("cmdline"), sb);
    }

    private static String classpath() {
        List<String> jars = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            jars.add("/home/user/.m2/repository/org/example/some-dependency/1.2." + i + "/some-dependency-1.2." + i + ".jar");
        }
        return String.join(":", jars);
    }

    private static JsonObject root(String contextName) {
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", contextName)));
        return root;
    }

    private static ProcessHandle process(String commandLine, ProcessHandle parent) {
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(info.commandLine()).thenReturn(Optional.ofNullable(commandLine));
        ProcessHandle ph = mock(ProcessHandle.class);
        when(ph.info()).thenReturn(info);
        when(ph.parent()).thenReturn(Optional.ofNullable(parent));
        when(ph.children()).thenAnswer(inv -> Stream.empty());
        return ph;
    }
}
