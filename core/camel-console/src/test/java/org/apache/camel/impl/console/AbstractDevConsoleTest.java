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

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Base class for DevConsole tests providing common helper methods.
 */
public abstract class AbstractDevConsoleTest extends ContextTestSupport {

    /**
     * Resolves a DevConsole by its ID.
     */
    protected DevConsole resolveConsole(String consoleId) {
        return PluginHelper.getDevConsoleResolver(context).resolveDevConsole(consoleId);
    }

    /**
     * Verifies that a console exists and has the expected ID.
     */
    protected DevConsole assertConsoleExists(String consoleId) {
        DevConsole console = resolveConsole(consoleId);
        assertNotNull(console, "Console '" + consoleId + "' should exist");
        assertNotNull(console.getGroup(), "Console group should not be null");
        assertEquals(consoleId, console.getId(), "Console ID should match");
        return console;
    }

    /**
     * Verifies that a console exists with the expected group and ID.
     */
    protected DevConsole assertConsoleExists(String consoleId, String expectedGroup) {
        DevConsole console = resolveConsole(consoleId);
        assertNotNull(console, "Console '" + consoleId + "' should exist");
        assertEquals(expectedGroup, console.getGroup(), "Console group should match");
        assertEquals(consoleId, console.getId(), "Console ID should match");
        return console;
    }

    /**
     * Calls a console with TEXT media type and verifies output is not null.
     */
    protected String callText(DevConsole console) {
        String out = (String) console.call(DevConsole.MediaType.TEXT);
        assertNotNull(out, "TEXT output should not be null");
        return out;
    }

    /**
     * Calls a console with TEXT media type and options.
     */
    protected String callText(DevConsole console, Map<String, Object> options) {
        String out = (String) console.call(DevConsole.MediaType.TEXT, options);
        assertNotNull(out, "TEXT output should not be null");
        return out;
    }

    /**
     * Calls a console with JSON media type and verifies output is not null.
     */
    protected JsonObject callJson(DevConsole console) {
        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        assertNotNull(out, "JSON output should not be null");
        return out;
    }

    /**
     * Calls a console with JSON media type and options.
     */
    protected JsonObject callJson(DevConsole console, Map<String, Object> options) {
        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        assertNotNull(out, "JSON output should not be null");
        return out;
    }
}
