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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CamelThreadDumpTest extends ActionCommandTestSupport {

    @Test
    void testRequestsThreadDumpAndRendersThreads() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelThreadDump command = new CamelThreadDump(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        // options are normally defaulted by picocli; set them as we construct the command directly
        command.filters = new String[] { "all" };
        command.sort = "id";
        command.depth = 1;

        int exit = callWithResponse(command, singleThreadResponse());

        assertEquals(0, exit);

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("thread-dump", action.getString("action"));

        String out = printer.getOutput();
        assertTrue(out.contains("PID: " + TEST_PID), "should print the pid header, was: " + out);
        assertTrue(out.contains("Camel-thread-1"), "should print the thread name, was: " + out);
        assertTrue(out.contains("RUNNABLE"), "should print the thread state, was: " + out);
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelThreadDump command = new CamelThreadDump(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";

        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().isEmpty(), "nothing should be rendered when no process matches");
    }

    private static JsonObject singleThreadResponse() {
        JsonObject thread = new JsonObject();
        thread.put("id", 1);
        thread.put("name", "Camel-thread-1");
        thread.put("state", "RUNNABLE");
        thread.put("waitedCount", 0);
        thread.put("waitedTime", 0);
        thread.put("blockedCount", 0);
        thread.put("blockedTime", 0);
        thread.put("stackTrace", new JsonArray());

        JsonArray threads = new JsonArray();
        threads.add(thread);

        JsonObject response = new JsonObject();
        response.put("threadCount", 1);
        response.put("peakThreadCount", 2);
        response.put("threads", threads);
        return response;
    }
}
