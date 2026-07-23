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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpAccessLevelTest {

    @ParameterizedTest
    @CsvSource({
            "readonly,   READONLY",
            "READONLY,   READONLY",
            "read-only,  READONLY",
            "read_only,  READONLY",
            "readwrite,  READWRITE",
            "read-write, READWRITE",
            "read_write, READWRITE",
            "admin,      ADMIN",
            "ADMIN,      ADMIN",
            ",           ADMIN",
            "'',         ADMIN"
    })
    void fromStringShouldParseValidValues(String input, McpAccessLevel expected) {
        assertThat(McpAccessLevel.fromString(input)).isEqualTo(expected);
    }

    @Test
    void fromStringShouldRejectUnknownValue() {
        assertThatThrownBy(() -> McpAccessLevel.fromString("superuser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("superuser");
    }

    @Test
    void readonlyShouldOnlyAllowReadOnlyTools() {
        McpAccessLevel level = McpAccessLevel.READONLY;

        assertThat(level.isToolAllowed(true, false)).isTrue();
        assertThat(level.isToolAllowed(false, false)).isFalse();
        assertThat(level.isToolAllowed(false, true)).isFalse();
    }

    @Test
    void readwriteShouldAllowNonDestructiveTools() {
        McpAccessLevel level = McpAccessLevel.READWRITE;

        assertThat(level.isToolAllowed(true, false)).isTrue();
        assertThat(level.isToolAllowed(false, false)).isTrue();
        assertThat(level.isToolAllowed(false, true)).isFalse();
    }

    @Test
    void adminShouldAllowAllTools() {
        McpAccessLevel level = McpAccessLevel.ADMIN;

        assertThat(level.isToolAllowed(true, false)).isTrue();
        assertThat(level.isToolAllowed(false, false)).isTrue();
        assertThat(level.isToolAllowed(false, true)).isTrue();
    }
}
