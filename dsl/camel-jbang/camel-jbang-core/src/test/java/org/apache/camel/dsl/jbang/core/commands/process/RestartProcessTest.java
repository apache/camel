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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.BackgroundLog;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestartProcessTest extends ProcessCommandTestSupport {

    @Test
    void testReturnsErrorWhenNoMatchingProcess() throws Exception {
        RestartProcess command = new RestartProcess(new CamelJBangMain().withPrinter(printer));
        command.name = "nonExistentApp";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            CliException e = assertThrows(CliException.class, command::doCall);

            assertEquals(CliExitCode.NOT_FOUND, e.getExitCode(), "Should return not found when no process matches");
            assertEquals("No running Camel integration matches: nonExistentApp", e.getMessage());
        }
    }

    @Test
    void testReturnsErrorWhenMultipleProcessesMatch() throws Exception {
        long pid2 = TEST_PID + 1;
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeStatusFile(pid2, buildContextStatus("myApp", 5));

        RestartProcess command = new RestartProcess(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph1 = mockProcessHandle(TEST_PID);
            ProcessHandle ph2 = mockProcessHandle(pid2);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph1, ph2));

            CliException e = assertThrows(CliException.class, command::doCall);

            assertEquals(CliExitCode.NOT_FOUND, e.getExitCode(), "Should return not found when multiple processes match");
            assertTrue(e.getMessage().contains("matches 2 running Camel integrations"), e.getMessage());
        }
    }

    @Test
    void testCommandLineNotAvailableIsAFailure() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));

        RestartProcess command = new RestartProcess(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            mocked.when(() -> ProcessHandle.of(TEST_PID)).thenReturn(Optional.of(ph));
            when(ph.info().command()).thenReturn(Optional.empty());
            when(ph.info().arguments()).thenReturn(Optional.empty());

            CliException e = assertThrows(CliException.class, command::doCall);

            assertEquals(CliExitCode.FAILURE, e.getExitCode());
            assertEquals("Cannot restart: command line not available for PID " + TEST_PID, e.getMessage());
            // the integration is not stopped
            verify(ph, never()).destroy();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testRestartStopsAndRelaunchesTheIntegration() throws Exception {
        // the integration: it writes the status of a started Camel for its pid, as Camel does, and runs until stopped
        // (exec: the shell becomes the sleep, so stopping the pid stops it)
        String script = "echo '{\"context\":{\"name\":\"myApp\",\"phase\":5}}' > '"
                        + CommandLineHelper.getCamelDir().toAbsolutePath() + "'/$$-status.json; echo started; exec sleep 60";

        int exit;
        try {
            exit = restart("/bin/sh", "-c", script);
        } finally {
            stopTheRelaunchedIntegration();
        }

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("Stopping Camel integration: myApp (PID: " + TEST_PID + ")"), out);
        assertTrue(out.contains("Restarted Camel integration: myApp (PID: "), out);
        assertTrue(out.contains("Log: "), out);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testRelaunchThatStopsRightAwayIsAFailure() throws Exception {
        CliException e = assertThrows(CliException.class, () -> restart("/bin/sh", "-c", "echo boom; exit 3"));

        assertEquals(CliExitCode.FAILURE, e.getExitCode());
        assertTrue(e.getMessage().contains("stopped right after the restart (exit code 3)"), e.getMessage());
        // the end of its log
        assertTrue(printer.getStderr().contains("boom"), printer.getStderr());
    }

    @Test
    void testCommandLineOfTheIntegration() {
        assertEquals(List.of("java", "-jar", "app.jar"), RestartProcess.parseCommandLine("java  -jar app.jar"));
        assertEquals(List.of("java", "-Dname=my app", "-jar", "app.jar"),
                RestartProcess.parseCommandLine("java \"-Dname=my app\" -jar app.jar"));
        assertEquals(List.of("echo", "say \"hi\""), RestartProcess.parseCommandLine("echo \"say \\\"hi\\\"\""));
        // a Windows command line keeps the backslashes of its paths
        assertEquals(List.of("C:\\Program Files\\Java\\bin\\java.exe", "-jar", "C:\\work\\app.jar"),
                RestartProcess.parseCommandLine(
                        "\"C:\\Program Files\\Java\\bin\\java.exe\" -jar C:\\work\\app.jar"));
        // and a quoted path that ends with a backslash: two backslashes in front of the closing quote are one
        assertEquals(List.of("\\\\server\\share\\", "-x"),
                RestartProcess.parseCommandLine("\"\\\\server\\share\\\\\" -x"));
        // three are one and a quote character
        assertEquals(List.of("say \\\"hi"), RestartProcess.parseCommandLine("\"say \\\\\\\"hi\""));
    }

    // restarts myApp, which runs the given command
    private int restart(String command, String... args) throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        RestartProcess restart = new RestartProcess(new CamelJBangMain().withPrinter(printer));
        restart.name = "myApp";
        restart.timeout = 10000;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            // found, and gone once it is stopped
            mocked.when(() -> ProcessHandle.of(TEST_PID)).thenReturn(Optional.of(ph), Optional.empty());
            when(ph.info().command()).thenReturn(Optional.of(command));
            when(ph.info().arguments()).thenReturn(Optional.of(args));

            int exit = restart.doCall();
            // there is no pid file of camel run to delete, so the integration is asked to stop by its process
            verify(ph).destroy();
            return exit;
        }
    }

    private void stopTheRelaunchedIntegration() {
        Matcher m = Pattern.compile("Restarted Camel integration: myApp \\(PID: (\\d+)\\)").matcher(printer.getOutput());
        if (m.find()) {
            ProcessHandle.of(Long.parseLong(m.group(1))).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void testTailOfTheLog(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("restart.log");
        Files.writeString(log, "one\ntwo\nthree\n");

        assertEquals("two" + System.lineSeparator() + "three", BackgroundLog.tail(log, 2));
        assertEquals("", BackgroundLog.tail(dir.resolve("missing.log"), 2));
    }
}
