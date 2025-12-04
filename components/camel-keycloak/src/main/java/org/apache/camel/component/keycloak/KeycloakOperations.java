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

public enum KeycloakOperations {
    createRealm,
    deleteRealm,
    getRealm,
    updateRealm,
    createUser,
    deleteUser,
    getUser,
    updateUser,
    listUsers,
    searchUsers,
    createRole,
    deleteRole,
    getRole,
    updateRole,
    listRoles,
    assignRoleToUser,
    removeRoleFromUser,
    getUserRoles,
    // Group operations
    createGroup,
    deleteGroup,
    getGroup,
    updateGroup,
    listGroups,
    addUserToGroup,
    removeUserFromGroup,
    listUserGroups,
    // Client operations
    createClient,
    deleteClient,
    getClient,
    updateClient,
    listClients,
    // User password operations
    resetUserPassword,
    // Client role operations
    createClientRole,
    deleteClientRole,
    getClientRole,
    updateClientRole,
    listClientRoles,
    assignClientRoleToUser,
    removeClientRoleFromUser,
    // User session operations
    listUserSessions,
    logoutUser,
    // Client scope operations
    createClientScope,
    deleteClientScope,
    getClientScope,
    updateClientScope,
    listClientScopes,
    // Identity Provider operations
    createIdentityProvider,
    deleteIdentityProvider,
    getIdentityProvider,
    updateIdentityProvider,
    listIdentityProviders,
    // Authorization Services operations
    createResource,
    deleteResource,
    getResource,
    updateResource,
    listResources,
    createResourcePolicy,
    deleteResourcePolicy,
    getResourcePolicy,
    updateResourcePolicy,
    listResourcePolicies,
    createResourcePermission,
    deleteResourcePermission,
    getResourcePermission,
    updateResourcePermission,
    listResourcePermissions,
    evaluatePermission,
    // User Attribute operations
    getUserAttributes,
    setUserAttribute,
    deleteUserAttribute,
    // User Credential operations
    getUserCredentials,
    deleteUserCredential,
    // User Action operations
    sendVerifyEmail,
    sendPasswordResetEmail,
    addRequiredAction,
    removeRequiredAction,
    executeActionsEmail,
    // Client Secret Management
    getClientSecret,
    regenerateClientSecret,
    // Bulk operations
    bulkCreateUsers,
    bulkDeleteUsers,
    bulkAssignRolesToUser,
    bulkAssignRoleToUsers,
    bulkUpdateUsers
}
