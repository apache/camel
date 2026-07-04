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
class ListProducerTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoProducers() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        writeStatusFile(TEST_PID, root);

        ListProducer command = new ListProducer(new CamelJBangMain().withPrinter(printer));
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
    void testShowsProducerUriAndState() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("producers", producerContainer(producer("route1", "log:info?showAll=true")));
        writeStatusFile(TEST_PID, root);

        ListProducer command = new ListProducer(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("log:info"), "Should show producer URI");
            assertTrue(output.contains("Started"), "Should show producer state");
        }
    }

    @Test
    void testFilterAndShortUri() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("producers", producerContainer(
                producer("route1", "log:info?showAll=true"),
                producer("route2", "mock:result?retainFirst=1")));
        writeStatusFile(TEST_PID, root);

        ListProducer command = new ListProducer(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.filter = "log:";
        command.shortUri = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("log:info"), "Matching producer should be shown");
            assertFalse(output.contains("showAll"), "Short URI should remove query parameters");
            assertFalse(output.contains("mock:result"), "Non-matching producer should be hidden");
        }
    }

    @Test
    void testLimit() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("producers", producerContainer(
                producer("route1", "log:one"),
                producer("route2", "log:two")));
        writeStatusFile(TEST_PID, root);

        ListProducer command = new ListProducer(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.limit = 1;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("log:one"), "First producer should be shown");
            assertFalse(output.contains("log:two"), "Second producer should be hidden by limit");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("producers", producerContainer(producer("route1", "direct:out")));
        writeStatusFile(TEST_PID, root);

        ListProducer command = new ListProducer(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("direct:out"));
            assertTrue(output.contains("Log"), "Producer suffix should be removed from type");
        }
    }

    private static JsonObject contextObj() {
        JsonObject ctx = new JsonObject();
        ctx.put("name", "myApp");
        return ctx;
    }

    private static JsonObject producerContainer(JsonObject... producers) {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, producers);
        JsonObject container = new JsonObject();
        container.put("producers", arr);
        return container;
    }

    private static JsonObject producer(String routeId, String uri) {
        JsonObject p = new JsonObject();
        p.put("routeId", routeId);
        p.put("uri", uri);
        p.put("state", "Started");
        p.put("class", "org.apache.camel.component.log.LogProducer");
        p.put("singleton", true);
        p.put("remote", false);
        return p;
    }
}
