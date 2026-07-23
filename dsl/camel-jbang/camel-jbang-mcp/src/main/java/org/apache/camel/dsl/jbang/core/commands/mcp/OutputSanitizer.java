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
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.util.json.JsonObject;

/**
 * Utility to redact sensitive data from MCP tool output before returning to clients.
 * <p>
 * Detects common credential patterns in JSON key names and string values, replacing them with a redaction marker.
 * Complements {@link PomSanitizer} (which handles input); this class handles output.
 */
final class OutputSanitizer {

    static final String REDACTED = "***REDACTED***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "secret", "token", "apikey", "api_key",
            "accesskey", "access_key", "secretkey", "secret_key",
            "privatekey", "private_key", "passphrase", "credentials",
            "connectionstring", "connection_string", "authorization");

    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(Bearer\\s+)[A-Za-z0-9_.\\-/+=]{10,}", Pattern.CASE_INSENSITIVE);

    private static final Pattern AWS_KEY = Pattern.compile(
            "(?:AKIA|ASIA)[A-Z0-9]{16}");

    private static final Pattern GENERIC_TOKEN = Pattern.compile(
            "(?i)(api[_-]?key|token|secret|password)\\s*[=:]\\s*[\"']?([^\"'\\s,}{]+)");

    private OutputSanitizer() {
    }

    /**
     * Redact sensitive values in a {@link JsonObject}, modifying it in place.
     *
     * @return the same JsonObject with sensitive values replaced
     */
    static JsonObject redact(JsonObject json) {
        if (json == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key) && value instanceof String) {
                entry.setValue(REDACTED);
            } else if (value instanceof JsonObject nested) {
                redact(nested);
            } else if (value instanceof String str) {
                entry.setValue(redactString(str));
            }
        }
        return json;
    }

    /**
     * Redact common credential patterns from a string value.
     */
    static String redactString(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = BEARER_TOKEN.matcher(value).replaceAll("$1" + REDACTED);
        result = AWS_KEY.matcher(result).replaceAll(REDACTED);
        result = GENERIC_TOKEN.matcher(result).replaceAll("$1=" + REDACTED);
        return result;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase().replace("-", "").replace(".", "");
        return SENSITIVE_KEYS.contains(normalized);
    }
}
