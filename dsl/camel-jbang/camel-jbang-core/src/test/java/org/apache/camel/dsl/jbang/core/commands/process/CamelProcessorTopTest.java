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
class CamelProcessorTopTest extends ProcessCommandTestSupport {

    @Test
    void testSortsByMeanDescendingSlowerProcessorFirst() throws Exception {
        long slowPid = TEST_PID;
        long fastPid = TEST_PID + 1;
        // same context name so primary sort (name) ties and mean tiebreaker fires
        writeStatusFile(slowPid, buildStatusWithOneProcessor("myApp", "slowRoute", "200"));
        writeStatusFile(fastPid, buildStatusWithOneProcessor("myApp", "fastRoute", "10"));

        // fast process first in stream — sort must correct the order
        assertSlowBeforeFast(fastPid, slowPid);
        // slow process first in stream — sort must preserve the order
        printer = new StringPrinter();
        assertSlowBeforeFast(slowPid, fastPid);
    }

    private void assertSlowBeforeFast(long firstPid, long secondPid) throws Exception {
        CamelProcessorTop command = new CamelProcessorTop(new CamelJBangMain().withPrinter(printer));
        command.sort = "name";

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
            int slowIdx = output.indexOf("slowRoute");
            int fastIdx = output.indexOf("fastRoute");
            assertTrue(slowIdx < fastIdx, "Slower processor (higher mean) should appear first regardless of stream order");
        }
    }

    @Test
    void testEmptyOutputWhenNoProcesses() throws Exception {
        CamelProcessorTop command = new CamelProcessorTop(new CamelJBangMain().withPrinter(printer));

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle current = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(current);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.empty());

            int exit = command.doCall();

            assertEquals(0, exit);
            assertEquals("", printer.getOutput().trim());
        }
    }

    private static JsonObject buildStatusWithOneProcessor(String contextName, String routeId, String mean) {
        JsonArray routes = new JsonArray();
        routes.add(processorEntry(routeId, mean));

        JsonObject context = new JsonObject();
        context.put("name", contextName);
        context.put("phase", 5);

        JsonObject root = new JsonObject();
        root.put("context", context);
        root.put("routes", routes);
        return root;
    }

    private static JsonObject processorEntry(String routeId, String mean) {
        JsonObject stats = new JsonObject();
        stats.put("exchangesTotal", "10");
        stats.put("exchangesFailed", "0");
        stats.put("exchangesInflight", "0");
        stats.put("meanProcessingTime", mean);
        stats.put("maxProcessingTime", mean);
        stats.put("minProcessingTime", mean);

        JsonObject route = new JsonObject();
        route.put("routeId", routeId);
        route.put("from", "timer:tick");
        route.put("state", "Started");
        route.put("statistics", stats);
        return route;
    }
}
