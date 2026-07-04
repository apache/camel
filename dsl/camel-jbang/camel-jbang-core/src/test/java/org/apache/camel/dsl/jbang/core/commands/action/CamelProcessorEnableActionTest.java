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
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class CamelProcessorEnableActionTest extends ActionCommandTestSupport {

    @Test
    void testWritesEnableProcessorActionForMatchingName() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelProcessorEnableAction command = new CamelProcessorEnableAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        command.id = "myProcessor";

        int exit = callWithSingleProcess(command);

        assertEquals(0, exit);
        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("processor", action.getString("action"));
        assertEquals("enable", action.getString("command"));
        assertEquals("myProcessor", action.getString("id"));
    }

    @Test
    void testNoActionWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelProcessorEnableAction command = new CamelProcessorEnableAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";

        int exit = callWithSingleProcess(command);

        assertEquals(0, exit);
        assertNull(readActionFile(TEST_PID), "no action file should be written when no process matches");
    }
}
