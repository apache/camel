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
class CamelReceiveActionTest extends ActionCommandTestSupport {

    @Test
    void testStartsReceivingFromNamedIntegration() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelReceiveAction command = new CamelReceiveAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        // request the start sub-action against the running integration (no --endpoint, so no auto-dump/spawn)
        command.action = "start";
        command.loggingColor = false;

        int exit = callWithResponse(command, new JsonObject());

        assertEquals(0, exit);

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("receive", action.getString("action"));
        assertEquals("true", action.getString("enabled"));
        assertEquals("*", action.getString("endpoint"));

        String out = printer.getOutput();
        assertTrue(out.contains("Starting to receive messages from existing Camel: myApp"),
                "should report connecting to the running integration, was: " + out);
    }

    @Test
    void testRendersNothingWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelReceiveAction command = new CamelReceiveAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";
        command.action = "start";
        command.loggingColor = false;

        int exit = callWithSingleProcess(command);

        assertEquals(0, exit);
        assertTrue(printer.getOutput().isEmpty(), "nothing should be rendered when no process matches");
    }
}
