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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VertxMcpPrincipal}: deriving a runtime-neutral name and roles from a Vert.x {@link User}.
 */
class VertxMcpPrincipalTest {

    @Test
    void nameFromUsernameClaim() {
        User user = User.create(new JsonObject().put("username", "camel"));
        assertThat(VertxMcpPrincipal.name(user)).isEqualTo("camel");
    }

    @Test
    void nameFromSubClaimWhenNoUsername() {
        User user = User.create(new JsonObject().put("sub", "user-123"));
        assertThat(VertxMcpPrincipal.name(user)).isEqualTo("user-123");
    }

    @Test
    void nameIsNullForNullUserOrNoClaims() {
        assertThat(VertxMcpPrincipal.name(null)).isNull();
        assertThat(VertxMcpPrincipal.name(User.create(new JsonObject()))).isNull();
    }

    @Test
    void rolesFromTopLevelRolesClaim() {
        User user = User.create(new JsonObject().put("roles", new JsonArray().add("admin").add("user")));
        assertThat(VertxMcpPrincipal.roles(user)).containsExactly("admin", "user");
    }

    @Test
    void rolesFromKeycloakRealmAccess() {
        User user = User.create(new JsonObject()
                .put("realm_access", new JsonObject().put("roles", new JsonArray().add("kc-role"))));
        assertThat(VertxMcpPrincipal.roles(user)).containsExactly("kc-role");
    }

    @Test
    void rolesFromGroupsClaim() {
        User user = User.create(new JsonObject().put("groups", new JsonArray().add("g1").add("g2")));
        assertThat(VertxMcpPrincipal.roles(user)).containsExactly("g1", "g2");
    }

    @Test
    void topLevelRolesTakePrecedenceOverGroups() {
        User user = User.create(new JsonObject()
                .put("roles", new JsonArray().add("a"))
                .put("groups", new JsonArray().add("b")));
        assertThat(VertxMcpPrincipal.roles(user)).containsExactly("a");
    }

    @Test
    void rolesSkipBlankAndNonStringEntries() {
        User user = User.create(new JsonObject()
                .put("roles", new JsonArray().add("ok").add("").add(123).addNull()));
        assertThat(VertxMcpPrincipal.roles(user)).containsExactly("ok");
    }

    @Test
    void rolesAreNullWhenAbsentEmptyOrNoneUsable() {
        assertThat(VertxMcpPrincipal.roles(null)).isNull();
        assertThat(VertxMcpPrincipal.roles(User.create(new JsonObject()))).isNull();
        assertThat(VertxMcpPrincipal.roles(User.create(new JsonObject().put("roles", new JsonArray())))).isNull();
        assertThat(VertxMcpPrincipal.roles(User.create(new JsonObject()
                .put("roles", new JsonArray().add("").addNull())))).isNull();
    }

    @Test
    void rolesIgnoreNonArrayClaimShapesInsteadOfThrowing() {
        // a token issuer may emit these claims with a non-array shape; extraction must not throw ClassCastException
        assertThat(VertxMcpPrincipal.roles(User.create(new JsonObject().put("roles", "admin")))).isNull();
        assertThat(VertxMcpPrincipal.roles(User.create(new JsonObject().put("groups", "devs")))).isNull();
        assertThat(VertxMcpPrincipal.roles(User.create(new JsonObject().put("realm_access", "nope")))).isNull();
        assertThat(VertxMcpPrincipal.roles(User.create(new JsonObject()
                .put("realm_access", new JsonObject().put("roles", "admin"))))).isNull();
    }

    @Test
    void rolesFallThroughFromNonArrayRolesToGroups() {
        // a non-array "roles" claim is ignored, and a valid "groups" array is still used
        User user = User.create(new JsonObject()
                .put("roles", "admin")
                .put("groups", new JsonArray().add("g1").add("g2")));
        assertThat(VertxMcpPrincipal.roles(user)).containsExactly("g1", "g2");
    }
}
