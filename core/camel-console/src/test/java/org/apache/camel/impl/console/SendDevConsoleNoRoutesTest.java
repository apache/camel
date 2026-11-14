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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A send without an endpoint to a Camel without routes sends nothing, and says so (it was reported as sent).
 */
public class SendDevConsoleNoRoutesTest extends AbstractDevConsoleTest {

    @Test
    public void aSendWithoutEndpointAndRoutesIsAnError() {
        DevConsole console = assertConsoleExists("send");
        Map<String, Object> options = new HashMap<>();
        options.put(SendDevConsole.BODY, "Hello");

        JsonObject json = callJson(console, options);
        assertEquals("error", json.getString("status"));
        String text = callText(console, options);
        assertTrue(text.contains("Status: error"), text);
        assertTrue(text.contains("No endpoint to send to: give the endpoint, as there are no routes"), text);
    }
}
