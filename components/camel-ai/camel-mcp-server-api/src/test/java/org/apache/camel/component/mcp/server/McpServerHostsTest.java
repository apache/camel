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

class McpServerHostsTest {

    @Test
    void notSetTurnsTheCheckOff() {
        assertThat(McpServerHosts.parseAllowedHosts(null)).isNull();
        assertThat(McpServerHosts.parseAllowedHosts(" ")).isNull();
        // a value with no host in it is the same as not set
        assertThat(McpServerHosts.parseAllowedHosts(",")).isNull();
    }

    @Test
    void entriesAreTrimmedAndNormalized() {
        assertThat(McpServerHosts.parseAllowedHosts(" MCP.Example.com ,, LocalHost:* , [::1]:08080 "))
                .containsExactly("mcp.example.com", "localhost:*", "[::1]:8080");
    }

    @ParameterizedTest
    @CsvSource({
            "mcp.example.com, mcp.example.com",
            "MCP.Example.COM, mcp.example.com",
            "mcp.example.com:443, mcp.example.com:443",
            "localhost:08080, localhost:8080",
            "localhost:*, localhost:*",
            "127.0.0.1:*, 127.0.0.1:*",
            "[::1]:*, [::1]:*",
            "[FE80::1]:3000, [fe80::1]:3000" })
    void allowedHostIsNormalized(String entry, String expected) {
        assertThat(McpServerHosts.normalizeAllowedHost(entry)).isEqualTo(expected);
    }

    @Test
    void wildcardForAllHostsIsRefused() {
        assertThatThrownBy(() -> McpServerHosts.parseAllowedHosts("mcp.example.com, *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("MCP allowed host * is not supported")
                .hasMessageContaining("localhost:*");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://mcp.example.com", "mcp.example.com/mcp", "user@mcp.example.com", "*.example.com", "mcp.example.com:",
            "mcp.example.com:x", "mcp.example.com:70000", "mcp example.com", "::1", "[::1" })
    void entryThatCanNeverMatchIsRefused(String entry) {
        assertThatThrownBy(() -> McpServerHosts.normalizeAllowedHost(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid MCP allowed host '" + entry + "'");
    }

    @ParameterizedTest
    @CsvSource({
            "mcp.example.com, mcp.example.com",
            "MCP.Example.com:8080, mcp.example.com:8080",
            "[::1]:8080, [::1]:8080",
            "evil..example.com/x, evil..example.com/x" })
    void hostHeaderIsNormalized(String header, String expected) {
        assertThat(McpServerHosts.normalizeHost(header)).isEqualTo(expected);
    }

    @Test
    void hostHeaderWithAWildcardPortIsKeptAsIs() {
        // only an allowed host may have a wildcard port; a header with one is left as is, it is lower-cased only when valid
        assertThat(McpServerHosts.normalizeHost("LocalHost:*")).isEqualTo("LocalHost:*");
        assertThat(McpServerHosts.normalizeHost(null)).isNull();
    }
}
