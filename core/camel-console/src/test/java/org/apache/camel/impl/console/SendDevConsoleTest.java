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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SendDevConsole with various options (endpoint, body, exchange pattern, etc.).
 */
public class SendDevConsoleTest extends AbstractDevConsoleTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testSendConsoleBasic() {
        DevConsole console = assertConsoleExists("send", "camel");

        Map<String, Object> options = createOptions("direct:start", "Hello World");
        String textOut = callText(console, options);
        assertTrue(textOut.contains("Endpoint:"));
        assertTrue(textOut.contains("Status:"));

        JsonObject jsonOut = callJson(console, options);
        assertNotNull(jsonOut.getString("status"));
        assertNotNull(jsonOut.getString("endpoint"));
    }

    @Test
    public void testSendConsoleInOutPattern() {
        DevConsole console = assertConsoleExists("send");

        Map<String, Object> options = createOptions("direct:start", "Hello");
        options.put(SendDevConsole.EXCHANGE_PATTERN, "InOut");

        callText(console, options);
        JsonObject jsonOut = callJson(console, options);
        assertNotNull(jsonOut.getString("status"));
    }

    @Test
    public void testSendConsoleWithHeaders() {
        DevConsole console = assertConsoleExists("send");

        Map<String, Object> options = createOptions("direct:start", "Hello");
        options.put("MyHeader", "MyValue");

        JsonObject out = callJson(console, options);
        assertNotNull(out.getString("status"));
    }

    @Test
    public void testSendConsoleNoEndpoint() {
        DevConsole console = assertConsoleExists("send");

        Map<String, Object> options = new HashMap<>();
        options.put(SendDevConsole.BODY, "Hello");

        JsonObject out = callJson(console, options);
        assertNotNull(out.getString("status"));
    }

    @Test
    public void testSendConsoleWithRouteIdPattern() {
        DevConsole console = assertConsoleExists("send");

        Map<String, Object> options = createOptions("myRoute", "Hello");
        JsonObject out = callJson(console, options);
        assertNotNull(out.getString("status"));
    }

    @Test
    public void testSendConsoleWithPatternWildcard() {
        DevConsole console = assertConsoleExists("send");

        Map<String, Object> options = createOptions("direct:*", "Hello");
        JsonObject out = callJson(console, options);
        assertNotNull(out.getString("status"));
    }

    @Test
    public void testSendConsoleToNonExistentEndpoint() {
        DevConsole console = assertConsoleExists("send");

        Map<String, Object> options = createOptions("nonexistent", "Hello");

        String textOut = callText(console, options);
        assertTrue(textOut.contains("Status: error"));

        JsonObject jsonOut = callJson(console, options);
        assertEquals("error", jsonOut.getString("status"));
    }

    @Test
    public void testSendConsoleGettersSetters() {
        SendDevConsole console = new SendDevConsole();

        console.setBodyMaxChars(1000);
        assertEquals(1000, console.getBodyMaxChars());

        console.setPollTimeout(5000);
        assertEquals(5000, console.getPollTimeout());
    }

    @Test
    public void testSendConsoleWithBodyMaxChars() {
        DevConsole console = assertConsoleExists("send");

        Map<String, Object> options = createOptions("direct:start", "Hello");
        options.put(SendDevConsole.EXCHANGE_PATTERN, "InOut");
        options.put(SendDevConsole.BODY_MAX_CHARS, "100");

        callText(console, options);
        JsonObject jsonOut = callJson(console, options);
        assertNotNull(jsonOut.getString("status"));
    }

    private Map<String, Object> createOptions(String endpoint, String body) {
        Map<String, Object> options = new HashMap<>();
        options.put(SendDevConsole.ENDPOINT, endpoint);
        options.put(SendDevConsole.BODY, body);
        return options;
    }
}
