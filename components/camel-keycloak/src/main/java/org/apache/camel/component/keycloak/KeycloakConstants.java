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
package org.apache.camel.component.keycloak;

import org.apache.camel.spi.Metadata;

public final class KeycloakConstants {

    @Metadata(description = "The operation to perform", javaType = "org.apache.camel.component.keycloak.KeycloakOperations")
    public static final String OPERATION = "CamelKeycloakOperation";

    @Metadata(description = "The realm name", javaType = "String")
    public static final String REALM_NAME = "CamelKeycloakRealmName";

    @Metadata(description = "The user ID", javaType = "String")
    public static final String USER_ID = "CamelKeycloakUserId";

    @Metadata(description = "The username", javaType = "String")
    public static final String USERNAME = "CamelKeycloakUsername";

    @Metadata(description = "The user email", javaType = "String")
    public static final String USER_EMAIL = "CamelKeycloakUserEmail";

    @Metadata(description = "The user first name", javaType = "String")
    public static final String USER_FIRST_NAME = "CamelKeycloakUserFirstName";

    @Metadata(description = "The user last name", javaType = "String")
    public static final String USER_LAST_NAME = "CamelKeycloakUserLastName";

    @Metadata(description = "The role ID", javaType = "String")
    public static final String ROLE_ID = "CamelKeycloakRoleId";

    @Metadata(description = "The role name", javaType = "String")
    public static final String ROLE_NAME = "CamelKeycloakRoleName";

    @Metadata(description = "The role description", javaType = "String")
    public static final String ROLE_DESCRIPTION = "CamelKeycloakRoleDescription";

    @Metadata(description = "The group ID", javaType = "String")
    public static final String GROUP_ID = "CamelKeycloakGroupId";

    @Metadata(description = "The group name", javaType = "String")
    public static final String GROUP_NAME = "CamelKeycloakGroupName";

    @Metadata(description = "The client ID", javaType = "String")
    public static final String CLIENT_ID = "CamelKeycloakClientId";

    @Metadata(description = "The client UUID", javaType = "String")
    public static final String CLIENT_UUID = "CamelKeycloakClientUuid";

    @Metadata(description = "The user password", javaType = "String")
    public static final String USER_PASSWORD = "CamelKeycloakUserPassword";

    @Metadata(description = "Whether the password is temporary", javaType = "Boolean")
    public static final String PASSWORD_TEMPORARY = "CamelKeycloakPasswordTemporary";

    @Metadata(description = "Search query string", javaType = "String")
    public static final String SEARCH_QUERY = "CamelKeycloakSearchQuery";

    @Metadata(description = "Maximum number of results", javaType = "Integer")
    public static final String MAX_RESULTS = "CamelKeycloakMaxResults";

    @Metadata(description = "First result index", javaType = "Integer")
    public static final String FIRST_RESULT = "CamelKeycloakFirstResult";

    @Metadata(description = "The client scope ID", javaType = "String")
    public static final String CLIENT_SCOPE_ID = "CamelKeycloakClientScopeId";

    @Metadata(description = "The client scope name", javaType = "String")
    public static final String CLIENT_SCOPE_NAME = "CamelKeycloakClientScopeName";

    private KeycloakConstants() {
        // Utility class
    }
}
