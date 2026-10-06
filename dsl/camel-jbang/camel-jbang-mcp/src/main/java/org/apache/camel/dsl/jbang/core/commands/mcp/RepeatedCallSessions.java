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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.McpConnection;
import org.apache.camel.dsl.jbang.core.commands.ai.RepeatedToolCalls;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.json.JsonObject;

/**
 * The {@link RepeatedToolCalls} of each MCP connection, so a client that asks a deterministic tool the same question
 * over and over gets a short note from the third time instead of the same full answer (CAMEL-25075).
 */
@ApplicationScoped
public class RepeatedCallSessions {

    /** Connections kept; the least recently used is forgotten beyond this. */
    private static final int MAX_SESSIONS = 64;

    private final Map<String, RepeatedToolCalls> sessions = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RepeatedToolCalls> eldest) {
            return size() > MAX_SESSIONS;
        }
    };

    /**
     * The short note to answer with instead of running the tool, or null to run it: the tool is not deterministic, the
     * call is one of the first identical ones, or there is no connection to tell the sessions apart.
     */
    JsonObject repeatOf(McpConnection connection, String tool, Map<String, String> args) {
        if (connection == null || connection.id() == null) {
            return null;
        }
        RepeatedToolCalls calls;
        synchronized (sessions) {
            calls = sessions.computeIfAbsent(connection.id(), id -> new RepeatedToolCalls());
        }
        return calls.repeatOf(ToolRegistry.findTool(tool), args);
    }
}
