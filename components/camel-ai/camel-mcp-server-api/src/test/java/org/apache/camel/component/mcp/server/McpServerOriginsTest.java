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
package org.apache.camel.component.mcp.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerOriginsTest {

    @Test
    void notSetLeavesTheEngineDefault() {
        assertThat(McpServerOrigins.parseAllowedOrigins(null)).isNull();
        assertThat(McpServerOrigins.parseAllowedOrigins(" ")).isNull();
        // a value with no origin in it allows none
        assertThat(McpServerOrigins.parseAllowedOrigins(",")).isEmpty();
    }

    @Test
    void entriesAreTrimmedAndNormalized() {
        assertThat(McpServerOrigins.parseAllowedOrigins(
                " https://App.Example.com:443/ ,, HTTP://localhost:* , http://Tools.Example.com:8080 "))
                .containsExactly("https://app.example.com", "http://localhost:*", "http://tools.example.com:8080");
    }

    @ParameterizedTest
    @CsvSource({
            "https://app.example.com, https://app.example.com",
            "https://App.Example.COM, https://app.example.com",
            "HTTPS://app.example.com, https://app.example.com",
            "https://app.example.com:443, https://app.example.com",
            "https://app.example.com/, https://app.example.com",
            "http://app.example.com:80, http://app.example.com",
            "http://app.example.com:443, http://app.example.com:443",
            "https://app.example.com:80, https://app.example.com:80",
            "http://localhost:08080, http://localhost:8080",
            "http://localhost:*, http://localhost:*",
            "http://[::1]:*, http://[::1]:*",
            "http://[::1]:80, http://[::1]",
            "http://[FE80::1]:3000, http://[fe80::1]:3000",
            "vscode-webview://abc123, vscode-webview://abc123" })
    void allowedOriginIsNormalized(String entry, String expected) {
        assertThat(McpServerOrigins.normalizeAllowedOrigin(entry)).isEqualTo(expected);
    }

    @Test
    void wildcardForAllOriginsIsRefused() {
        assertThatThrownBy(() -> McpServerOrigins.parseAllowedOrigins("https://app.example.com, *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("MCP allowed origin * is not supported")
                .hasMessageContaining("http://localhost:*");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "app.example.com", "localhost:8080", "null", "https://app.example.com/mcp", "https://app.example.com?x=1",
            "https://user@app.example.com", "https://*.example.com", "https://app.example.com:", "https://app.example.com:x",
            "https://app.example.com:70000", "https://", "://app.example.com", "https://app example.com" })
    void entryThatCanNeverMatchIsRefused(String entry) {
        assertThatThrownBy(() -> McpServerOrigins.normalizeAllowedOrigin(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid MCP allowed origin '" + entry + "'");
    }

    @ParameterizedTest
    @CsvSource({
            "https://app.example.com, https://app.example.com",
            "https://App.Example.com:443, https://app.example.com",
            "http://LOCALHOST:6274, http://localhost:6274",
            "http://localhost:80, http://localhost",
            "null, null",
            "http://localhost:*, http://localhost:*",
            "https://app.example.com/path, https://app.example.com/path" })
    void requestOriginIsNormalizedOrKeptAsIs(String origin, String expected) {
        assertThat(McpServerOrigins.normalizeOrigin(origin)).isEqualTo(expected);
    }
}
