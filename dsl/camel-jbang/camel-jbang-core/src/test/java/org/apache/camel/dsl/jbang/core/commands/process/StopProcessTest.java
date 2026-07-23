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
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StopProcessTest extends ProcessCommandTestSupport {

    @Test
    void testStopDeletesPidFile() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        // the pid file (named by PID only) signals graceful shutdown when deleted
        Path pidFile = CommandLineHelper.getCamelDir().resolve(Long.toString(TEST_PID));
        Files.writeString(pidFile, "");
        assertTrue(Files.exists(pidFile), "Pid file must exist before stop");

        StopProcess command = new StopProcess(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertFalse(Files.exists(pidFile), "Pid file should be deleted to signal graceful stop");
            assertTrue(printer.getOutput().contains("Shutting down"), "Should print shutdown message");
        }
    }

    @Test
    void testStopWithKillCallsDestroyForcibly() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));

        StopProcess command = new StopProcess(new CamelJBangMain().withPrinter(printer));
        command.kill = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            mocked.when(() -> ProcessHandle.of(TEST_PID)).thenReturn(Optional.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            verify(ph).destroyForcibly();
            assertTrue(printer.getOutput().contains("Killing"), "Should print kill message");
        }
    }

    @Test
    void testStopWithNoMatchingProcessDoesNothing() throws Exception {
        StopProcess command = new StopProcess(new CamelJBangMain().withPrinter(printer));
        command.name = "nonExistentApp";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim(), "No output expected when no processes match");
        }
    }
}
