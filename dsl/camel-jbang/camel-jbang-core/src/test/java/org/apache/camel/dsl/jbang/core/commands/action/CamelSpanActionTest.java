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
class CamelSpanActionTest extends ActionCommandTestSupport {

    @Test
    void testRequestsSpansAndRendersTrace() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelSpanAction command = new CamelSpanAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        // options are normally defaulted by picocli; set them as we construct the command directly
        command.limit = 500;

        int exit = callWithResponse(command, singleSpanResponse());

        assertEquals(0, exit);

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("span", action.getString("action"));

        String out = printer.getOutput();
        assertTrue(out.contains("myRoute"), "should print the route id of the captured span, was: " + out);
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelSpanAction command = new CamelSpanAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";

        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().isEmpty(), "nothing should be rendered when no process matches");
    }

    private static JsonObject singleSpanResponse() {
        JsonObject span = new JsonObject();
        span.put("traceId", "abcdef0123456789");
        span.put("spanId", "span-1");
        span.put("parentSpanId", "");
        span.put("name", "timer://foo");
        span.put("kind", "INTERNAL");
        span.put("status", "OK");
        span.put("durationMs", 5);
        span.put("routeId", "myRoute");
        span.put("processorId", "to1");
        span.put("startEpochNanos", 1_000_000L);
        span.put("endEpochNanos", 6_000_000L);
        span.put("attributes", new JsonObject());
        JsonArray spans = new JsonArray();
        spans.add(span);

        JsonObject response = new JsonObject();
        response.put("enabled", true);
        response.put("spans", spans);
        return response;
    }
}
