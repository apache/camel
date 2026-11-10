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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpOAuthProtectedResourceMetadataTest {

    @Test
    void insertsWellKnownPathBetweenAuthorityAndResourcePath() {
        McpOAuthProtectedResourceMetadata metadata = McpOAuthProtectedResourceMetadata.create(
                "https://mcp.example.test/api/mcp", "/api/mcp", "https://id.example.test/issuer",
                java.util.List.of("mcp:read", "mcp:write"));

        assertThat(metadata.metadataPath()).isEqualTo("/.well-known/oauth-protected-resource/api/mcp");
        assertThat(metadata.metadataUri())
                .isEqualTo("https://mcp.example.test/.well-known/oauth-protected-resource/api/mcp");
        assertThat(metadata.asMap())
                .containsEntry("resource", "https://mcp.example.test/api/mcp")
                .containsEntry("authorization_servers", java.util.List.of("https://id.example.test/issuer"))
                .containsEntry("scopes_supported", java.util.List.of("mcp:read", "mcp:write"))
                .containsEntry("bearer_methods_supported", java.util.List.of("header"));
    }

    @Test
    void rootResourceUsesTheRootWellKnownPath() {
        McpOAuthProtectedResourceMetadata metadata = McpOAuthProtectedResourceMetadata.create(
                "https://mcp.example.test/", "/", "https://id.example.test/issuer", java.util.List.of());

        assertThat(metadata.metadataPath()).isEqualTo("/.well-known/oauth-protected-resource");
        assertThat(metadata.asMap()).doesNotContainKey("scopes_supported");
    }

    @Test
    void challengePreservesExistingBearerErrorAndAddsMetadataAndScopes() {
        McpOAuthProtectedResourceMetadata metadata = McpOAuthProtectedResourceMetadata.create(
                "https://mcp.example.test/mcp", "/mcp", "https://id.example.test/issuer",
                java.util.List.of("mcp:read", "mcp:write"));

        assertThat(metadata.addToWwwAuthenticate("Bearer error=\"invalid_token\""))
                .isEqualTo("Bearer error=\"invalid_token\", resource_metadata=\""
                           + "https://mcp.example.test/.well-known/oauth-protected-resource/mcp"
                           + "\", scope=\"mcp:read mcp:write\"");
    }

    @Test
    void rejectsInsecureOrMismatchedResourceUris() {
        assertThatThrownBy(() -> McpOAuthProtectedResourceMetadata.create(
                "http://mcp.example.test/mcp", "/mcp", "https://id.example.test/issuer", java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute HTTPS URI");

        assertThatThrownBy(() -> McpOAuthProtectedResourceMetadata.create(
                "https://mcp.example.test/other", "/mcp", "https://id.example.test/issuer", java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path must match");
    }
}
