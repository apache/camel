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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/** Protocol helpers shared by the inbound CLI connector endpoint and its connection sessions. */
final class CliConnectorProtocol {

    static final int VERSION = 1;
    static final String EXECUTION_ID_PARAMETER = "executionId";
    private static final Pattern EXECUTION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private CliConnectorProtocol() {
    }

    static JsonObject parseFrame(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty WebSocket frame");
        }
        Object value;
        try {
            value = Jsoner.deserialize(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON frame", e);
        }
        if (!(value instanceof JsonObject frame)) {
            throw new IllegalArgumentException("A protocol frame must be a JSON object");
        }
        if (!Integer.valueOf(VERSION).equals(frame.getInteger("v"))) {
            throw new IllegalArgumentException("Unsupported protocol version: " + frame.get("v"));
        }
        String type = frame.getString("type");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Missing protocol frame type");
        }
        return frame;
    }

    static boolean isValidExecutionId(String value) {
        return value != null && EXECUTION_ID.matcher(value).matches();
    }

    /** Extracts the stable integration id from the query string used by the existing connector clients. */
    static String executionId(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("A valid executionId query parameter is required");
        }
        String found = null;
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String rawName = separator >= 0 ? pair.substring(0, separator) : pair;
            String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
            String name;
            String value;
            try {
                name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
                value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid WebSocket query string", e);
            }
            if (EXECUTION_ID_PARAMETER.equals(name)) {
                if (found != null) {
                    throw new IllegalArgumentException("The executionId parameter must occur only once");
                }
                found = value;
            }
        }
        if (!isValidExecutionId(found)) {
            throw new IllegalArgumentException("A valid executionId query parameter is required");
        }
        return found;
    }

    static String queryFromUri(String uri) {
        if (uri == null) {
            return null;
        }
        int queryStart = uri.indexOf('?');
        return queryStart >= 0 ? uri.substring(queryStart + 1) : null;
    }

    static JsonObject envelope(String type) {
        JsonObject frame = new JsonObject();
        frame.put("v", VERSION);
        frame.put("type", type);
        return frame;
    }

    static JsonObject actionFrame(String requestId, JsonObject action) {
        JsonObject frame = envelope("action");
        frame.put("requestId", requestId);
        frame.put("action", action);
        return frame;
    }
}
