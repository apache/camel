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
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListActivityTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoActivityFile() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));

        ListActivity command = createCommand();

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim());
        }
    }

    @Test
    void testShowsActivityEntries() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeActivityFile(TEST_PID, activityJson(
                activityEntry("EX-001", "route1", false, 42, "direct://start"),
                activityEntry("EX-002", "route2", true, 150, "timer://tick")));

        ListActivity command = createCommand();

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("EX-001"), "Should show first exchange id");
            assertTrue(output.contains("EX-002"), "Should show second exchange id");
            assertTrue(output.contains("route1"), "Should show first route id");
            assertTrue(output.contains("route2"), "Should show second route id");
            assertTrue(output.contains("OK"), "Should show OK status");
            assertTrue(output.contains("FAILED"), "Should show FAILED status");
            assertTrue(output.contains("42ms"), "Should show elapsed time");
            assertTrue(output.contains("150ms"), "Should show elapsed time");
        }
    }

    @Test
    void testFilterByRoute() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeActivityFile(TEST_PID, activityJson(
                activityEntry("EX-001", "route1", false, 42, "direct://start"),
                activityEntry("EX-002", "route2", false, 100, "timer://tick")));

        ListActivity command = createCommand();
        command.filter = "route1";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("route1"), "Should show matching route");
            assertFalse(output.contains("route2"), "Should not show filtered-out route");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeActivityFile(TEST_PID, activityJson(
                activityEntry("EX-100", "myRoute", false, 55, "direct://foo")));

        ListActivity command = createCommand();
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.startsWith("["), "JSON output should be array");
            assertTrue(output.contains("EX-100"));
            assertTrue(output.contains("myRoute"));
            assertTrue(output.contains("\"status\":\"OK\""));
        }
    }

    @Test
    void testLimitRows() throws Exception {
        writeStatusFile(TEST_PID, buildContextStatus("myApp", 5));
        writeActivityFile(TEST_PID, activityJson(
                activityEntry("EX-001", "route1", false, 10, "direct://a"),
                activityEntry("EX-002", "route1", false, 20, "direct://a"),
                activityEntry("EX-003", "route1", false, 30, "direct://a")));

        ListActivity command = createCommand();
        command.limit = 2;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            // count exchange ID occurrences — should be limited to 2
            long count = output.lines().filter(l -> l.contains("EX-")).count();
            assertEquals(2, count, "Should limit to 2 rows");
        }
    }

    private ListActivity createCommand() {
        ListActivity cmd = new ListActivity(new CamelJBangMain().withPrinter(printer));
        cmd.sort = "since";
        return cmd;
    }

    private static void writeActivityFile(long pid, JsonObject activity) throws Exception {
        Path f = CommandLineHelper.getCamelDir().resolve(pid + "-activity.json");
        Files.writeString(f, activity.toJson());
    }

    private static JsonObject activityJson(JsonObject... entries) {
        JsonArray arr = new JsonArray();
        for (JsonObject entry : entries) {
            arr.add(entry);
        }
        JsonObject root = new JsonObject();
        root.put("activitySize", 100);
        root.put("activity", arr);
        return root;
    }

    private static JsonObject activityEntry(
            String exchangeId, String routeId, boolean failed, long elapsed, String endpointUri) {
        JsonObject e = new JsonObject();
        e.put("uid", System.nanoTime());
        e.put("exchangeId", exchangeId);
        e.put("routeId", routeId);
        e.put("failed", failed);
        e.put("elapsed", elapsed);
        e.put("timestamp", System.currentTimeMillis());
        if (endpointUri != null) {
            e.put("fromEndpointUri", endpointUri);
        }
        return e;
    }
}
