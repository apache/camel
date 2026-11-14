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
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        CliException e = assertThrows(CliException.class, () -> callWithSingleProcess(command));

        assertEquals(CliExitCode.NOT_FOUND, e.getExitCode());
        assertEquals("No running Camel integration matches: doesNotExist", e.getMessage());
        assertTrue(printer.getOutput().isEmpty(), "nothing should be printed, was: " + printer.getOutput());
    }

    @Test
    void withoutANameTheMessageGoesToTheIntegrationThatRuns() throws Exception {
        // camel run myroute.java --stub=jms, then camel cmd send --body=... (camel-jbang-tips.adoc): before, an empty
        // Camel of the CLI took the message, which sent nothing and reported success (CAMEL-26929)
        writeStatusFile(TEST_PID, "myApp");

        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
        command.body = "Something here";
        command.loggingColor = false;

        assertEquals(0, callWithResponse(command, sentResponse()));

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "the running integration should get the send");
        assertEquals("Something here", action.getString("body"));
        assertNull(action.get("endpoint"));
    }

    @Test
    void withoutANameAnEndpointOfAnIntegrationGoesToTheIntegrationThatRuns() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
        command.endpoint = "direct:orders";
        command.loggingColor = false;

        assertEquals(0, callWithResponse(command, sentResponse()));

        assertEquals("direct:orders", readActionFile(TEST_PID).getString("endpoint"));
    }

    @Test
    void withoutANameAndNoIntegrationRunningTheSendIsRefused() {
        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
        command.endpoint = "seda:orders";

        CliException e = assertThrows(CliException.class, () -> callWithSingleProcess(command));

        assertEquals(CliExitCode.NOT_FOUND, e.getExitCode());
        assertEquals("No running Camel integration to send to: seda:orders is in an integration, so start it with"
                     + " camel run first",
                e.getMessage());
    }

    @Test
    void withoutANameAndSeveralIntegrationsRunningTheSendIsRefused() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        writeStatusFile(TEST_PID + 1, "billing");

        CamelSendAction command = new CamelSendAction(new CamelJBangMain().withPrinter(printer));

        CliException e = assertThrows(CliException.class,
                () -> callWithResponse(command, sentResponse(), TEST_PID + 1));

        assertEquals(CliExitCode.NOT_FOUND, e.getExitCode());
        assertTrue(e.getMessage().startsWith("2 Camel integrations are running: "), e.getMessage());
        assertTrue(e.getMessage().endsWith(". Give the name or pid of the one to send to."), e.getMessage());
        assertNull(readActionFile(TEST_PID), "nothing is sent");
    }

    @Test
    void anEndpointOutsideTheIntegrationsIsSentFromTheCli() {
        assertTrue(CamelSendAction.isInsideAnIntegration("direct:foo"));
        assertTrue(CamelSendAction.isInsideAnIntegration("seda://foo"));
        assertTrue(CamelSendAction.isInsideAnIntegration("STUB:jms:inbox"));
        assertTrue(CamelSendAction.isInsideAnIntegration("myRoute"));
        assertTrue(CamelSendAction.isInsideAnIntegration("kafka:*"));
        assertFalse(CamelSendAction.isInsideAnIntegration("kafka:refunds"));
        assertFalse(CamelSendAction.isInsideAnIntegration("jms:queue:inbox"));
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
