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

import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

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

            int exit = command.doCall();

            assertEquals(1, exit, "Should return non-zero when no process matches");
            assertTrue(printer.getOutput().contains("No matching"), "Should print no-match message");
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

            int exit = command.doCall();

            assertEquals(1, exit, "Should return non-zero when multiple processes match");
            assertTrue(printer.getOutput().contains("Multiple"), "Should print ambiguity message");
        }
    }
}
