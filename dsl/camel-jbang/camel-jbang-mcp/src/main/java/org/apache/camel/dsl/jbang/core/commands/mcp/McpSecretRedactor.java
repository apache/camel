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

import org.apache.camel.support.processor.DefaultMaskingFormatter;
import org.apache.camel.util.SensitiveUtils;

/**
 * Secret redaction engine for MCP tool responses.
 * <p>
 * Both modes reuse Camel's own knowledge of what a secret is, rather than a hand-maintained word list. Free-text output
 * is masked with {@link DefaultMaskingFormatter}, which blanks the value of any {@code key=value}, XML element or JSON
 * field whose name is one of {@link SensitiveUtils#getSensitiveKeys()}. Structured results ({@link Map}/{@link List},
 * such as the {@code JsonObject} returned by the runtime tools) are walked and any entry whose key
 * {@link SensitiveUtils#containsSensitive(String) is sensitive} has its value blanked, which is what catches
 * JSON-quoted values inside an object tree. Operators may add extra regex patterns via
 * {@code camel.mcp.security.redaction.patterns}.
 */
@ApplicationScoped
public class McpSecretRedactor {

    static final String REDACTED = "***REDACTED***";

    @Inject
    McpSecurityConfig config;

    private volatile DefaultMaskingFormatter maskingFormatter;

    private DefaultMaskingFormatter maskingFormatter() {
        DefaultMaskingFormatter mf = maskingFormatter;
        if (mf == null) {
            // Masks key=value, XML and JSON using SensitiveUtils.getSensitiveKeys() as the keyword set.
            mf = new DefaultMaskingFormatter();
            mf.setMaskString(REDACTED);
            maskingFormatter = mf;
        }
        return mf;
    }

    /**
     * Masks secrets in free-text tool output using Camel's masking formatter, then applies any operator-supplied custom
     * patterns.
     */
    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = maskingFormatter().format(text);
        for (Pattern pattern : config.getRedactionPatterns()) {
            result = pattern.matcher(result).replaceAll(REDACTED);
        }
        return result;
    }

    public boolean containsSecret(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return !redact(text).equals(text);
    }

    /**
     * Redacts a structured tool result in place. In a {@link Map} (such as the {@code JsonObject} returned by the
     * runtime tools) any entry whose key {@link SensitiveUtils#containsSensitive(String) is sensitive} has its value
     * blanked, and every string value is passed through {@link #redact(String)}; {@link List} elements are recursed.
     * The same instance is mutated and returned, so a tool's declared return type is preserved.
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
                if (key instanceof String name && SensitiveUtils.containsSensitive(name)) {
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
