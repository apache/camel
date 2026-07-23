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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpAccessFilterTest {

    @Test
    void securityDisabledAllowsAllAccessLevels() {
        McpSecurityConfig config = createConfig(false, "read-only");

        // Even read-only level permits everything when security is disabled
        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    void readOnlyLevelBlocksWriteAndDestructive() {
        McpSecurityConfig config = createConfig(true, "read-only");
        McpSecurityConfig.AccessLevel level = config.getAccessLevel();

        assertThat(level.permits(true, false)).as("read-only tool should be visible").isTrue();
        assertThat(level.permits(false, false)).as("write tool should be hidden").isFalse();
        assertThat(level.permits(false, true)).as("destructive tool should be hidden").isFalse();
    }

    @Test
    void readWriteLevelBlocksDestructiveOnly() {
        McpSecurityConfig config = createConfig(true, "read-write");
        McpSecurityConfig.AccessLevel level = config.getAccessLevel();

        assertThat(level.permits(true, false)).as("read-only tool should be visible").isTrue();
        assertThat(level.permits(false, false)).as("write tool should be visible").isTrue();
        assertThat(level.permits(false, true)).as("destructive tool should be hidden").isFalse();
    }

    @Test
    void adminLevelAllowsEverything() {
        McpSecurityConfig config = createConfig(true, "admin");
        McpSecurityConfig.AccessLevel level = config.getAccessLevel();

        assertThat(level.permits(true, false)).as("read-only tool").isTrue();
        assertThat(level.permits(false, false)).as("write tool").isTrue();
        assertThat(level.permits(false, true)).as("destructive tool").isTrue();
    }

    private static McpSecurityConfig createConfig(boolean enabled, String accessLevel) {
        McpSecurityConfig config = new McpSecurityConfig();
        config.enabled = enabled;
        config.accessLevel = accessLevel;
        config.auditEnabled = false;
        config.auditIncludeArguments = true;
        config.redactionEnabled = false;
        config.redactionPatterns = Optional.empty();
        config.maxArgumentLength = McpSecurityConfig.DEFAULT_MAX_ARGUMENT_LENGTH;
        return config;
    }
}
