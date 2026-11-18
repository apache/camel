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

import io.modelcontextprotocol.spec.ProtocolVersions;

/**
 * Decides whether a POST to the MCP endpoint belongs to a protocol version that opens with an {@code initialize}
 * handshake (legacy) or to protocol version 2026-07-28 and later (modern), as the MCP specification describes for a
 * server that serves both on one endpoint.
 * <p>
 * The body decides: a request whose {@code params._meta} names a protocol version belongs to the era of that version,
 * and an {@code initialize} request without one is legacy. The {@code MCP-Protocol-Version} header never changes that
 * decision; when it disagrees with the version in the body the request is rejected. Only a message that carries no
 * version in its body, a notification or a request without {@code _meta}, is decided by the header: a version that is
 * not a legacy version is modern, so an unknown version reaches the modern server, which answers with the versions it
 * supports.
 */
final class McpProtocolEraClassifier {

    /**
     * The key of the protocol version in the {@code _meta} of a request of protocol version 2026-07-28.
     */
    static final String PROTOCOL_VERSION_META_KEY = "io.modelcontextprotocol/protocolVersion";

    /**
     * The protocol versions that open with an {@code initialize} handshake, newest first.
     */
    static final List<String> LEGACY_VERSIONS = List.of(
            ProtocolVersions.MCP_2025_11_25, ProtocolVersions.MCP_2025_06_18,
            ProtocolVersions.MCP_2025_03_26, ProtocolVersions.MCP_2024_11_05);

    /**
     * The JSON-RPC error of a request whose headers disagree with its body (MCP 2026-07-28).
     */
    static final int HEADER_MISMATCH = -32020;

    /**
     * The JSON-RPC error of a request of a protocol version the server does not serve (MCP 2026-07-28).
     */
    static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    private McpProtocolEraClassifier() {
    }

    enum Era {
        LEGACY,
        MODERN
    }

    /**
     * The era of a request, or the reason it is rejected before it reaches a server.
     *
     * @param era     the era, or {@code null} when rejected
     * @param message the rejection message, or {@code null}
     */
    record Verdict(Era era, String message) {

        static final Verdict LEGACY = new Verdict(Era.LEGACY, null);
        static final Verdict MODERN = new Verdict(Era.MODERN, null);

        static Verdict headerMismatch(String message) {
            return new Verdict(null, message);
        }

        boolean rejected() {
            return era == null;
        }
    }

    /**
     * Classifies a POST body that has been parsed into JSON values.
     *
     * @param versionHeader the value of the {@code MCP-Protocol-Version} header, or {@code null}
     * @param body          the parsed body: a {@link Map} for a JSON object, anything else for other JSON
     */
    static Verdict classify(String versionHeader, Object body) {
        if (!(body instanceof Map<?, ?> message)) {
            // a batch or a value that is not a JSON-RPC message: the legacy handling answers it as it always did
            return Verdict.LEGACY;
        }
        String header = versionHeader != null && !versionHeader.isBlank() ? versionHeader.trim() : null;
        String claim = metaVersion(message.get("params"));
        if (claim != null) {
            if (header != null && !header.equals(claim)) {
                return Verdict.headerMismatch("MCP-Protocol-Version header does not match _meta protocol version");
            }
            return isLegacy(claim) ? Verdict.LEGACY : Verdict.MODERN;
        }
        if ("initialize".equals(message.get("method"))) {
            return Verdict.LEGACY;
        }
        return header != null && !isLegacy(header) ? Verdict.MODERN : Verdict.LEGACY;
    }

    static boolean isLegacy(String version) {
        return LEGACY_VERSIONS.contains(version);
    }

    private static String metaVersion(Object params) {
        if (params instanceof Map<?, ?> paramsMap && paramsMap.get("_meta") instanceof Map<?, ?> meta
                && meta.get(PROTOCOL_VERSION_META_KEY) instanceof String version && !version.isBlank()) {
            return version;
        }
        return null;
    }
}
