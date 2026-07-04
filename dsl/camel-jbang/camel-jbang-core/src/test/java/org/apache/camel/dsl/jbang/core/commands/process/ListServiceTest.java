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
class ListServiceTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoServices() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        writeStatusFile(TEST_PID, root);

        ListService command = new ListService(new CamelJBangMain().withPrinter(printer));
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
    void testShowsServiceDetails() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("services", serviceContainer(service("platform-http", "in", "http", "http://0.0.0.0:8080/hello")));
        writeStatusFile(TEST_PID, root);

        ListService command = new ListService(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("platform-http"), "Should show service component");
            assertTrue(output.contains("http://0.0.0.0:8080/hello"), "Should show service URL");
        }
    }

    @Test
    void testMetadataAndShortUri() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("services", serviceContainer(service("rest", "in", "http", "http://localhost:8080/orders")));
        writeStatusFile(TEST_PID, root);

        ListService command = new ListService(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.metadata = true;
        command.shortUri = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("method=GET"), "Metadata should be shown when requested");
            assertTrue(output.contains("direct:orders"), "Endpoint URI should be shown");
            assertFalse(output.contains("bridgeEndpoint"), "Short URI should remove endpoint query parameters");
        }
    }

    @Test
    void testJsonOutputUsesEmptyRouteWhenMissing() throws Exception {
        JsonObject entry = service("servlet", "in", "http", "http://localhost:8080/ping");
        entry.remove("routeId");

        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("services", serviceContainer(entry));
        writeStatusFile(TEST_PID, root);

        ListService command = new ListService(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("\"routeId\":\"\""), "Missing route id should be serialized as an empty string");
            assertTrue(output.contains("servlet"));
        }
    }

    private static JsonObject contextObj() {
        JsonObject ctx = new JsonObject();
        ctx.put("name", "myApp");
        return ctx;
    }

    private static JsonObject serviceContainer(JsonObject... services) {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, services);
        JsonObject container = new JsonObject();
        container.put("services", arr);
        return container;
    }

    private static JsonObject service(String component, String direction, String protocol, String serviceUrl) {
        JsonObject metadata = new JsonObject();
        metadata.put("method", "GET");
        metadata.put("path", "/orders");

        JsonObject s = new JsonObject();
        s.put("component", component);
        s.put("direction", direction);
        s.put("hosted", true);
        s.put("protocol", protocol);
        s.put("serviceUrl", serviceUrl);
        s.put("endpointUri", "direct:orders?bridgeEndpoint=true");
        s.put("hits", 3L);
        s.put("routeId", "route1");
        s.put("metadata", metadata);
        return s;
    }
}
