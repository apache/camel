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
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.impl.console.ConsumerDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.JsonRecordSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListConsumerTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoConsumers() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        writeStatusFile(TEST_PID, root);

        ListConsumer command = new ListConsumer(new CamelJBangMain().withPrinter(printer));
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
    void testShowsConsumerUriAndState() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("consumers", consumerContainer(consumer()));
        writeStatusFile(TEST_PID, root);

        ListConsumer command = new ListConsumer(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("timer:tick"), "Should show consumer URI");
            assertTrue(output.contains("Started"), "Should show consumer state");
        }
    }

    @Test
    void testJsonOutput() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("consumers", consumerContainer(consumer()));
        writeStatusFile(TEST_PID, root);

        ListConsumer command = new ListConsumer(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("timer:tick"));
        }
    }

    @Test
    void testJsonOutputShowsType() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("consumers", consumerContainer(consumer()));
        writeStatusFile(TEST_PID, root);

        ListConsumer command = new ListConsumer(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertTrue(printer.getOutput().contains("\"type\":\"org.apache.camel.component.timer.Timer\""),
                    "Consumer suffix should be removed from type");
        }
    }

    @Test
    void testConsumerOfOlderCamelVersion() throws Exception {
        // Camel 4.22 and older say class and hostedService
        JsonObject c = consumer();
        c.put("class", c.remove("clazz"));
        c.put("hostedService", c.remove("hosted"));
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("consumers", consumerContainer(c));
        writeStatusFile(TEST_PID, root);

        ListConsumer command = new ListConsumer(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.jsonOutput = true;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertTrue(printer.getOutput().contains("\"type\":\"org.apache.camel.component.timer.Timer\""));
        }
    }

    private static JsonObject contextObj() {
        JsonObject ctx = new JsonObject();
        ctx.put("name", "myApp");
        return ctx;
    }

    private static JsonObject consumerContainer(JsonObject... consumers) {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, consumers);
        JsonObject container = new JsonObject();
        container.put("consumers", arr);
        return container;
    }

    // built from the record of the consumer console, so a renamed key breaks this test
    private static JsonObject consumer() {
        ConsumerDevConsole.ConsumerEntry entry = new ConsumerDevConsole.ConsumerEntry(
                "route1", "timer:tick", "Started", "org.apache.camel.component.timer.TimerConsumer", false, false, 0, true,
                null, null, null, null, null, null, null, null, null, null, "OFF", 0L, null, null, null, null, null, null,
                null, null, null, "tick", false, 1000L, null);
        JsonObject response = JsonRecordSupport.toJsonObject(new ConsumerDevConsole.Response(List.of(entry)));
        return (JsonObject) ((JsonArray) response.get("consumers")).get(0);
    }
}
