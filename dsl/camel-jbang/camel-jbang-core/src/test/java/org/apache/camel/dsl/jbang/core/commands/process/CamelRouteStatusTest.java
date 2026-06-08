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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class CamelRouteStatusTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));

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
    void testDisplaysSingleRoute() throws Exception {
        JsonObject status = buildRouteStatus("route1", "Started");
        writeStatusFile(TEST_PID, status);

        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("route1"), "Should show route ID");
            assertTrue(output.contains("timer:tick"), "Should show from URI");
            assertTrue(output.contains("Started"), "Should show route state");
        }
    }

    @Test
    void testDisplaysMultipleRoutes() throws Exception {
        JsonObject routeStats = buildRouteStats();
        JsonObject route1 = buildRoute("route1", "timer:tick1", routeStats);
        JsonObject route2 = buildRoute("route2", "timer:tick2", routeStats);

        JsonArray routes = new JsonArray();
        routes.add(route1);
        routes.add(route2);

        JsonObject context = new JsonObject();
        context.put("name", "multiRouteApp");
        context.put("phase", 5);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", routes);
        writeStatusFile(TEST_PID, root);

        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("route1"));
            assertTrue(output.contains("route2"));
        }
    }

    @Test
    void testSuspendedRouteShownCorrectly() throws Exception {
        JsonObject status = buildRouteStatus("suspendedRoute", "Suspended");
        writeStatusFile(TEST_PID, status);

        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertTrue(printer.getOutput().contains("Suspended"));
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject status = buildRouteStatus("route1", "Started");
        writeStatusFile(TEST_PID, status);

        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
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
            assertTrue(output.contains("route1"));
        }
    }

    @Test
    void testFilterByRouteId() throws Exception {
        JsonObject routeStats = buildRouteStats();
        JsonObject route1 = buildRoute("matchingRoute", "timer:tick1", routeStats);
        JsonObject route2 = buildRoute("otherRoute", "timer:tick2", routeStats);

        JsonArray routes = new JsonArray();
        routes.add(route1);
        routes.add(route2);

        JsonObject context = new JsonObject();
        context.put("name", "myApp");
        context.put("phase", 5);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", routes);
        writeStatusFile(TEST_PID, root);

        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.filter = new String[] { "matchingRoute" };

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("matchingRoute"), "Filtered route should appear");
            assertFalse(output.contains("otherRoute"), "Non-matching route should be hidden by filter");
        }
    }

    @Test
    void testLimitReducesRows() throws Exception {
        JsonObject routeStats = buildRouteStats();
        JsonArray routes = new JsonArray();
        for (int i = 1; i <= 5; i++) {
            routes.add(buildRoute("route" + i, "timer:tick" + i, routeStats));
        }

        JsonObject context = new JsonObject();
        context.put("name", "bigApp");
        context.put("phase", 5);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", routes);
        writeStatusFile(TEST_PID, root);

        CamelRouteStatus command = new CamelRouteStatus(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.limit = 2;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            // Only 2 routes should appear — limit is enforced during row collection
            assertTrue(output.contains("route1"));
            assertTrue(output.contains("route2"));
            assertFalse(output.contains("route5"), "route5 should be excluded by limit=2");
        }
    }

    private static JsonObject buildRouteStats() {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", "0");
        stats.put("exchangesFailed", "0");
        stats.put("exchangesInflight", "0");
        stats.put("meanProcessingTime", "-1");
        stats.put("maxProcessingTime", "0");
        stats.put("minProcessingTime", "0");
        return stats;
    }

    private static JsonObject buildRoute(String routeId, String fromUri, JsonObject stats) {
        JsonObject route = new JsonObject();
        route.put("routeId", routeId);
        route.put("from", fromUri);
        route.put("state", "Started");
        route.put("uptime", "1m");
        route.put("statistics", stats);
        return route;
    }
}
