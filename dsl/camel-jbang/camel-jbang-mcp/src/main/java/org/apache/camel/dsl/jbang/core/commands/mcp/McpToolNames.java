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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The names of the tools of the MCP server in the answers of the tools it shares with camel ask: they name the tools of
 * camel ask (list_processes, list_examples), which the MCP server does not have, so a client called a tool that does
 * not exist (CAMEL-26932).
 */
final class McpToolNames {

    private static final Map<String, String> NAMES = Map.of(
            "list_processes", "camel_runtime_processes",
            "list_examples", "camel_catalog_examples",
            "get_example_file", "camel_catalog_example_file");

    private static final Pattern NAME = Pattern.compile("\\b(" + String.join("|", NAMES.keySet()) + ")\\b");

    // camel ask selects an integration with a tool of its own; a tool of the MCP server takes it as an argument
    private static final String SELECT = "Use select_process to connect to one.";
    private static final String SELECT_MCP = "Pass nameOrPid to the tool to pick one.";

    private McpToolNames() {
    }

    /**
     * The text with the tool names of camel ask replaced by those of the MCP server.
     */
    static String of(String text) {
        if (text == null) {
            return null;
        }
        String answer = text.replace(SELECT, SELECT_MCP);
        Matcher m = NAME.matcher(answer);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, NAMES.get(m.group(1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * The answer with the tool names of camel ask replaced by those of the MCP server.
     */
    static JsonObject of(JsonObject answer) {
        if (answer == null) {
            return null;
        }
        String json = answer.toJson();
        String renamed = of(json);
        return renamed.equals(json) ? answer : (JsonObject) Jsoner.deserialize(renamed, new JsonObject());
    }
}
