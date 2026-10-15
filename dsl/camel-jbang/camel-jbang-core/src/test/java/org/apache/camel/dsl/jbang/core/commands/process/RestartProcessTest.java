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
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.junit.jupiter.api.Test;
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

// TODO: add tests for the successful restart path once ProcessBuilder/Runtime.exec can be mocked
//       or an integration-test harness is available. The current tests only cover early-exit guards.
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
    void testTailOfTheLog(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("restart.log");
        Files.writeString(log, "one\ntwo\nthree\n");

        assertEquals("two" + System.lineSeparator() + "three", RestartProcess.tail(log, 2));
        assertEquals("", RestartProcess.tail(dir.resolve("missing.log"), 2));
    }
}
