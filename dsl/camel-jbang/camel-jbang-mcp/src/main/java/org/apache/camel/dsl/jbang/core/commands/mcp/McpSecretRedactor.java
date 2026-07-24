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
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Secret redaction engine for MCP tool responses.
 * <p>
 * Two complementary modes are provided. {@link #redact(String)} applies regex patterns to free-text output, catching
 * unquoted {@code key=value} pairs, connection strings, AWS keys and PEM private-key blocks. {@link #redactStructured}
 * walks {@link Map}/{@link List} results (such as the {@code JsonObject} returned by the runtime tools) and blanks the
 * value of any entry whose key name looks like a secret, so JSON-quoted values that the value regex cannot match are
 * still redacted. Both are needed: the tools return a mix of plain strings and structured {@code JsonObject} trees.
 */
@ApplicationScoped
public class McpSecretRedactor {

    static final String REDACTED = "***REDACTED***";

    static final List<Pattern> DEFAULT_PATTERNS = List.of(
            // password/passwd/pwd key-value pairs
            Pattern.compile("(?i)(password|passwd|pwd|passphrase)\\s*[=:]\\s*[^\\s,;}'\"\\]]+"),
            // API keys, secret keys, access keys, OAuth client secrets
            Pattern.compile(
                    "(?i)(api[_-]?key|apikey|secret[_-]?key|access[_-]?key|client[_-]?secret)\\s*[=:]\\s*[^\\s,;}'\"\\]]+"),
            // tokens and bearer authorization
            Pattern.compile("(?i)(token|bearer|authorization)\\s*[=:]\\s*[^\\s,;}'\"\\]]+"),
            // AWS Access Key IDs (AKIA prefix + 16 alphanumeric)
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            // Connection strings with embedded userinfo credentials (user:pass@host)
            Pattern.compile("(?i)(mongodb(\\+srv)?://|amqp://|redis://|jdbc:)[^\\s\"']+@[^\\s\"']+"),
            // Secrets carried as a URL query parameter (e.g. jdbc:...?password=xxx, which has no userinfo '@')
            Pattern.compile("(?i)[?&](password|secret|token|access[_-]?key)=[^\\s&\"']+"),
            // PEM private-key blocks
            Pattern.compile("(?s)-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.*?-----END [A-Z0-9 ]*PRIVATE KEY-----"));

    /**
     * Key names (case-insensitive, ignoring '-'/'_' separators) whose value is blanked during structured redaction.
     */
    static final Pattern SECRET_KEY_NAME = Pattern.compile(
            "(?i).*(password|passwd|pwd|passphrase|secret|secretkey|apikey|accesskey|clientsecret|token|bearer|"
                                                           + "authorization|credential|credentials|privatekey|keystorepassword|truststorepassword|saslpassword|sasljaasconfig).*");

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

    /**
     * Whether a map key name denotes a secret whose value should be blanked. The name is normalised by dropping '-' and
     * '_' so that {@code client-secret}, {@code client_secret} and {@code clientSecret} all match.
     */
    static boolean isSecretKeyName(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String normalised = key.replace("-", "").replace("_", "");
        return SECRET_KEY_NAME.matcher(normalised).matches();
    }

    /**
     * Redacts a structured tool result in place. {@link Map} values (such as the {@code JsonObject} returned by the
     * runtime tools) have any secret-named entry blanked and every string value passed through {@link #redact(String)};
     * {@link List} elements are recursed. The same instance is mutated and returned, so a tool's declared return type
     * is preserved.
     *
     * @param  value the result to scrub (a {@code Map}, {@code List}, or anything else which is returned untouched)
     * @return       {@code true} if anything was changed
     */
    @SuppressWarnings("unchecked")
    public boolean redactStructured(Object value) {
        boolean changed = false;
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            for (Object key : List.copyOf(mutable.keySet())) {
                Object current = mutable.get(key);
                if (key instanceof String name && isSecretKeyName(name)) {
                    if (current != null && !REDACTED.equals(current)) {
                        mutable.put(key, REDACTED);
                        changed = true;
                    }
                } else if (current instanceof String s) {
                    String scrubbed = redact(s);
                    if (!scrubbed.equals(s)) {
                        mutable.put(key, scrubbed);
                        changed = true;
                    }
                } else if (current instanceof Map<?, ?> || current instanceof List<?>) {
                    changed |= redactStructured(current);
                }
            }
        } else if (value instanceof List<?> list) {
            List<Object> mutable = (List<Object>) list;
            for (int i = 0; i < mutable.size(); i++) {
                Object current = mutable.get(i);
                if (current instanceof String s) {
                    String scrubbed = redact(s);
                    if (!scrubbed.equals(s)) {
                        mutable.set(i, scrubbed);
                        changed = true;
                    }
                } else if (current instanceof Map<?, ?> || current instanceof List<?>) {
                    changed |= redactStructured(current);
                }
            }
        }
        return changed;
    }
}
