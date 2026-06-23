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

import java.util.Collections;
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
class ListEventTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoEvents() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        writeStatusFile(TEST_PID, root);

        ListEvent command = new ListEvent(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

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
    void testShowsAllEventGroups() throws Exception {
        JsonObject root = buildEventStatus();
        writeStatusFile(TEST_PID, root);

        ListEvent command = new ListEvent(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("context started"), "Should show context events");
            assertTrue(output.contains("route started"), "Should show route events");
            assertTrue(output.contains("exchange done"), "Should show exchange events");
        }
    }

    @Test
    void testFilterRouteEvents() throws Exception {
        JsonObject root = buildEventStatus();
        writeStatusFile(TEST_PID, root);

        ListEvent command = new ListEvent(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.filter = "route";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("route started"), "Route events should be shown");
            assertFalse(output.contains("context started"), "Context events should be filtered out");
            assertFalse(output.contains("exchange done"), "Exchange events should be filtered out");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject root = buildEventStatus();
        writeStatusFile(TEST_PID, root);

        ListEvent command = new ListEvent(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("ContextStarted"));
        }
    }

    private static JsonObject contextObj() {
        JsonObject ctx = new JsonObject();
        ctx.put("name", "myApp");
        return ctx;
    }

    private static JsonObject buildEventStatus() {
        JsonObject events = new JsonObject();
        events.put("events", eventArray(event("ContextStarted", "context started")));
        events.put("routeEvents", eventArray(event("RouteStarted", "route started")));
        events.put("exchangeEvents", eventArray(event("ExchangeCompleted", "exchange done")));

        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("events", events);
        return root;
    }

    private static JsonArray eventArray(JsonObject... events) {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, events);
        return arr;
    }

    private static JsonObject event(String type, String message) {
        JsonObject e = new JsonObject();
        e.put("type", type);
        e.put("timestamp", System.currentTimeMillis() - 1000);
        e.put("exchangeId", "exchange-1");
        e.put("message", message);
        return e;
    }
}
