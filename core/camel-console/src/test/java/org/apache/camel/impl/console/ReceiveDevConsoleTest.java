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
package org.apache.camel.impl.console;

import java.util.Collection;
import java.util.Map;

import org.apache.camel.console.DevConsole;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ReceiveDevConsole.
 */
public class ReceiveDevConsoleTest extends AbstractDevConsoleTest {

    @Test
    public void testReceiveConsoleBasic() {
        DevConsole console = assertConsoleExists("receive", "camel");

        String textOut = callText(console);
        assertTrue(textOut.contains("Enabled:"));
        assertTrue(textOut.contains("Total:"));

        JsonObject jsonOut = callJson(console);
        assertNotNull(jsonOut.get("enabled"));
        assertNotNull(jsonOut.get("total"));
    }

    @Test
    public void testDumpWithoutMessages() {
        DevConsole console = assertConsoleExists("receive", "camel");
        // started, as the dev console registry of an application does
        ServiceHelper.startService(console);

        // nothing received yet, or since the last dump: the camel cli connector dumps all the time
        String textOut = callText(console, Map.of("dump", "true"));
        assertEquals("[]", textOut.trim());

        JsonObject jsonOut = callJson(console, Map.of("dump", "true"));
        Collection<?> messages = jsonOut.getCollection("messages");
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }
}
