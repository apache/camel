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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class CamelRouteGroupStatusTest extends ProcessCommandTestSupport {

    @Test
    void testDisplaysRouteGroup() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithGroup("orderGroup", "Started", "10", "0"));

        CamelRouteGroupStatus command = new CamelRouteGroupStatus(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("myApp"), "Should show integration name");
            assertTrue(output.contains("orderGroup"), "Should show group name");
            assertTrue(output.contains("Started"), "Should show group state");
        }
    }

    @Test
    void testRunningFilterExcludesSuspendedGroups() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithGroup("suspendedGroup", "Suspended", "0", "0"));

        CamelRouteGroupStatus command = new CamelRouteGroupStatus(new CamelJBangMain().withPrinter(printer));
        command.running = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim(), "Suspended group should be hidden when --running is set");
        }
    }

    @Test
    void testFilterMeanExcludesFastGroups() throws Exception {
        // group mean=5 should be filtered out when --filter-mean=20
        writeStatusFile(TEST_PID, buildStatusWithGroup("fastGroup", "Started", "5", "0"));

        CamelRouteGroupStatus command = new CamelRouteGroupStatus(new CamelJBangMain().withPrinter(printer));
        command.mean = 20;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim(), "Group below mean threshold should be filtered out");
        }
    }

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        CamelRouteGroupStatus command = new CamelRouteGroupStatus(new CamelJBangMain().withPrinter(printer));

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
        writeStatusFile(TEST_PID, buildStatusWithGroup("shippingGroup", "Started", "30", "1"));

        CamelRouteGroupStatus command = new CamelRouteGroupStatus(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("shippingGroup"), "JSON should contain group name");
        }
    }

    private static JsonObject buildStatusWithGroup(String groupName, String state, String mean, String failed) {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", "10");
        stats.put("exchangesFailed", failed);
        stats.put("exchangesInflight", "0");
        stats.put("meanProcessingTime", mean);
        stats.put("maxProcessingTime", mean);
        stats.put("minProcessingTime", mean);

        JsonArray routeIds = new JsonArray();
        routeIds.add("route1");

        JsonObject group = new JsonObject();
        group.put("group", groupName);
        group.put("size", 1);
        group.put("routeIds", routeIds);
        group.put("state", state);
        group.put("uptime", "1m0s");
        group.put("statistics", stats);

        JsonArray groups = new JsonArray();
        groups.add(group);

        JsonObject context = new JsonObject();
        context.put("name", "myApp");
        context.put("phase", 5);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routeGroups", groups);
        root.put("routes", new JsonArray());
        return root;
    }
}
