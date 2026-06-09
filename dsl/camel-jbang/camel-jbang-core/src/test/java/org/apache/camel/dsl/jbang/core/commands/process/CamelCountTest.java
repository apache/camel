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
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class CamelCountTest extends ProcessCommandTestSupport {

    @Test
    void testShowsTableWithTotalsAndFailed() throws Exception {
        JsonObject status = buildContextStatus("countApp", 5);
        JsonObject stats = (JsonObject) ((JsonObject) status.get("context")).get("statistics");
        stats.put("exchangesTotal", "42");
        stats.put("exchangesFailed", "3");
        writeStatusFile(TEST_PID, status);

        CamelCount command = new CamelCount(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("countApp"), "Should show context name");
            assertTrue(output.contains("42"), "Should show total exchanges");
            assertTrue(output.contains("3"), "Should show failed exchanges");
        }
    }

    @Test
    void testTotalFlagOutputsOnlyCount() throws Exception {
        JsonObject status = buildContextStatus("totalApp", 5);
        JsonObject stats = (JsonObject) ((JsonObject) status.get("context")).get("statistics");
        stats.put("exchangesTotal", "100");
        stats.put("exchangesFailed", "5");
        writeStatusFile(TEST_PID, status);

        CamelCount command = new CamelCount(new CamelJBangMain().withPrinter(printer));
        command.total = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("100", printer.getOutput().trim());
        }
    }

    @Test
    void testFailFlagOutputsOnlyFailedCount() throws Exception {
        JsonObject status = buildContextStatus("failApp", 5);
        JsonObject stats = (JsonObject) ((JsonObject) status.get("context")).get("statistics");
        stats.put("exchangesTotal", "50");
        stats.put("exchangesFailed", "7");
        writeStatusFile(TEST_PID, status);

        CamelCount command = new CamelCount(new CamelJBangMain().withPrinter(printer));
        command.fail = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("7", printer.getOutput().trim());
        }
    }

    @Test
    void testTotalAndFailFlagsOutputCommaSeparated() throws Exception {
        JsonObject status = buildContextStatus("bothApp", 5);
        JsonObject stats = (JsonObject) ((JsonObject) status.get("context")).get("statistics");
        stats.put("exchangesTotal", "20");
        stats.put("exchangesFailed", "2");
        writeStatusFile(TEST_PID, status);

        CamelCount command = new CamelCount(new CamelJBangMain().withPrinter(printer));
        command.total = true;
        command.fail = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("20,2", printer.getOutput().trim());
        }
    }

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        CamelCount command = new CamelCount(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim());
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject status = buildContextStatus("jsonCountApp", 5);
        JsonObject stats = (JsonObject) ((JsonObject) status.get("context")).get("statistics");
        stats.put("exchangesTotal", "15");
        stats.put("exchangesFailed", "1");
        writeStatusFile(TEST_PID, status);

        CamelCount command = new CamelCount(new CamelJBangMain().withPrinter(printer));
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.startsWith("["), "JSON output should be an array");
            assertTrue(output.contains("jsonCountApp"), "JSON should contain context name");
            assertTrue(output.contains("total"), "JSON should contain total field");
        }
    }
}
