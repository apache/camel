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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A tool of the TUI's MCP server and AI panel, defined once in {@link TuiToolDefinitions}: what clients see of it (its
 * name, description and input schema), its flags, and the handler that runs it.
 */
record TuiTool(TuiToolRegistry.ToolDef def, Set<Flag> flags, Handler handler) {

    enum Flag {
        /**
         * Changes nothing: reads the screen, a tab or an integration. The AI panel lets an ACP agent call such a tool
         * without asking the user; anything else is put in front of the user first.
         */
        READ_ONLY,
        /**
         * In the core set, the tools the AI panel sends to a local model, which pays for every schema in prompt
         * processing time on each question. A tool earns its place by the questions a local model gets asked, not by
         * its size (CAMEL-24760): tui_get_diagram is the ASCII drawing of what tui_get_topology returns as JSON,
         * tui_filter a screen nicety (tui_get_table returns the whole table), tui_set_log_level a rare request the
         * prompt guards anyway.
         */
        CORE
    }

    /** Runs the tool with the arguments of the call and returns its result, mostly JSON. */
    @FunctionalInterface
    interface Handler {
        String call(Map<String, Object> args) throws Exception;
    }

    TuiTool {
        Objects.requireNonNull(def, "def");
        Objects.requireNonNull(handler, "handler");
        flags = Set.copyOf(flags);
    }

    String name() {
        return def.name();
    }

    boolean has(Flag flag) {
        return flags.contains(flag);
    }
}
