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
import java.util.Collections;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListErrorTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoErrors() throws Exception {
        JsonObject status = buildContextStatus("myApp", 5);
        writeStatusFile(TEST_PID, status);
        // no error file written

        ListError command = new ListError(new CamelJBangMain().withPrinter(printer));
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
    void testShowsErrorRouteId() throws Exception {
        JsonObject status = buildContextStatus("myApp", 5);
        writeStatusFile(TEST_PID, status);
        writeErrorFile(errorEntry());

        ListError command = new ListError(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("myRoute"), "Should show routeId");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject status = buildContextStatus("myApp", 5);
        writeStatusFile(TEST_PID, status);
        writeErrorFile(errorEntry());

        ListError command = new ListError(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("myRoute"));
        }
    }

    private static void writeErrorFile(JsonObject... errors) throws Exception {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, errors);
        JsonObject root = new JsonObject();
        root.put("errors", arr);
        Path f = CommandLineHelper.getCamelDir().resolve(ProcessCommandTestSupport.TEST_PID + "-error.json");
        Files.writeString(f, root.toJson());
    }

    private static JsonObject errorEntry() {
        JsonObject ex = new JsonObject();
        ex.put("type", "java.lang.RuntimeException");
        ex.put("message", "Test error");

        JsonObject e = new JsonObject();
        e.put("routeId", "myRoute");
        e.put("nodeId", "myNode");
        e.put("exchangeId", "abc-123");
        e.put("handled", false);
        e.put("timestamp", System.currentTimeMillis());
        e.put("exception", ex);
        return e;
    }
}
