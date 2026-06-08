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
class ListPlatformHttpTest extends ProcessCommandTestSupport {

    @Test
    void testDisplaysEndpoint() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithHttp("/api/orders", "GET", "/q/health", "GET"));

        ListPlatformHttp command = new ListPlatformHttp(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("/api/orders"), "Should show user endpoint path");
            assertTrue(output.contains("GET"), "Should show HTTP verb");
        }
    }

    @Test
    void testManagementEndpointsHiddenByDefault() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithHttp("/api/orders", "GET", "/q/health", "GET"));

        ListPlatformHttp command = new ListPlatformHttp(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertFalse(printer.getOutput().contains("/q/health"),
                    "Management endpoint should be hidden without --all");
        }
    }

    @Test
    void testAllFlagIncludesManagementEndpoints() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithHttp("/api/orders", "POST", "/q/health", "GET"));

        ListPlatformHttp command = new ListPlatformHttp(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.all = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("/api/orders"), "Should show user endpoint");
            assertTrue(output.contains("/q/health"), "Should show management endpoint when --all");
        }
    }

    @Test
    void testEmptyOutputWhenNoPlatformHttpSection() throws Exception {
        JsonObject status = buildContextStatus("noHttpApp", 5);
        writeStatusFile(TEST_PID, status);

        ListPlatformHttp command = new ListPlatformHttp(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim(), "No platform-http section means no output rows");
        }
    }

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        ListPlatformHttp command = new ListPlatformHttp(new CamelJBangMain().withPrinter(printer));

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
        writeStatusFile(TEST_PID, buildStatusWithHttp("/api/ping", "GET", "/q/health", "GET"));

        ListPlatformHttp command = new ListPlatformHttp(new CamelJBangMain().withPrinter(printer));
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
            // Jsoner escapes forward slashes, so assert on the slug without them
            assertTrue(output.contains("api") && output.contains("ping"), "JSON should contain endpoint path segments");
            assertTrue(output.contains("method"), "JSON should contain method field");
        }
    }

    private static JsonObject buildStatusWithHttp(
            String userPath, String userVerb, String mgmtPath, String mgmtVerb) {
        JsonObject userEndpoint = new JsonObject();
        userEndpoint.put("url", "http://localhost:8080" + userPath);
        userEndpoint.put("path", userPath);
        userEndpoint.put("verbs", userVerb);

        JsonArray endpoints = new JsonArray();
        endpoints.add(userEndpoint);

        JsonObject mgmtEndpoint = new JsonObject();
        mgmtEndpoint.put("url", "http://localhost:8080" + mgmtPath);
        mgmtEndpoint.put("path", mgmtPath);
        mgmtEndpoint.put("verbs", mgmtVerb);

        JsonArray mgmtEndpoints = new JsonArray();
        mgmtEndpoints.add(mgmtEndpoint);

        JsonObject platformHttp = new JsonObject();
        platformHttp.put("server", "netty");
        platformHttp.put("endpoints", endpoints);
        platformHttp.put("managementEndpoints", mgmtEndpoints);

        JsonObject root = buildContextStatus("myApp", 5);
        root.put("platform-http", platformHttp);
        return root;
    }
}
