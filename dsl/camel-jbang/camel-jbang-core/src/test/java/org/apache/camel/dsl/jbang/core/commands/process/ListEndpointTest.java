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
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListEndpointTest extends ProcessCommandTestSupport {

    @Test
    void testDisplaysEndpoints() throws Exception {
        JsonObject root = buildEndpointStatus("myApp",
                endpoint("timer:tick", "in", "10"),
                endpoint("log:info", "out", "10"));
        writeStatusFile(TEST_PID, root);

        ListEndpoint command = new ListEndpoint(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("timer:tick"), "Should show consumer endpoint");
            assertTrue(output.contains("log:info"), "Should show producer endpoint");
        }
    }

    @Test
    void testEmptyOutputWhenNoEndpoints() throws Exception {
        JsonObject root = buildEndpointStatus("myApp");
        writeStatusFile(TEST_PID, root);

        ListEndpoint command = new ListEndpoint(new CamelJBangMain().withPrinter(printer));
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
    void testFilterByDirection() throws Exception {
        JsonObject root = buildEndpointStatus("myApp",
                endpoint("timer:tick", "in", "5"),
                endpoint("kafka:topic", "out", "5"));
        writeStatusFile(TEST_PID, root);

        ListEndpoint command = new ListEndpoint(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.filterDirection = "in";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("timer:tick"), "Consumer endpoint should be shown for direction=in");
            assertFalse(output.contains("kafka:topic"), "Producer endpoint should be hidden for direction=in");
        }
    }

    @Test
    void testLimitKeepsTheFirstRowsAfterSorting() throws Exception {
        JsonObject root = buildEndpointStatus("myApp",
                endpoint("timer:tick", "in", "1"),
                endpoint("kafka:orders", "out", "30"),
                endpoint("log:info", "out", "5"));
        writeStatusFile(TEST_PID, root);

        ListEndpoint command = new ListEndpoint(new CamelJBangMain().withPrinter(printer));
        command.sort = "-total";
        command.limit = 1;
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            // the busiest endpoint, not the first one found
            assertTrue(output.contains("kafka:orders"), "Should keep the endpoint with the most exchanges: " + output);
            assertFalse(output.contains("timer:tick"), "Should not keep the first endpoint found: " + output);
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject root = buildEndpointStatus("myApp",
                endpoint("timer:tick", "in", "1"));
        writeStatusFile(TEST_PID, root);

        ListEndpoint command = new ListEndpoint(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.startsWith("["), "JSON output should be an array");
            assertTrue(output.contains("timer:tick"));
        }
    }

    @Test
    void testScopeTellsRemoteInternalAndLocalApart() throws Exception {
        JsonObject root = buildEndpointStatus("myApp",
                endpoint("kafka:orders", "in", "3", true),
                endpoint("log:payment", "out", "3", false),
                endpoint("exec:wc", "out", "1", false));
        writeStatusFile(TEST_PID, root);

        ListEndpoint command = new ListEndpoint(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            assertEquals(0, command.doCall());
            String output = printer.getOutput();
            assertTrue(output.contains("SCOPE"), output);
            assertFalse(output.contains("REMOTE"), output);
            assertTrue(lineOf(output, "kafka:orders").contains("remote"), output);
            assertTrue(lineOf(output, "log:payment").contains("internal"), output);
            assertTrue(lineOf(output, "exec:wc").contains("local"), output);
        }
    }

    @Test
    void testJsonOutputKeepsRemoteAndAddsInternal() throws Exception {
        JsonObject root = buildEndpointStatus("myApp",
                endpoint("log:payment", "out", "3", false),
                endpoint("exec:wc", "out", "1", false));
        writeStatusFile(TEST_PID, root);

        ListEndpoint command = new ListEndpoint(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            assertEquals(0, command.doCall());
            JsonArray rows = (JsonArray) Jsoner.deserialize(printer.getOutput().trim());
            JsonObject log = (JsonObject) rows.get(0);
            JsonObject exec = (JsonObject) rows.get(1);
            assertEquals("log:payment", log.getString("uri"));
            assertEquals(false, log.get("remote"));
            assertEquals(true, log.get("internal"));
            assertEquals(false, exec.get("internal"));
        }
    }

    private static String lineOf(String output, String uri) {
        return output.lines().filter(l -> l.contains(uri)).findFirst().orElse("");
    }

    private static JsonObject endpoint(String uri, String direction, String hits, boolean remote) {
        JsonObject ep = endpoint(uri, direction, hits);
        ep.put("remote", remote);
        return ep;
    }

    private static JsonObject buildEndpointStatus(String contextName, JsonObject... endpoints) {
        JsonArray epArr = new JsonArray();
        Collections.addAll(epArr, endpoints);

        JsonObject epContainer = new JsonObject();
        epContainer.put("endpoints", epArr);

        JsonObject context = new JsonObject();
        context.put("name", contextName);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("endpoints", epContainer);
        return root;
    }

    private static JsonObject endpoint(String uri, String direction, String hits) {
        JsonObject ep = new JsonObject();
        ep.put("uri", uri);
        ep.put("direction", direction);
        ep.put("hits", hits);
        ep.put("stub", false);
        ep.put("remote", true);
        return ep;
    }
}
