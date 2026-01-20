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

    @Metadata(description = "The event type (event or admin-event)", javaType = "String")
    public static final String EVENT_TYPE = "CamelKeycloakEventType";

    @Metadata(description = "The event ID or timestamp", javaType = "Long")
    public static final String EVENT_ID = "CamelKeycloakEventId";

    // Identity Provider constants
    @Metadata(description = "The identity provider alias", javaType = "String")
    public static final String IDP_ALIAS = "CamelKeycloakIdpAlias";

    @Metadata(description = "The identity provider ID", javaType = "String")
    public static final String IDP_ID = "CamelKeycloakIdpId";

    // Authorization Services constants
    @Metadata(description = "The resource ID", javaType = "String")
    public static final String RESOURCE_ID = "CamelKeycloakResourceId";

    @Metadata(description = "The resource name", javaType = "String")
    public static final String RESOURCE_NAME = "CamelKeycloakResourceName";

    @Metadata(description = "The resource type", javaType = "String")
    public static final String RESOURCE_TYPE = "CamelKeycloakResourceType";

    @Metadata(description = "The resource URI", javaType = "String")
    public static final String RESOURCE_URI = "CamelKeycloakResourceUri";

    @Metadata(description = "The policy ID", javaType = "String")
    public static final String POLICY_ID = "CamelKeycloakPolicyId";

    @Metadata(description = "The policy name", javaType = "String")
    public static final String POLICY_NAME = "CamelKeycloakPolicyName";

    @Metadata(description = "The policy type", javaType = "String")
    public static final String POLICY_TYPE = "CamelKeycloakPolicyType";

    @Metadata(description = "The permission ID", javaType = "String")
    public static final String PERMISSION_ID = "CamelKeycloakPermissionId";

    @Metadata(description = "The permission name", javaType = "String")
    public static final String PERMISSION_NAME = "CamelKeycloakPermissionName";

    @Metadata(description = "The scope name", javaType = "String")
    public static final String SCOPE_NAME = "CamelKeycloakScopeName";

    // User Attribute constants
    @Metadata(description = "The user attribute name", javaType = "String")
    public static final String ATTRIBUTE_NAME = "CamelKeycloakAttributeName";

    @Metadata(description = "The user attribute value", javaType = "String")
    public static final String ATTRIBUTE_VALUE = "CamelKeycloakAttributeValue";

    // User Credential constants
    @Metadata(description = "The credential ID", javaType = "String")
    public static final String CREDENTIAL_ID = "CamelKeycloakCredentialId";

    @Metadata(description = "The credential type", javaType = "String")
    public static final String CREDENTIAL_TYPE = "CamelKeycloakCredentialType";

    // User Action constants
    @Metadata(description = "The required action type", javaType = "String")
    public static final String REQUIRED_ACTION = "CamelKeycloakRequiredAction";

    @Metadata(description = "The list of actions to execute", javaType = "java.util.List<String>")
    public static final String ACTIONS = "CamelKeycloakActions";

    @Metadata(description = "The redirect URI", javaType = "String")
    public static final String REDIRECT_URI = "CamelKeycloakRedirectUri";

    @Metadata(description = "The lifespan in seconds", javaType = "Integer")
    public static final String LIFESPAN = "CamelKeycloakLifespan";

    // Bulk operations constants
    @Metadata(description = "The list of users for bulk operations",
              javaType = "java.util.List<org.keycloak.representations.idm.UserRepresentation>")
    public static final String USERS = "CamelKeycloakUsers";

    @Metadata(description = "The list of user IDs for bulk operations", javaType = "java.util.List<String>")
    public static final String USER_IDS = "CamelKeycloakUserIds";

    @Metadata(description = "The list of usernames for bulk operations", javaType = "java.util.List<String>")
    public static final String USERNAMES = "CamelKeycloakUsernames";

    @Metadata(description = "The list of role names for bulk operations", javaType = "java.util.List<String>")
    public static final String ROLE_NAMES = "CamelKeycloakRoleNames";

    @Metadata(description = "Continue on error during bulk operations", javaType = "Boolean")
    public static final String CONTINUE_ON_ERROR = "CamelKeycloakContinueOnError";

    @Metadata(description = "Batch size for bulk operations", javaType = "Integer")
    public static final String BATCH_SIZE = "CamelKeycloakBatchSize";

    // Permission evaluation constants
    @Metadata(description = "The access token for permission evaluation", javaType = "String")
    public static final String ACCESS_TOKEN = "CamelKeycloakAccessToken";

    @Metadata(description = "Comma-separated list of resource names or IDs to evaluate permissions for", javaType = "String")
    public static final String PERMISSION_RESOURCE_NAMES = "CamelKeycloakPermissionResourceNames";

    @Metadata(description = "Comma-separated list of scopes to evaluate permissions for", javaType = "String")
    public static final String PERMISSION_SCOPES = "CamelKeycloakPermissionScopes";

    @Metadata(description = "Subject token for permission evaluation on behalf of a user", javaType = "String")
    public static final String SUBJECT_TOKEN = "CamelKeycloakSubjectToken";

    @Metadata(description = "Audience for permission evaluation", javaType = "String")
    public static final String PERMISSION_AUDIENCE = "CamelKeycloakPermissionAudience";

    @Metadata(description = "Whether to only return the list of permissions without obtaining an RPT", javaType = "Boolean")
    public static final String PERMISSIONS_ONLY = "CamelKeycloakPermissionsOnly";

    private KeycloakConstants() {
        // Utility class
    }
}
