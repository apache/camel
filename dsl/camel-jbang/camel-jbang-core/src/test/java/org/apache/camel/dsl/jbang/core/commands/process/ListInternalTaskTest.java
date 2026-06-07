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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListInternalTaskTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoInternalTasks() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        writeStatusFile(TEST_PID, root);

        ListInternalTask command = new ListInternalTask(new CamelJBangMain().withPrinter(printer));
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
    void testShowsInternalTask() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("internal-tasks", taskContainer(task("reload", "Blocked")));
        writeStatusFile(TEST_PID, root);

        ListInternalTask command = new ListInternalTask(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("reload"), "Should show task name");
            assertTrue(output.contains("Blocked"), "Should show task status");
            assertTrue(output.contains("boom"), "Should show task failure");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("internal-tasks", taskContainer(task("sync", "Done")));
        writeStatusFile(TEST_PID, root);

        ListInternalTask command = new ListInternalTask(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("sync"));
        }
    }

    private static JsonObject contextObj() {
        JsonObject ctx = new JsonObject();
        ctx.put("name", "myApp");
        return ctx;
    }

    private static JsonObject taskContainer(JsonObject... tasks) {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, tasks);
        JsonObject container = new JsonObject();
        container.put("tasks", arr);
        return container;
    }

    private static JsonObject task(String name, String status) {
        long now = System.currentTimeMillis();
        JsonObject t = new JsonObject();
        t.put("name", name);
        t.put("status", status);
        t.put("attempts", 2L);
        t.put("delay", 1000L);
        t.put("elapsed", 50L);
        t.put("firstTime", now - 5000);
        t.put("lastTime", now - 1000);
        t.put("nextTime", now + 1000);
        t.put("error", "boom");
        return t;
    }
}
