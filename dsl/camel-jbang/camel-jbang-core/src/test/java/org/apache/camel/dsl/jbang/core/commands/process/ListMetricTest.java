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
class ListMetricTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoMetrics() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        writeStatusFile(TEST_PID, root);

        ListMetric command = new ListMetric(new CamelJBangMain().withPrinter(printer));
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
    void testShowsCounterMetric() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("micrometer", micrometerObj(counterEntry(42.0)));
        writeStatusFile(TEST_PID, root);

        ListMetric command = new ListMetric(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.all = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("myCounter"), "Should show metric name");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("micrometer", micrometerObj(counterEntry(10.0)));
        writeStatusFile(TEST_PID, root);

        ListMetric command = new ListMetric(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.all = true;
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
            assertTrue(output.contains("myCounter"));
        }
    }

    private static JsonObject contextObj() {
        JsonObject ctx = new JsonObject();
        ctx.put("name", "myApp");
        return ctx;
    }

    private static JsonObject micrometerObj(JsonObject... counters) {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, counters);
        JsonObject mo = new JsonObject();
        mo.put("counters", arr);
        return mo;
    }

    private static JsonObject counterEntry(double count) {
        JsonObject c = new JsonObject();
        c.put("name", "myCounter");
        c.put("description", "");
        c.put("count", count);
        return c;
    }
}
