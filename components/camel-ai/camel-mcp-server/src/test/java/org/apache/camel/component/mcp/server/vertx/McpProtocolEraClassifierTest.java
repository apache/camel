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
package org.apache.camel.component.mcp.server.vertx;

import java.util.List;
import java.util.Map;

import org.apache.camel.component.mcp.server.vertx.McpProtocolEraClassifier.Era;
import org.apache.camel.component.mcp.server.vertx.McpProtocolEraClassifier.Verdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpProtocolEraClassifierTest {

    private static Map<String, Object> request(String method, Map<String, Object> params) {
        return Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params);
    }

    private static Map<String, Object> withVersion(String version) {
        return Map.of("_meta", Map.of(McpProtocolEraClassifier.PROTOCOL_VERSION_META_KEY, version));
    }

    @Test
    void bodyVersionDecides() {
        assertThat(McpProtocolEraClassifier.classify("2026-07-28", request("tools/list", withVersion("2026-07-28"))))
                .isEqualTo(Verdict.MODERN);
        // no header: the body still decides, and the modern server then requires the header
        assertThat(McpProtocolEraClassifier.classify(null, request("tools/list", withVersion("2026-07-28"))))
                .isEqualTo(Verdict.MODERN);
        // an unknown version is modern, so the modern server names the versions it supports
        assertThat(McpProtocolEraClassifier.classify(null, request("tools/list", withVersion("2099-01-01"))))
                .isEqualTo(Verdict.MODERN);
        assertThat(McpProtocolEraClassifier.classify(null, request("tools/list", withVersion("2025-11-25"))))
                .isEqualTo(Verdict.LEGACY);
    }

    @Test
    void headerThatDisagreesWithTheBodyIsRejected() {
        Verdict verdict = McpProtocolEraClassifier.classify("2025-11-25",
                request("tools/list", withVersion("2026-07-28")));

        assertThat(verdict.rejected()).isTrue();
        assertThat(verdict.message()).contains("MCP-Protocol-Version");
    }

    @Test
    void initializeWithoutVersionInTheBodyIsLegacy() {
        Map<String, Object> initialize = request("initialize", Map.of("protocolVersion", "2025-11-25"));

        assertThat(McpProtocolEraClassifier.classify(null, initialize)).isEqualTo(Verdict.LEGACY);
        // the legacy Java SDK client sends its version header on initialize too
        assertThat(McpProtocolEraClassifier.classify("2025-11-25", initialize)).isEqualTo(Verdict.LEGACY);
        // the header never turns a legacy handshake into a modern request
        assertThat(McpProtocolEraClassifier.classify("2026-07-28", initialize)).isEqualTo(Verdict.LEGACY);
    }

    @Test
    void messageWithoutVersionInTheBodyIsDecidedByTheHeader() {
        Map<String, Object> notification = Map.of("jsonrpc", "2.0", "method", "notifications/initialized");
        Map<String, Object> legacyRequest = request("tools/list", Map.of());

        assertThat(McpProtocolEraClassifier.classify(null, notification)).isEqualTo(Verdict.LEGACY);
        assertThat(McpProtocolEraClassifier.classify("2025-06-18", notification)).isEqualTo(Verdict.LEGACY);
        assertThat(McpProtocolEraClassifier.classify("2026-07-28", notification)).isEqualTo(Verdict.MODERN);
        assertThat(McpProtocolEraClassifier.classify(null, legacyRequest)).isEqualTo(Verdict.LEGACY);
        assertThat(McpProtocolEraClassifier.classify("2025-11-25", legacyRequest)).isEqualTo(Verdict.LEGACY);
        // a modern header without the version in the body: the modern server rejects the missing _meta
        assertThat(McpProtocolEraClassifier.classify("2026-07-28", legacyRequest)).isEqualTo(Verdict.MODERN);
    }

    @Test
    void bodyThatIsNotAMessageIsLeftToTheLegacyHandling() {
        assertThat(McpProtocolEraClassifier.classify("2026-07-28", List.of(Map.of("method", "tools/list"))).era())
                .isEqualTo(Era.LEGACY);
        assertThat(McpProtocolEraClassifier.classify(null, "text").era()).isEqualTo(Era.LEGACY);
    }

    @Test
    void blankVersionIsNoVersion() {
        Map<String, Object> blank = request("tools/list", withVersion(" "));

        assertThat(McpProtocolEraClassifier.classify(" ", blank)).isEqualTo(Verdict.LEGACY);
    }
}
