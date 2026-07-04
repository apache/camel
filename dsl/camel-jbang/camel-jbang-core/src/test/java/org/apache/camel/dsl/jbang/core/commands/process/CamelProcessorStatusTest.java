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
class CamelProcessorStatusTest extends ProcessCommandTestSupport {

    @Test
    void testDisplaysProcessorRow() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithProcessor("myRoute", "log1", "log:output", "Started", "25", "0", "0"));

        CamelProcessorStatus command = new CamelProcessorStatus(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("myApp"), "Should show integration name");
            assertTrue(output.contains("myRoute"), "Should show route ID");
            assertTrue(output.contains("25"), "Should show mean processing time");
        }
    }

    @Test
    void testFilterMeanExcludesSlowProcessors() throws Exception {
        // processor with mean=5 should be excluded when --filter-mean=10
        writeStatusFile(TEST_PID, buildStatusWithProcessor("myRoute", "log1", "log:output", "Started", "5", "0", "0"));

        CamelProcessorStatus command = new CamelProcessorStatus(new CamelJBangMain().withPrinter(printer));
        command.mean = 10;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim(), "Processor below mean threshold should be filtered out");
        }
    }

    @Test
    void testFilterMeanIncludesSlowEnoughProcessors() throws Exception {
        writeStatusFile(TEST_PID, buildStatusWithProcessor("myRoute", "log1", "log:output", "Started", "50", "0", "0"));

        CamelProcessorStatus command = new CamelProcessorStatus(new CamelJBangMain().withPrinter(printer));
        command.mean = 10;

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            assertTrue(printer.getOutput().contains("50"), "Processor above mean threshold should be visible");
        }
    }

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        CamelProcessorStatus command = new CamelProcessorStatus(new CamelJBangMain().withPrinter(printer));

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
        writeStatusFile(TEST_PID, buildStatusWithProcessor("myRoute", "log1", "log:output", "Started", "30", "2", "0"));

        CamelProcessorStatus command = new CamelProcessorStatus(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("myRoute"), "JSON should contain route ID");
        }
    }

    private static JsonObject buildStatusWithProcessor(
            String routeId, String processorId, String from,
            String state, String mean, String failed, String inflight) {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", "10");
        stats.put("exchangesFailed", failed);
        stats.put("exchangesInflight", inflight);
        stats.put("meanProcessingTime", mean);
        stats.put("maxProcessingTime", mean);
        stats.put("minProcessingTime", mean);

        JsonObject route = new JsonObject();
        route.put("routeId", routeId);
        route.put("processorId", processorId);
        route.put("from", from);
        route.put("state", state);
        route.put("statistics", stats);

        JsonArray routes = new JsonArray();
        routes.add(route);

        JsonObject context = new JsonObject();
        context.put("name", "myApp");
        context.put("phase", 5);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", routes);
        return root;
    }
}
