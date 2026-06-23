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
package org.apache.camel.dsl.jbang.core.commands.action;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CamelHistoryActionTest extends ActionCommandTestSupport {

    private static final long FIXED_TIMESTAMP = 1_700_000_000_000L;

    @Test
    void testRendersMessageHistoryFromHistoryFile() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        // CamelHistoryAction reads a pre-existing <pid>-history.json, not the action/output round trip
        writeMessageHistoryFile(TEST_PID, singleTraceLine());

        CamelHistoryAction command = new CamelHistoryAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        // options are normally defaulted by picocli; set them as we construct the command directly
        // depth defaults to 9 in picocli; without it depth=0 filters down to only level-1 created/completed nodes
        command.depth = 9;
        command.loggingColor = false;

        int exit = callWithSingleProcess(command);

        assertEquals(0, exit);

        String out = printer.getOutput();
        assertTrue(out.contains("Message History of last completed"),
                "should print the message history header, was: " + out);
        assertTrue(out.contains("myApp"), "should print the integration name, was: " + out);
        assertTrue(out.contains("ABCDEFGH-0001"), "should print the exchange id, was: " + out);
    }

    @Test
    void testRendersNothingWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeMessageHistoryFile(TEST_PID, singleTraceLine());

        CamelHistoryAction command = new CamelHistoryAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";
        command.loggingColor = false;

        int exit = callWithSingleProcess(command);

        assertEquals(0, exit);
        assertTrue(printer.getOutput().isEmpty(), "nothing should be rendered when no process matches");
    }

    private static JsonObject singleTraceLine() {
        JsonObject message = new JsonObject();
        message.put("exchangePattern", "InOnly");
        message.put("exchangeId", "ABCDEFGH-0001");
        message.put("body", new JsonObject());

        JsonObject trace = new JsonObject();
        trace.put("uid", 1);
        trace.put("first", true);
        trace.put("last", true);
        trace.put("routeId", "myRoute");
        trace.put("fromRouteId", "myRoute");
        trace.put("nodeId", "to1");
        trace.put("nodeShortName", "to");
        trace.put("nodeLabel", "to[mock://out]");
        trace.put("timestamp", FIXED_TIMESTAMP);
        trace.put("elapsed", 5);
        trace.put("failed", false);
        trace.put("done", true);
        trace.put("threadName", "main");
        trace.put("exchangeId", "ABCDEFGH-0001");
        trace.put("message", message);
        JsonArray traces = new JsonArray();
        traces.add(trace);

        JsonObject line = new JsonObject();
        line.put("name", "myApp");
        line.put("traces", traces);
        return line;
    }
}
