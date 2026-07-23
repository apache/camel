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

import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListProcessTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim());
        }
    }

    @Test
    void testListsRunningProcess() throws Exception {
        JsonObject stats = buildStats();
        JsonObject status = buildContextStatus("myApp", 5);
        ((JsonObject) status.get("context")).put("statistics", stats);
        writeStatusFile(TEST_PID, status);

        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("myApp"), "Should contain context name");
            assertTrue(output.contains("Running"), "Should show Running status for phase 5");
            assertTrue(output.contains("1/1"), "Should show ready 1/1 when phase is 5");
        }
    }

    @Test
    void testListsStartingProcess() throws Exception {
        // Phase 3 = Starting (anything <= 4 is Starting)
        JsonObject status = buildContextStatus("startingApp", 3);
        writeStatusFile(TEST_PID, status);

        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("startingApp"));
            assertTrue(output.contains("Starting"), "Phase <= 4 should map to Starting");
            assertTrue(output.contains("0/1"), "Should show 0/1 ready when not phase 5");
        }
    }

    @Test
    void testPidOnlyFlag() throws Exception {
        JsonObject status = buildContextStatus("myApp", 5);
        writeStatusFile(TEST_PID, status);

        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));
        command.pid = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            List<String> lines = printer.getLines();
            assertEquals(1, lines.size(), "Should output exactly one PID line");
            assertEquals(String.valueOf(TEST_PID), lines.get(0).trim());
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject status = buildContextStatus("myApp", 5);
        writeStatusFile(TEST_PID, status);

        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.startsWith("["), "JSON output should be a JSON array");
            assertTrue(output.contains("myApp"), "JSON should contain the context name");
        }
    }

    @Test
    void testJsonPidOnlyFlag() throws Exception {
        JsonObject status = buildContextStatus("myApp", 5);
        writeStatusFile(TEST_PID, status);

        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));
        command.pid = true;
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.startsWith("["), "JSON pid output should be a JSON array");
            assertTrue(output.contains(String.valueOf(TEST_PID)), "Should contain the PID");
        }
    }

    @Test
    void testSortByName() throws Exception {
        long zebraPid = 11111L;
        long applePid = 22222L;

        JsonObject status1 = buildContextStatus("zebra", 5);
        JsonObject status2 = buildContextStatus("apple", 5);
        writeStatusFile(zebraPid, status1);
        writeStatusFile(applePid, status2);

        // zebra process first in stream — sort must promote apple to top
        assertAppleBeforeZebra(zebraPid, applePid);
        // apple process first in stream — sort must preserve the order
        printer = new StringPrinter();
        assertAppleBeforeZebra(applePid, zebraPid);
    }

    private void assertAppleBeforeZebra(long firstPid, long secondPid) throws Exception {
        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));
        command.sort = "name";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph1 = mockProcessHandle(firstPid);
            ProcessHandle ph2 = mockProcessHandle(secondPid);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            // fresh stream per invocation so allProcesses() can be called multiple times
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph1, ph2));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            int applePos = output.indexOf("apple");
            int zebraPos = output.indexOf("zebra");
            assertTrue(applePos < zebraPos, "Sort by name ascending: apple must precede zebra regardless of stream order");
        }
    }

    @Test
    void testReloadErrorShownInOutput() throws Exception {
        JsonObject status = buildContextStatus("faultyApp", 5);
        JsonObject stats = (JsonObject) ((JsonObject) status.get("context")).get("statistics");
        JsonObject reload = new JsonObject();
        JsonObject lastError = new JsonObject();
        lastError.put("message", "Route file parse error");
        reload.put("lastError", lastError);
        stats.put("reload", reload);
        writeStatusFile(TEST_PID, status);

        ListProcess command = new ListProcess(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("Error"), "Should show Error status when reload failed");
            // Description column is width-limited; "Reload" is the start of the description
            assertTrue(output.contains("Reload"), "Should display the reload description");
        }
    }

    private static JsonObject buildStats() {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", "100");
        stats.put("exchangesFailed", "5");
        stats.put("exchangesInflight", "3");
        return stats;
    }
}
