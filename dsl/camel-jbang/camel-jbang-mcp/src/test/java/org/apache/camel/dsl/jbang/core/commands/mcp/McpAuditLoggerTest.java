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

import static org.assertj.core.api.Assertions.assertThat;

class McpAuditLoggerTest {

    @Test
    void escapeHandlesSpecialCharacters() {
        assertThat(McpAuditLogger.escape("line1\nline2")).isEqualTo("line1\\nline2");
        assertThat(McpAuditLogger.escape("tab\there")).isEqualTo("tab\\there");
        assertThat(McpAuditLogger.escape("quote\"here")).isEqualTo("quote\\\"here");
        assertThat(McpAuditLogger.escape("back\\slash")).isEqualTo("back\\\\slash");
        assertThat(McpAuditLogger.escape("return\rhere")).isEqualTo("return\\rhere");
    }

    @Test
    void escapeHandlesNull() {
        assertThat(McpAuditLogger.escape(null)).isEmpty();
    }

    @Test
    void escapePreservesNormalText() {
        assertThat(McpAuditLogger.escape("normal text")).isEqualTo("normal text");
    }

    @Test
    void loggerCanBeInstantiated() {
        McpAuditLogger logger = new McpAuditLogger();
        // Verify the logger doesn't throw on invocation — actual log output
        // goes to JBoss LogManager which is not captured in unit tests
        logger.logToolCall("test_tool", "conn-1", "claude", "ADMIN", "{\"arg\":\"val\"}");
        logger.logToolResult("test_tool", "conn-1", false, false, 42);
        logger.logAccessDenied("test_tool", "conn-1", "claude", "admin", "read-only");
    }
}
