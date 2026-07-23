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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ListGroovyTest extends ProcessCommandTestSupport {

    @Test
    void testDisplaysGroovyCompilerStats() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithGroovy(3, 1, "MyRoute", "MyBean"));

        ListGroovy command = new ListGroovy(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("myApp"), "Should show integration name");
            assertTrue(output.contains("MyRoute"), "Should show compiled class names");
        }
    }

    @Test
    void testEmptyOutputWhenNoGroovySection() throws Exception {
        JsonObject status = buildContextStatus("noGroovyApp", 5);
        writeStatusFile(TEST_PID, status);

        ListGroovy command = new ListGroovy(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim(), "No groovy section means no output rows");
        }
    }

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        ListGroovy command = new ListGroovy(new CamelJBangMain().withPrinter(printer));

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
        writeStatusFile(TEST_PID, buildStatusWithGroovy(2, 0, "MyProcessor"));

        ListGroovy command = new ListGroovy(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("compile"), "JSON should contain compile field");
        }
    }

    private static JsonObject buildStatusWithGroovy(int compiled, int preloaded, String... classNames) {
        JsonArray classes = new JsonArray();
        for (String name : classNames) {
            classes.add(name);
        }

        JsonObject compiler = new JsonObject();
        compiler.put("compiledCounter", compiled);
        compiler.put("preloadedCounter", preloaded);
        compiler.put("classesSize", classNames.length);
        compiler.put("compiledTime", 1500L);
        compiler.put("lastCompilationTimestamp", 0L);
        compiler.put("classes", classes);

        JsonObject groovy = new JsonObject();
        groovy.put("compiler", compiler);

        JsonObject root = buildContextStatus("myApp", 5);
        root.put("groovy", groovy);
        return root;
    }
}
