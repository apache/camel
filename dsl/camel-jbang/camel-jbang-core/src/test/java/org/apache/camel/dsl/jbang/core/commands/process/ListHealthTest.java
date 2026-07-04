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
class ListHealthTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        ListHealth command = new ListHealth(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

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
    void testShowsUpHealthCheck() throws Exception {
        JsonObject root = buildHealthStatus(check("context", "UP", true));
        writeStatusFile(TEST_PID, root);

        ListHealth command = new ListHealth(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("UP"), "Should show UP state");
            assertTrue(output.contains("context"), "Should show check ID");
        }
    }

    @Test
    void testShowsDownHealthCheck() throws Exception {
        JsonObject root = buildHealthStatus(check("context", "DOWN", true));
        writeStatusFile(TEST_PID, root);

        ListHealth command = new ListHealth(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertTrue(printer.getOutput().contains("DOWN"), "Should show DOWN state");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject root = buildHealthStatus(check("context", "UP", true));
        writeStatusFile(TEST_PID, root);

        ListHealth command = new ListHealth(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("context"));
        }
    }

    @Test
    void testFilterReadyOnly() throws Exception {
        JsonObject root = buildHealthStatus(
                check("context", "UP", true),
                check("consumer", "DOWN", false));
        writeStatusFile(TEST_PID, root);

        ListHealth command = new ListHealth(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.ready = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("context"), "Readiness check should be shown");
            assertFalse(output.contains("consumer"), "Non-readiness check should be hidden with --ready");
        }
    }

    private static JsonObject buildHealthStatus(JsonObject... checks) {
        JsonArray checksArr = new JsonArray();
        Collections.addAll(checksArr, checks);

        JsonObject hc = new JsonObject();
        hc.put("checks", checksArr);

        JsonObject context = new JsonObject();
        context.put("name", "myApp");

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("healthChecks", hc);
        return root;
    }

    private static JsonObject check(String id, String state, boolean readiness) {
        JsonObject c = new JsonObject();
        c.put("id", id);
        c.put("group", "camel");
        c.put("state", state);
        c.put("readiness", readiness);
        c.put("liveness", true);
        return c;
    }
}
