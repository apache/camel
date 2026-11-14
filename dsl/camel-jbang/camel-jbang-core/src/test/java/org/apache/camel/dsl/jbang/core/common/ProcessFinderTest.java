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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Which integrations a name or pid finds: a running integration has a live process and a status file, and its Camel has
 * not stopped.
 */
class ProcessFinderTest {

    private static final long CURRENT = 99999L;

    @TempDir
    Path home;

    private Path previousHome;
    private final List<ProcessHandle> processes = new ArrayList<>();
    private MockedStatic<ProcessHandle> mocked;

    @BeforeEach
    void setup() throws Exception {
        previousHome = CommandLineHelper.getHomeDir();
        CommandLineHelper.useHomeDir(home.toString());
        Files.createDirectories(CommandLineHelper.getCamelDir());

        mocked = mockStatic(ProcessHandle.class);
        ProcessHandle current = handle(CURRENT);
        mocked.when(ProcessHandle::current).thenReturn(current);
        mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> processes.stream());
    }

    @AfterEach
    void restore() {
        mocked.close();
        CommandLineHelper.useHomeDir(previousHome.toString());
    }

    @Test
    void findsRunningIntegrations() throws Exception {
        running(100, "orders", 5);
        running(200, "billing", 5);
        // Camel has stopped: the launcher of an integration that runs in a child process
        running(300, "launcher", 9);
        // a status file left behind by a process that is gone
        status(400, "gone", 5);
        // a process without status file is no Camel integration
        processes.add(handle(500));

        assertThat(ProcessFinder.findAll()).extracting(CamelProcess::pid).containsExactly(100L, 200L);
        assertThat(ProcessFinder.find("*")).extracting(CamelProcess::pid).containsExactly(100L, 200L);
    }

    @Test
    void aStatusFileOlderThanItsProcessIsLeftOverAndFindsNothing() throws Exception {
        Instant start = Instant.now().minus(Duration.ofMinutes(5));
        // a status file left behind by an integration that was killed, and its pid is now another process
        status(100, "orders", 5);
        Files.setLastModifiedTime(CommandLineHelper.getCamelDir().resolve("100-status.json"),
                FileTime.from(start.minus(Duration.ofHours(1))));
        processes.add(handle(100, start));
        // a running integration writes its status after it started
        status(200, "billing", 5);
        processes.add(handle(200, start));

        // camel stop must not signal the process that now has the pid
        assertThat(ProcessFinder.findAll()).extracting(CamelProcess::pid).containsExactly(200L);
        assertThat(ProcessFinder.find("orders")).isEmpty();
        assertThat(ProcessFinder.find("100")).isEmpty();
        assertThat(ProcessFinder.find("billing")).extracting(CamelProcess::pid).containsExactly(200L);
    }

    @Test
    void nameIsAPrefixUnlessItEndsWithExclamationMark() throws Exception {
        running(100, "orders", 5);
        running(200, "orders-eu", 5);

        assertThat(ProcessFinder.find("ord")).extracting(CamelProcess::pid).containsExactly(100L, 200L);
        assertThat(ProcessFinder.find("orders!")).extracting(CamelProcess::pid).containsExactly(100L);
        assertThat(ProcessFinder.find("ord!")).isEmpty();
        // a regular expression
        assertThat(ProcessFinder.find("orders-(eu|us)")).extracting(CamelProcess::pid).containsExactly(200L);
    }

    @Test
    void aNameOfARunningIntegrationMatchesOnlyThatIntegration() throws Exception {
        running(100, "orders", 5);
        running(200, "orders-v2", 5);
        running(300, "orders-eu", 5);

        // camel stop orders must not stop orders-v2 and orders-eu
        assertThat(ProcessFinder.find("orders")).extracting(CamelProcess::pid).containsExactly(100L);
        assertThat(ProcessFinder.find("ORDERS")).extracting(CamelProcess::pid).containsExactly(100L);
        assertThat(ProcessFinder.requireAny("orders")).extracting(CamelProcess::pid).containsExactly(100L);
        // no integration has these names, so they stay prefixes and patterns
        assertThat(ProcessFinder.find("order")).extracting(CamelProcess::pid).containsExactly(100L, 200L, 300L);
        assertThat(ProcessFinder.find("orders*")).extracting(CamelProcess::pid).containsExactly(100L, 200L, 300L);
        assertThat(ProcessFinder.find("orders-e")).extracting(CamelProcess::pid).containsExactly(300L);
        assertThat(ProcessFinder.find("*")).hasSize(3);
    }

    @Test
    void aFileNameOrContextNameOfARunningIntegrationMatchesOnlyThatIntegration() throws Exception {
        // the name comes from the main class, and the context has a name of its own
        JsonObject root = statusOf("billing", 5);
        JsonObject runtime = new JsonObject();
        runtime.put("mainClass", "Orders");
        root.put("runtime", runtime);
        Files.writeString(CommandLineHelper.getCamelDir().resolve("100-status.json"), root.toJson());
        processes.add(handle(100));
        running(200, "billing-eu", 5);
        running(300, "orders-v2", 5);

        assertThat(ProcessFinder.find("billing")).extracting(CamelProcess::pid).containsExactly(100L);
        assertThat(ProcessFinder.find("orders")).extracting(CamelProcess::pid).containsExactly(100L);
    }

    @Test
    void theDefaultContextNameOfTheCliMatchesNothing() throws Exception {
        // the name comes from the main class, and the context has the default name of the Camel CLI
        JsonObject root = statusOf("CamelJBang", 5);
        JsonObject runtime = new JsonObject();
        runtime.put("mainClass", "Orders");
        root.put("runtime", runtime);
        Files.writeString(CommandLineHelper.getCamelDir().resolve("100-status.json"), root.toJson());
        processes.add(handle(100));

        assertThat(ProcessFinder.find("Camel")).isEmpty();
        assertThat(ProcessFinder.find("Ord")).extracting(CamelProcess::name).containsExactly("Orders");
    }

    @Test
    void pidMustBeARunningIntegration() throws Exception {
        running(100, "orders", 5);
        processes.add(handle(500));

        assertThat(ProcessFinder.find("100")).extracting(CamelProcess::name).containsExactly("orders");
        // a process that is no Camel integration, and a pid that does not exist
        assertThat(ProcessFinder.find("500")).isEmpty();
        assertThat(ProcessFinder.find("600")).isEmpty();
    }

    @Test
    void aNameNeverMatchesThisProcessButItsPidDoes() throws Exception {
        status(CURRENT, "cli", 5);
        processes.add(handle(CURRENT));

        assertThat(ProcessFinder.find("cli")).isEmpty();
        assertThat(ProcessFinder.find(String.valueOf(CURRENT))).hasSize(1);
    }

    @Test
    void processKnowsItsVersionAndContextName() throws Exception {
        JsonObject root = statusOf("orders", 5);
        ((JsonObject) root.get("context")).put("version", "4.24.0-SNAPSHOT");
        Files.writeString(CommandLineHelper.getCamelDir().resolve("100-status.json"), root.toJson());
        processes.add(handle(100));

        CamelProcess p = ProcessFinder.requireOne("orders");
        assertThat(p.camelVersion()).isEqualTo("4.24.0-SNAPSHOT");
        assertThat(p.contextName()).isEqualTo("orders");
    }

    @Test
    void requireOneNeedsExactlyOneMatch() throws Exception {
        running(100, "orders", 5);
        running(200, "orders-eu", 5);

        assertThat(ProcessFinder.requireOne("orders!").pid()).isEqualTo(100L);
        assertThatThrownBy(() -> ProcessFinder.requireOne("billing"))
                .isInstanceOf(CliException.class)
                .hasMessage("No running Camel integration matches: billing")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
        // an integration named orders runs, so orders is not ambiguous
        assertThat(ProcessFinder.requireOne("orders").pid()).isEqualTo(100L);
        assertThatThrownBy(() -> ProcessFinder.requireOne("ord"))
                .isInstanceOf(CliException.class)
                .hasMessage("Name or pid ord matches 2 running Camel integrations: 100 (orders), 200 (orders-eu)."
                            + " Specify the pid, or a name that matches exactly one.")
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void requireAnyNeedsAMatch() throws Exception {
        running(100, "orders", 5);

        assertThat(ProcessFinder.requireAny("ord")).hasSize(1);
        assertThatThrownBy(() -> ProcessFinder.requireAny("billing"))
                .isInstanceOf(CliException.class)
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void noCamelDirectoryFindsNothing() {
        CommandLineHelper.useHomeDir(home.resolve("missing").toString());

        assertThat(ProcessFinder.findAll()).isEmpty();
        assertThat(ProcessFinder.find("orders")).isEmpty();
    }

    private void running(long pid, String contextName, int phase) throws Exception {
        status(pid, contextName, phase);
        processes.add(handle(pid));
    }

    private static void status(long pid, String contextName, int phase) throws Exception {
        Files.writeString(CommandLineHelper.getCamelDir().resolve(pid + "-status.json"),
                statusOf(contextName, phase).toJson());
    }

    private static JsonObject statusOf(String contextName, int phase) {
        JsonObject context = new JsonObject();
        context.put("name", contextName);
        context.put("phase", phase);
        JsonObject root = new JsonObject();
        root.put("context", context);
        return root;
    }

    private static ProcessHandle handle(long pid) {
        return handle(pid, null);
    }

    private static ProcessHandle handle(long pid, Instant start) {
        ProcessHandle ph = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(ph.pid()).thenReturn(pid);
        when(ph.info()).thenReturn(info);
        when(info.commandLine()).thenReturn(Optional.empty());
        when(info.startInstant()).thenReturn(Optional.ofNullable(start));
        return ph;
    }
}
