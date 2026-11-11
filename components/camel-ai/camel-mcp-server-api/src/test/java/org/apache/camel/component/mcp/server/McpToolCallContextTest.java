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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.OAuthTokenValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolCallContextTest {

    @Test
    void copiesOAuthScopesAndClaims() {
        List<String> scopes = new ArrayList<>(List.of("mcp:read", "mcp:write"));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "alice");
        claims.put("roles", List.of("operator"));

        OAuthTokenValidationResult validationResult = OAuthTokenValidationResult.valid(
                "alice", "https://id.example.test", List.of("mcp-server"), List.of("mcp:read"), claims, 123L);
        McpToolCallContext context = new McpToolCallContext(
                null, "alice", List.of("operator"), "alice", scopes, claims, validationResult);

        scopes.add("admin");
        claims.put("admin", true);

        assertThat(context.oauthSubject()).isEqualTo("alice");
        assertThat(context.oauthTokenValidationResult()).isSameAs(validationResult);
        assertThat(context.oauthScopes()).containsExactly("mcp:read", "mcp:write");
        assertThat(context.oauthClaims())
                .containsEntry("sub", "alice")
                .containsEntry("roles", List.of("operator"))
                .doesNotContainKey("admin");

        assertThatThrownBy(() -> context.oauthScopes().add("admin"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.oauthClaims().put("admin", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<?>) context.oauthClaims().get("roles")).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<?>) context.oauthClaims().get("roles")).add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesTheExistingConstructors() {
        Object principal = new Object();

        McpToolCallContext oneArgument = new McpToolCallContext(principal);
        assertThat(oneArgument.securityPrincipal()).isSameAs(principal);
        assertThat(oneArgument.oauthSubject()).isNull();

        McpToolCallContext threeArguments = new McpToolCallContext(
                principal, "alice", List.of("operator"));
        assertThat(threeArguments.securityPrincipal()).isSameAs(principal);
        assertThat(threeArguments.principalName()).isEqualTo("alice");
        assertThat(threeArguments.principalRoles()).containsExactly("operator");
        assertThat(threeArguments.oauthScopes()).isNull();
    }
}
