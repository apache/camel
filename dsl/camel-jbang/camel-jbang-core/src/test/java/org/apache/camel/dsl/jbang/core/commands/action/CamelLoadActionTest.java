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

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CamelLoadActionTest extends ActionCommandTestSupport {

    @Test
    void testRequestsLoadAndReportsSuccess() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelLoadAction command = new CamelLoadAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        command.source = List.of("myRoute.yaml");

        int exit = callWithResponse(command, successResponse());

        assertEquals(0, exit);

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("load", action.getString("action"));

        String out = printer.getOutput();
        assertTrue(out.contains("Successfully loaded 1 source files"),
                "should report the loaded source files, was: " + out);
    }

    @Test
    void testReturnsErrorWhenNoSourceProvided() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelLoadAction command = new CamelLoadAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";

        // the command rejects the missing --source before resolving any process, so no ProcessHandle mock is needed
        int exit = command.doCall();

        assertEquals(1, exit);
        assertTrue(printer.getOutput().contains("No source files provided"),
                "should report the missing source option, was: " + printer.getOutput());
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelLoadAction command = new CamelLoadAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";
        command.source = List.of("myRoute.yaml");

        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().isEmpty(), "nothing should be rendered when no process matches");
    }

    private static JsonObject successResponse() {
        JsonObject response = new JsonObject();
        response.put("status", "success");
        return response;
    }
}
