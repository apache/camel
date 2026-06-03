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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AskCliToolsTest {

    // ---- tokenizeCommand tests ----

    @Test
    void tokenizeSimpleCommand() {
        assertArrayEquals(
                new String[] { "get", "routes" },
                Ask.tokenizeCommand("get routes"));
    }

    @Test
    void tokenizeCommandWithDoubleQuotes() {
        assertArrayEquals(
                new String[] { "get", "route", "--filter=my route" },
                Ask.tokenizeCommand("get route --filter=\"my route\""));
    }

    @Test
    void tokenizeCommandWithSingleQuotes() {
        assertArrayEquals(
                new String[] { "get", "route", "--filter=my route" },
                Ask.tokenizeCommand("get route --filter='my route'"));
    }

    @Test
    void tokenizeCommandWithExtraSpaces() {
        assertArrayEquals(
                new String[] { "catalog", "component", "--filter=kafka" },
                Ask.tokenizeCommand("  catalog   component   --filter=kafka  "));
    }

    @Test
    void tokenizeEmptyCommand() {
        assertEquals(0, Ask.tokenizeCommand("").length);
        assertEquals(0, Ask.tokenizeCommand("   ").length);
    }

    @Test
    void tokenizeSingleArg() {
        assertArrayEquals(
                new String[] { "ps" },
                Ask.tokenizeCommand("ps"));
    }

    // ---- collectCommands tests ----

    @Test
    void collectCommandsNoFilter() {
        List<JsonObject> commands = buildTestCommands();
        List<JsonObject> result = new ArrayList<>();
        Ask.collectCommands(commands, result, null);
        assertEquals(3, result.size());
    }

    @Test
    void collectCommandsWithFilter() {
        List<JsonObject> commands = buildTestCommands();
        List<JsonObject> result = new ArrayList<>();
        Ask.collectCommands(commands, result, "error");
        assertEquals(1, result.size());
        assertEquals("get error", result.get(0).getString("command"));
    }

    @Test
    void collectCommandsFilterByDescription() {
        List<JsonObject> commands = buildTestCommands();
        List<JsonObject> result = new ArrayList<>();
        Ask.collectCommands(commands, result, "routing");
        assertEquals(1, result.size());
        assertEquals("get error", result.get(0).getString("command"));
    }

    @Test
    void collectCommandsFilterNoMatch() {
        List<JsonObject> commands = buildTestCommands();
        List<JsonObject> result = new ArrayList<>();
        Ask.collectCommands(commands, result, "nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void collectCommandsIncludesSubcommandMetadata() {
        List<JsonObject> commands = buildTestCommands();
        List<JsonObject> result = new ArrayList<>();
        Ask.collectCommands(commands, result, "get");
        JsonObject getCmd = result.stream()
                .filter(r -> "get".equals(r.getString("command")))
                .findFirst().orElse(null);
        assertNotNull(getCmd);
        assertTrue((Boolean) getCmd.get("hasSubcommands"));
        assertEquals(1, ((Number) getCmd.get("subcommandCount")).intValue());
    }

    // ---- findCommand tests ----

    @Test
    void findCommandTopLevel() {
        List<JsonObject> commands = buildTestCommands();
        JsonObject found = Ask.findCommand(commands, "ps");
        assertNotNull(found);
        assertEquals("ps", found.getString("fullName"));
    }

    @Test
    void findCommandNested() {
        List<JsonObject> commands = buildTestCommands();
        JsonObject found = Ask.findCommand(commands, "get error");
        assertNotNull(found);
        assertEquals("get error", found.getString("fullName"));
    }

    @Test
    void findCommandNotFound() {
        List<JsonObject> commands = buildTestCommands();
        assertNull(Ask.findCommand(commands, "nonexistent"));
    }

    // ---- test data helpers ----

    private static List<JsonObject> buildTestCommands() {
        JsonObject errorCmd = new JsonObject();
        errorCmd.put("name", "error");
        errorCmd.put("fullName", "get error");
        errorCmd.put("description", "Get captured routing errors");

        JsonObject getCmd = new JsonObject();
        getCmd.put("name", "get");
        getCmd.put("fullName", "get");
        getCmd.put("description", "Get status of Camel integrations");
        JsonArray getSubs = new JsonArray();
        getSubs.add(errorCmd);
        getCmd.put("subcommands", getSubs);

        JsonObject psCmd = new JsonObject();
        psCmd.put("name", "ps");
        psCmd.put("fullName", "ps");
        psCmd.put("description", "List running Camel integrations");

        return List.of(getCmd, psCmd);
    }
}
