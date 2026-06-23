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
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CamelSendActionTest extends ActionCommandTestSupport {

    private static final long FIXED_TIMESTAMP = 1_700_000_000_000L;

    @Test
    void testSendsToNamedIntegrationAndRendersStatus() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        command.endpoint = "seda://foo";
        // route the status line to the captured printer instead of AnsiConsole
        command.loggingColor = false;

        int exit = callWithResponse(command, sentResponse());

        assertEquals(0, exit);

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("send", action.getString("action"));
        assertEquals("seda://foo", action.getString("endpoint"));

        String out = printer.getOutput();
        assertTrue(out.contains("seda://foo"), "should print the target endpoint, was: " + out);
        assertTrue(out.contains("Sent (success)"), "should print the send status, was: " + out);
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";
        command.endpoint = "seda://foo";

        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().contains("matches 0 running Camel integrations"),
                "should report no matching integration, was: " + printer.getOutput());
    }

    private static JsonObject sentResponse() {
        JsonObject response = new JsonObject();
        response.put("timestamp", FIXED_TIMESTAMP);
        response.put("endpoint", "seda://foo");
        response.put("elapsed", 5);
        response.put("exchangeId", "E1");
        return response;
    }
}
