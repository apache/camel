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
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpSecurityConfigTest {

    // ---- AccessLevel.permits() ----

    @Test
    void readOnlyPermitsOnlyReadOnlyTools() {
        McpSecurityConfig.AccessLevel level = McpSecurityConfig.AccessLevel.READ_ONLY;

        assertThat(level.permits(true, false)).as("read-only tool").isTrue();
        assertThat(level.permits(false, false)).as("read-write tool").isFalse();
        assertThat(level.permits(false, true)).as("destructive tool").isFalse();
    }

    @Test
    void readWritePermitsNonDestructiveTools() {
        McpSecurityConfig.AccessLevel level = McpSecurityConfig.AccessLevel.READ_WRITE;

        assertThat(level.permits(true, false)).as("read-only tool").isTrue();
        assertThat(level.permits(false, false)).as("read-write tool").isTrue();
        assertThat(level.permits(false, true)).as("destructive tool").isFalse();
    }

    @Test
    void adminPermitsAllTools() {
        McpSecurityConfig.AccessLevel level = McpSecurityConfig.AccessLevel.ADMIN;

        assertThat(level.permits(true, false)).as("read-only tool").isTrue();
        assertThat(level.permits(false, false)).as("read-write tool").isTrue();
        assertThat(level.permits(false, true)).as("destructive tool").isTrue();
    }

    // ---- AccessLevel.parse() ----

    @Test
    void parseAccessLevelVariants() {
        assertThat(McpSecurityConfig.AccessLevel.parse("read-only")).isEqualTo(McpSecurityConfig.AccessLevel.READ_ONLY);
        assertThat(McpSecurityConfig.AccessLevel.parse("readonly")).isEqualTo(McpSecurityConfig.AccessLevel.READ_ONLY);
        assertThat(McpSecurityConfig.AccessLevel.parse("READ_ONLY")).isEqualTo(McpSecurityConfig.AccessLevel.READ_ONLY);
        assertThat(McpSecurityConfig.AccessLevel.parse("read-write")).isEqualTo(McpSecurityConfig.AccessLevel.READ_WRITE);
        assertThat(McpSecurityConfig.AccessLevel.parse("readwrite")).isEqualTo(McpSecurityConfig.AccessLevel.READ_WRITE);
        assertThat(McpSecurityConfig.AccessLevel.parse("admin")).isEqualTo(McpSecurityConfig.AccessLevel.ADMIN);
        assertThat(McpSecurityConfig.AccessLevel.parse("ADMIN")).isEqualTo(McpSecurityConfig.AccessLevel.ADMIN);
    }

    @Test
    void parseUnknownDefaultsToAdmin() {
        assertThat(McpSecurityConfig.AccessLevel.parse("unknown")).isEqualTo(McpSecurityConfig.AccessLevel.ADMIN);
        assertThat(McpSecurityConfig.AccessLevel.parse("")).isEqualTo(McpSecurityConfig.AccessLevel.ADMIN);
        assertThat(McpSecurityConfig.AccessLevel.parse(null)).isEqualTo(McpSecurityConfig.AccessLevel.ADMIN);
    }

    // ---- Config defaults ----

    @Test
    void defaultConfigIsDisabled() {
        McpSecurityConfig config = createConfig(false, "admin", false, true, false, null);

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getAccessLevel()).isEqualTo(McpSecurityConfig.AccessLevel.ADMIN);
        assertThat(config.isAuditEnabled()).isFalse();
        assertThat(config.isAuditIncludeArguments()).isTrue();
        assertThat(config.isRedactionEnabled()).isFalse();
    }

    @Test
    void maxArgumentLengthFallsBackToDefault() {
        McpSecurityConfig config = createConfig(false, "admin", false, true, false, null);
        config.maxArgumentLength = 0;

        assertThat(config.getMaxArgumentLength()).isEqualTo(McpSecurityConfig.DEFAULT_MAX_ARGUMENT_LENGTH);
    }

    // ---- Redaction patterns ----

    @Test
    void defaultRedactionPatternsIncludeBuiltins() {
        McpSecurityConfig config = createConfig(true, "admin", false, true, true, null);

        List<Pattern> patterns = config.getRedactionPatterns();

        assertThat(patterns).hasSizeGreaterThanOrEqualTo(McpSecretRedactor.DEFAULT_PATTERNS.size());
    }

    @Test
    void customRedactionPatternsAppended() {
        McpSecurityConfig config = createConfig(true, "admin", false, true, true, "SECRET_\\d+,TOKEN_[A-Z]+");

        List<Pattern> patterns = config.getRedactionPatterns();

        assertThat(patterns.size()).isEqualTo(McpSecretRedactor.DEFAULT_PATTERNS.size() + 2);
    }

    // ---- Helper ----

    private static McpSecurityConfig createConfig(
            boolean enabled, String accessLevel, boolean auditEnabled,
            boolean auditIncludeArguments, boolean redactionEnabled, String redactionPatterns) {
        McpSecurityConfig config = new McpSecurityConfig();
        config.enabled = enabled;
        config.accessLevel = accessLevel;
        config.auditEnabled = auditEnabled;
        config.auditIncludeArguments = auditIncludeArguments;
        config.redactionEnabled = redactionEnabled;
        config.redactionPatterns = Optional.ofNullable(redactionPatterns);
        config.maxArgumentLength = McpSecurityConfig.DEFAULT_MAX_ARGUMENT_LENGTH;
        return config;
    }
}
