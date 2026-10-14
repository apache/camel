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
     * The caller name: the Vert.x {@link User#subject() subject} when non-null and non-blank, else the {@code username}
     * or {@code sub} claim of the principal when non-null and non-blank, else {@code null}.
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
     * The caller roles, read from the authenticated token's claims copied into the principal, in order: a top-level
     * {@code roles} array, the Keycloak {@code realm_access.roles} array, or a {@code groups} array. A claim that is
     * present but not a JSON array (for example a single-valued {@code "roles":"admin"}, or a non-object
     * {@code realm_access}) is ignored rather than cast, so a token in that shape cannot fail the call. Returns
     * {@code null} when the principal carries no usable roles, so the bridge does not stamp an empty roles property;
     * blank and non-string entries are skipped.
     */
    static List<String> roles(User user) {
        if (user == null) {
            return null;
        }
        JsonObject principal = user.principal();
        if (principal == null) {
            return null;
        }
        // Read defensively: a token may carry these claims with an unexpected type (a single-valued
        // "roles":"admin" string, or a non-object "realm_access"). The Vert.x JWT provider copies every
        // non-reserved claim into principal(), so casting via getJsonArray/getJsonObject would throw a
        // ClassCastException and fail the tool call before it runs. Ignore any shape that is not a JSON array.
        Object value = principal.getValue("roles");
        if (!(value instanceof JsonArray)) {
            Object realmAccess = principal.getValue("realm_access");
            value = realmAccess instanceof JsonObject jo ? jo.getValue("roles") : null;
        }
        if (!(value instanceof JsonArray)) {
            value = principal.getValue("groups");
        }
        if (!(value instanceof JsonArray roles) || roles.isEmpty()) {
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
