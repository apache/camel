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
class ListPropertiesTest extends ProcessCommandTestSupport {

    @Test
    void testEmptyOutputWhenNoProperties() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("properties", propertyContainer("properties"));
        writeStatusFile(TEST_PID, root);

        ListProperties command = new ListProperties(new CamelJBangMain().withPrinter(printer));
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
    void testMasksSensitiveValuesByDefault() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("properties", propertyContainer("properties",
                property("camel.password", "secret", "secret", false, "properties:app", "initial")));
        writeStatusFile(TEST_PID, root);

        ListProperties command = new ListProperties(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            // "camel.password" is 14 chars; the KEY column minimum is 15 but may truncate with ellipsis
            // in narrow terminal environments — check the prefix that always survives truncation
            assertTrue(output.contains("camel.pass"), "Should show sensitive property key (possibly truncated)");
            assertTrue(output.contains("xxxxxx"), "Sensitive value should be masked");
            assertFalse(output.contains("secret"), "Raw sensitive value should not be printed");
        }
    }

    @Test
    void testInternalPropertiesHiddenUnlessRequested() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("properties", propertyContainer("properties",
                property("public.key", "visible", "visible", false, "properties:app", "ENV"),
                property("internal.key", "hidden", "hidden", true, "properties:app", "SYS")));
        writeStatusFile(TEST_PID, root);

        ListProperties command = new ListProperties(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("public.key"), "Public property should be shown");
            assertFalse(output.contains("internal.key"), "Internal property should be hidden by default");
        }
    }

    @Test
    void testStartupVerboseJsonOutput() throws Exception {
        JsonObject root = new JsonObject();
        root.put("context", contextObj());
        root.put("main-configuration", propertyContainer("configurations",
                property("camel.main.name", "demo", "demo", false, "sys:camel.main.name", "CLI")));
        writeStatusFile(TEST_PID, root);

        ListProperties command = new ListProperties(new CamelJBangMain().withPrinter(printer));
        command.sort = "pid";
        command.startup = true;
        command.verbose = true;
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
            assertTrue(output.contains("camel.main.name"));
            assertTrue(output.contains("Command Line"), "CLI location should be sanitized");
            assertTrue(output.contains("\"function\":\"sys\""), "Function should be derived from source prefix");
        }
    }

    private static JsonObject contextObj() {
        JsonObject ctx = new JsonObject();
        ctx.put("name", "myApp");
        return ctx;
    }

    private static JsonObject propertyContainer(String key, JsonObject... properties) {
        JsonArray arr = new JsonArray();
        Collections.addAll(arr, properties);
        JsonObject container = new JsonObject();
        container.put(key, arr);
        return container;
    }

    private static JsonObject property(
            String key, String value, String originalValue, boolean internal, String source, String location) {
        JsonObject p = new JsonObject();
        p.put("key", key);
        p.put("value", value);
        p.put("originalValue", originalValue);
        p.put("internal", internal);
        p.put("source", source);
        p.put("location", location);
        return p;
    }
}
