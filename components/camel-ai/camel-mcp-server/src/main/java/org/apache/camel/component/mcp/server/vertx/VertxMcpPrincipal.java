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

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;

/**
 * Derives a runtime-neutral view — a caller name and role names — from a Vert.x {@link User}, so the MCP bridge can
 * stamp a stable caller identity ({@code CamelMcpSecurityPrincipalName} / {@code CamelMcpSecurityPrincipalRoles}) that
 * a tool route or its {@link org.apache.camel.spi.AuthorizationPolicy} can authorize on without depending on Vert.x.
 */
final class VertxMcpPrincipal {

    private VertxMcpPrincipal() {
    }

    /**
     * The caller name: the Vert.x {@link User#subject() subject} when set, else the {@code username} or {@code sub}
     * claim of the principal, else {@code null}.
     */
    static String name(User user) {
        if (user == null) {
            return null;
        }
        String subject = user.subject();
        if (isSet(subject)) {
            return subject;
        }
        JsonObject principal = user.principal();
        if (principal != null) {
            String username = principal.getString("username");
            if (isSet(username)) {
                return username;
            }
            String sub = principal.getString("sub");
            if (isSet(sub)) {
                return sub;
            }
        }
        return null;
    }

    /**
     * The caller roles, read from the principal's common role claims in order: a top-level {@code roles} array, the
     * Keycloak {@code realm_access.roles} array, or a {@code groups} array. Returns {@code null} when the principal
     * carries none, so the bridge does not stamp an empty roles property; blank and non-string entries are skipped.
     */
    static List<String> roles(User user) {
        if (user == null) {
            return null;
        }
        JsonObject principal = user.principal();
        if (principal == null) {
            return null;
        }
        JsonArray roles = principal.getJsonArray("roles");
        if (roles == null) {
            JsonObject realmAccess = principal.getJsonObject("realm_access");
            if (realmAccess != null) {
                roles = realmAccess.getJsonArray("roles");
            }
        }
        if (roles == null) {
            roles = principal.getJsonArray("groups");
        }
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        List<String> answer = new ArrayList<>(roles.size());
        for (Object role : roles) {
            if (role instanceof String s && isSet(s)) {
                answer.add(s);
            }
        }
        return answer.isEmpty() ? null : answer;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
