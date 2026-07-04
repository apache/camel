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
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class CamelContextTopTest extends ProcessCommandTestSupport {

    @Test
    void testDisplaysMemoryAndThreadFields() throws Exception {
        JsonObject status = buildContextStatus("topApp", 5);
        addMemoryAndThreads(status, 256_000_000L, 8, 10);
        writeStatusFile(TEST_PID, status);

        CamelContextTop command = new CamelContextTop(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("topApp"), "Should display context name");
            assertTrue(output.contains("HEAP"), "Should display HEAP column header");
            assertTrue(output.contains("THREADS"), "Should display THREADS column header");
        }
    }

    @Test
    void testDisplaysThroughputAndLoad() throws Exception {
        JsonObject status = buildContextStatus("loadApp", 5);
        JsonObject ctx = (JsonObject) status.get("context");
        JsonObject stats = (JsonObject) ctx.get("statistics");
        stats.put("exchangesThroughput", "12.5");
        stats.put("load01", "0.10");
        stats.put("load05", "0.20");
        stats.put("load15", "0.05");
        writeStatusFile(TEST_PID, status);

        CamelContextTop command = new CamelContextTop(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            assertTrue(output.contains("loadApp"), "Should show context name");
            // non-zero load values should appear as load1/load5/load15
            assertTrue(output.contains("0.10"), "Should show load01 value");
        }
    }

    @Test
    void testSortByMemShowsHigherHeapFirst() throws Exception {
        long highPid = TEST_PID;
        long lowPid = TEST_PID + 1;

        JsonObject highMem = buildContextStatus("highMem", 5);
        addMemoryAndThreads(highMem, 500_000_000L, 8, 10);
        writeStatusFile(highPid, highMem);

        JsonObject lowMem = buildContextStatus("lowMem", 5);
        addMemoryAndThreads(lowMem, 100_000_000L, 4, 5);
        writeStatusFile(lowPid, lowMem);

        // low process first — sort must promote high to top
        assertHighBeforeLow(lowPid, highPid);
        // high process first — sort must preserve the order
        printer = new StringPrinter();
        assertHighBeforeLow(highPid, lowPid);
    }

    private void assertHighBeforeLow(long firstPid, long secondPid) throws Exception {
        CamelContextTop command = new CamelContextTop(new CamelJBangMain().withPrinter(printer));
        command.sort = "mem";

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph1 = mockProcessHandle(firstPid);
            ProcessHandle ph2 = mockProcessHandle(secondPid);
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            // fresh stream per invocation so allProcesses() can be called multiple times
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph1, ph2));

            int exit = command.doCall();

            assertEquals(0, exit);
            String output = printer.getOutput();
            int highIdx = output.indexOf("highMem");
            int lowIdx = output.indexOf("lowMem");
            assertTrue(highIdx < lowIdx, "Higher heap should appear first regardless of stream order");
        }
    }

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        CamelContextTop command = new CamelContextTop(new CamelJBangMain().withPrinter(printer));

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
        JsonObject status = buildContextStatus("jsonTopApp", 5);
        addMemoryAndThreads(status, 128_000_000L, 4, 4);
        writeStatusFile(TEST_PID, status);

        CamelContextTop command = new CamelContextTop(new CamelJBangMain().withPrinter(printer));
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
            assertTrue(output.contains("jsonTopApp"), "JSON should contain context name");
            assertTrue(output.contains("heap"), "JSON should contain heap field");
        }
    }

    private static void addMemoryAndThreads(JsonObject root, long heapUsed, int threads, int peakThreads) {
        JsonObject mem = new JsonObject();
        mem.put("heapMemoryUsed", heapUsed);
        mem.put("heapMemoryCommitted", heapUsed + 50_000_000L);
        mem.put("heapMemoryMax", 1_000_000_000L);
        mem.put("nonHeapMemoryUsed", 64_000_000L);
        mem.put("nonHeapMemoryCommitted", 70_000_000L);
        root.put("memory", mem);

        JsonObject th = new JsonObject();
        th.put("threadCount", threads);
        th.put("peakThreadCount", peakThreads);
        root.put("threads", th);
    }
}
