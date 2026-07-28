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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Central configuration for the MCP security execution layer.
 * <p>
 * All settings are opt-in and disabled by default to preserve backward compatibility. Enable via properties or
 * environment variables (e.g., {@code CAMEL_MCP_SECURITY_ENABLED=true}).
 */
@ApplicationScoped
public class McpSecurityConfig {

    private static final Logger LOG = Logger.getLogger(McpSecurityConfig.class);

    static final int DEFAULT_MAX_ARGUMENT_LENGTH = 10_000;

    @ConfigProperty(name = "camel.mcp.security.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "camel.mcp.security.access-level", defaultValue = "admin")
    String accessLevel;

    @ConfigProperty(name = "camel.mcp.security.audit.enabled", defaultValue = "false")
    boolean auditEnabled;

    @ConfigProperty(name = "camel.mcp.security.audit.include-arguments", defaultValue = "true")
    boolean auditIncludeArguments;

    @ConfigProperty(name = "camel.mcp.security.redaction.enabled", defaultValue = "false")
    boolean redactionEnabled;

    @ConfigProperty(name = "camel.mcp.security.redaction.patterns")
    Optional<String> redactionPatterns;

    @ConfigProperty(name = "camel.mcp.security.max-argument-length", defaultValue = "10000")
    int maxArgumentLength;

    private volatile List<Pattern> compiledPatterns;

    public boolean isEnabled() {
        return enabled;
    }

    public AccessLevel getAccessLevel() {
        return AccessLevel.parse(accessLevel);
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public boolean isAuditIncludeArguments() {
        return auditIncludeArguments;
    }

    public boolean isRedactionEnabled() {
        return redactionEnabled;
    }

    public int getMaxArgumentLength() {
        return maxArgumentLength > 0 ? maxArgumentLength : DEFAULT_MAX_ARGUMENT_LENGTH;
    }

    public List<Pattern> getRedactionPatterns() {
        List<Pattern> cached = compiledPatterns;
        if (cached != null) {
            return cached;
        }
        // Built-in secret detection is handled by McpSecretRedactor via Camel's DefaultMaskingFormatter and
        // SensitiveUtils. These are only the extra, operator-supplied patterns.
        List<Pattern> patterns = new ArrayList<>();
        if (redactionPatterns.isPresent()) {
            for (String p : redactionPatterns.get().split(",")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        patterns.add(Pattern.compile(trimmed));
                    } catch (PatternSyntaxException e) {
                        LOG.warnf("Ignoring invalid redaction pattern '%s': %s", trimmed, e.getMessage());
                    }
                }
            }
        }
        compiledPatterns = Collections.unmodifiableList(patterns);
        return compiledPatterns;
    }

    /**
     * Access levels derived from MCP tool annotations.
     */
    public enum AccessLevel {
        READ_ONLY,
        READ_WRITE,
        ADMIN;

        public boolean permits(boolean readOnlyHint, boolean destructiveHint) {
            return switch (this) {
                case READ_ONLY -> readOnlyHint;
                case READ_WRITE -> !destructiveHint;
                case ADMIN -> true;
            };
        }

        static AccessLevel parse(String value) {
            if (value == null || value.isBlank()) {
                return ADMIN;
            }
            return switch (value.trim().toLowerCase().replace("_", "-")) {
                case "read-only", "readonly" -> READ_ONLY;
                case "read-write", "readwrite" -> READ_WRITE;
                case "admin" -> ADMIN;
                default -> ADMIN;
            };
        }
    }
}
