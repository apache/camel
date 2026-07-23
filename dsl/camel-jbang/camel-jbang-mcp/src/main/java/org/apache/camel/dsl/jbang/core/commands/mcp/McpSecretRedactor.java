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

import java.util.List;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Regex-based secret redaction engine for MCP tool responses.
 * <p>
 * Applies configurable patterns to detect and replace credentials, tokens, API keys, and connection strings in tool
 * output text.
 */
@ApplicationScoped
public class McpSecretRedactor {

    static final String REDACTED = "***REDACTED***";

    static final List<Pattern> DEFAULT_PATTERNS = List.of(
            // password/passwd/pwd key-value pairs
            Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*[^\\s,;}'\"\\]]+"),
            // API keys, secret keys, access keys
            Pattern.compile("(?i)(api[_-]?key|apikey|secret[_-]?key|access[_-]?key)\\s*[=:]\\s*[^\\s,;}'\"\\]]+"),
            // tokens and bearer authorization
            Pattern.compile("(?i)(token|bearer|authorization)\\s*[=:]\\s*[^\\s,;}'\"\\]]+"),
            // AWS Access Key IDs (AKIA prefix + 16 alphanumeric)
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            // Connection strings with embedded credentials
            Pattern.compile("(?i)(mongodb(\\+srv)?://|amqp://|redis://|jdbc:)[^\\s\"']+@[^\\s\"']+"));

    @Inject
    McpSecurityConfig config;

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<Pattern> patterns = config.getRedactionPatterns();
        String result = text;
        for (Pattern pattern : patterns) {
            result = pattern.matcher(result).replaceAll(REDACTED);
        }
        return result;
    }

    public boolean containsSecret(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        List<Pattern> patterns = config.getRedactionPatterns();
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
