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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.resource.AuthorizationResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;
import org.keycloak.representations.idm.authorization.Permission;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourcePermissionRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;

/**
 * A Producer which sends messages to Keycloak Admin API
 */
public class KeycloakProducer extends DefaultProducer {

    public static final String MISSING_REALM_NAME = "Realm name must be specified";
    public static final String MISSING_USER_NAME = "Username must be specified";
    public static final String MISSING_ROLE_NAME = "Role name must be specified";
    public static final String MISSING_USER_ID = "User ID must be specified";
    public static final String MISSING_ROLE_ID = "Role ID must be specified";
    public static final String MISSING_GROUP_NAME = "Group name must be specified";
    public static final String MISSING_GROUP_ID = "Group ID must be specified";
    public static final String MISSING_CLIENT_ID = "Client ID must be specified";
    public static final String MISSING_CLIENT_UUID = "Client UUID must be specified";
    public static final String MISSING_PASSWORD = "Password must be specified";

    private transient String keycloakProducerToString;

    public KeycloakProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        KeycloakOperations operation = determineOperation(exchange);
        if (operation == null) {
            throw new IllegalArgumentException("Operation must be provided");
        }

        switch (operation) {
            case createRealm:
                createRealm(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteRealm:
                deleteRealm(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getRealm:
                getRealm(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateRealm:
                updateRealm(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createUser:
                createUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteUser:
                deleteUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getUser:
                getUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateUser:
                updateUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listUsers:
                listUsers(getEndpoint().getKeycloakClient(), exchange);
                break;
            case searchUsers:
                searchUsers(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createRole:
                createRole(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteRole:
                deleteRole(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getRole:
                getRole(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateRole:
                updateRole(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listRoles:
                listRoles(getEndpoint().getKeycloakClient(), exchange);
                break;
            case assignRoleToUser:
                assignRoleToUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case removeRoleFromUser:
                removeRoleFromUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getUserRoles:
                getUserRoles(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createGroup:
                createGroup(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteGroup:
                deleteGroup(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getGroup:
                getGroup(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateGroup:
                updateGroup(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listGroups:
                listGroups(getEndpoint().getKeycloakClient(), exchange);
                break;
            case addUserToGroup:
                addUserToGroup(getEndpoint().getKeycloakClient(), exchange);
                break;
            case removeUserFromGroup:
                removeUserFromGroup(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listUserGroups:
                listUserGroups(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createClient:
                createClient(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteClient:
                deleteClient(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getClient:
                getClient(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateClient:
                updateClient(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listClients:
                listClients(getEndpoint().getKeycloakClient(), exchange);
                break;
            case resetUserPassword:
                resetUserPassword(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createClientRole:
                createClientRole(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteClientRole:
                deleteClientRole(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getClientRole:
                getClientRole(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateClientRole:
                updateClientRole(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listClientRoles:
                listClientRoles(getEndpoint().getKeycloakClient(), exchange);
                break;
            case assignClientRoleToUser:
                assignClientRoleToUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case removeClientRoleFromUser:
                removeClientRoleFromUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listUserSessions:
                listUserSessions(getEndpoint().getKeycloakClient(), exchange);
                break;
            case logoutUser:
                logoutUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createClientScope:
                createClientScope(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteClientScope:
                deleteClientScope(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getClientScope:
                getClientScope(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateClientScope:
                updateClientScope(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listClientScopes:
                listClientScopes(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createIdentityProvider:
                createIdentityProvider(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteIdentityProvider:
                deleteIdentityProvider(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getIdentityProvider:
                getIdentityProvider(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateIdentityProvider:
                updateIdentityProvider(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listIdentityProviders:
                listIdentityProviders(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createResource:
                createResource(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteResource:
                deleteResource(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getResource:
                getResource(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateResource:
                updateResource(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listResources:
                listResources(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createResourcePolicy:
                createResourcePolicy(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteResourcePolicy:
                deleteResourcePolicy(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getResourcePolicy:
                getResourcePolicy(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateResourcePolicy:
                updateResourcePolicy(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listResourcePolicies:
                listResourcePolicies(getEndpoint().getKeycloakClient(), exchange);
                break;
            case createResourcePermission:
                createResourcePermission(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteResourcePermission:
                deleteResourcePermission(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getResourcePermission:
                getResourcePermission(getEndpoint().getKeycloakClient(), exchange);
                break;
            case updateResourcePermission:
                updateResourcePermission(getEndpoint().getKeycloakClient(), exchange);
                break;
            case listResourcePermissions:
                listResourcePermissions(getEndpoint().getKeycloakClient(), exchange);
                break;
            case evaluatePermission:
                evaluatePermission(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getUserAttributes:
                getUserAttributes(getEndpoint().getKeycloakClient(), exchange);
                break;
            case setUserAttribute:
                setUserAttribute(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteUserAttribute:
                deleteUserAttribute(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getUserCredentials:
                getUserCredentials(getEndpoint().getKeycloakClient(), exchange);
                break;
            case deleteUserCredential:
                deleteUserCredential(getEndpoint().getKeycloakClient(), exchange);
                break;
            case sendVerifyEmail:
                sendVerifyEmail(getEndpoint().getKeycloakClient(), exchange);
                break;
            case sendPasswordResetEmail:
                sendPasswordResetEmail(getEndpoint().getKeycloakClient(), exchange);
                break;
            case addRequiredAction:
                addRequiredAction(getEndpoint().getKeycloakClient(), exchange);
                break;
            case removeRequiredAction:
                removeRequiredAction(getEndpoint().getKeycloakClient(), exchange);
                break;
            case executeActionsEmail:
                executeActionsEmail(getEndpoint().getKeycloakClient(), exchange);
                break;
            case getClientSecret:
                getClientSecret(getEndpoint().getKeycloakClient(), exchange);
                break;
            case regenerateClientSecret:
                regenerateClientSecret(getEndpoint().getKeycloakClient(), exchange);
                break;
            case bulkCreateUsers:
                bulkCreateUsers(getEndpoint().getKeycloakClient(), exchange);
                break;
            case bulkDeleteUsers:
                bulkDeleteUsers(getEndpoint().getKeycloakClient(), exchange);
                break;
            case bulkAssignRolesToUser:
                bulkAssignRolesToUser(getEndpoint().getKeycloakClient(), exchange);
                break;
            case bulkAssignRoleToUsers:
                bulkAssignRoleToUsers(getEndpoint().getKeycloakClient(), exchange);
                break;
            case bulkUpdateUsers:
                bulkUpdateUsers(getEndpoint().getKeycloakClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private KeycloakOperations determineOperation(Exchange exchange) {
        KeycloakOperations operation = exchange.getIn().getHeader(KeycloakConstants.OPERATION, KeycloakOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected KeycloakConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (keycloakProducerToString == null) {
            keycloakProducerToString = "KeycloakProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return keycloakProducerToString;
    }

    @Override
    public KeycloakEndpoint getEndpoint() {
        return (KeycloakEndpoint) super.getEndpoint();
    }

    private void createRealm(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RealmRepresentation) {
                keycloakClient.realms().create((RealmRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Realm created successfully");
            }
        } else {
            RealmRepresentation realm = new RealmRepresentation();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.REALM_NAME))) {
                String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
                realm.setRealm(realmName);
                realm.setEnabled(true);
            } else {
                throw new IllegalArgumentException(MISSING_REALM_NAME);
            }
            keycloakClient.realms().create(realm);
            Message message = getMessageForResponse(exchange);
            message.setBody("Realm created successfully");
        }
    }

    private void deleteRealm(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        keycloakClient.realm(realmName).remove();
        Message message = getMessageForResponse(exchange);
        message.setBody("Realm deleted successfully");
    }

    private void getRealm(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        RealmRepresentation realm = keycloakClient.realm(realmName).toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(realm);
    }

    private void updateRealm(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RealmRepresentation) {
                keycloakClient.realm(realmName).update((RealmRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Realm updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update realm requires POJO request with RealmRepresentation");
        }
    }

    private void createUser(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UserRepresentation) {
                Response response = keycloakClient.realm(realmName).users().create((UserRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            UserRepresentation user = new UserRepresentation();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.USERNAME))) {
                String username = exchange.getIn().getHeader(KeycloakConstants.USERNAME, String.class);
                user.setUsername(username);
                user.setEnabled(true);

                if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.USER_EMAIL))) {
                    user.setEmail(exchange.getIn().getHeader(KeycloakConstants.USER_EMAIL, String.class));
                }
                if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.USER_FIRST_NAME))) {
                    user.setFirstName(exchange.getIn().getHeader(KeycloakConstants.USER_FIRST_NAME, String.class));
                }
                if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.USER_LAST_NAME))) {
                    user.setLastName(exchange.getIn().getHeader(KeycloakConstants.USER_LAST_NAME, String.class));
                }
            } else {
                throw new IllegalArgumentException(MISSING_USER_NAME);
            }
            Response response = keycloakClient.realm(realmName).users().create(user);
            Message message = getMessageForResponse(exchange);
            message.setBody(response);
        }
    }

    private void deleteUser(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        Response response = keycloakClient.realm(realmName).users().delete(userId);
        Message message = getMessageForResponse(exchange);
        message.setBody(response);
    }

    private void getUser(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        UserRepresentation user = keycloakClient.realm(realmName).users().get(userId).toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(user);
    }

    private void updateUser(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UserRepresentation) {
                keycloakClient.realm(realmName).users().get(userId).update((UserRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("User updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update user requires POJO request with UserRepresentation");
        }
    }

    private void listUsers(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<UserRepresentation> users = keycloakClient.realm(realmName).users().list();
        Message message = getMessageForResponse(exchange);
        message.setBody(users);
    }

    private void createRole(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RoleRepresentation) {
                keycloakClient.realm(realmName).roles().create((RoleRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Role created successfully");
            }
        } else {
            RoleRepresentation role = new RoleRepresentation();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME))) {
                String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
                role.setName(roleName);

                if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.ROLE_DESCRIPTION))) {
                    role.setDescription(exchange.getIn().getHeader(KeycloakConstants.ROLE_DESCRIPTION, String.class));
                }
            } else {
                throw new IllegalArgumentException(MISSING_ROLE_NAME);
            }
            keycloakClient.realm(realmName).roles().create(role);
            Message message = getMessageForResponse(exchange);
            message.setBody("Role created successfully");
        }
    }

    private void deleteRole(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        keycloakClient.realm(realmName).roles().deleteRole(roleName);
        Message message = getMessageForResponse(exchange);
        message.setBody("Role deleted successfully");
    }

    private void getRole(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        RoleRepresentation role = keycloakClient.realm(realmName).roles().get(roleName).toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(role);
    }

    private void updateRole(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RoleRepresentation) {
                keycloakClient.realm(realmName).roles().get(roleName).update((RoleRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Role updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update role requires POJO request with RoleRepresentation");
        }
    }

    private void listRoles(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<RoleRepresentation> roles = keycloakClient.realm(realmName).roles().list();
        Message message = getMessageForResponse(exchange);
        message.setBody(roles);
    }

    private void assignRoleToUser(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        RoleRepresentation role = keycloakClient.realm(realmName).roles().get(roleName).toRepresentation();
        keycloakClient.realm(realmName).users().get(userId).roles().realmLevel().add(List.of(role));
        Message message = getMessageForResponse(exchange);
        message.setBody("Role assigned to user successfully");
    }

    private void removeRoleFromUser(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        RoleRepresentation role = keycloakClient.realm(realmName).roles().get(roleName).toRepresentation();
        keycloakClient.realm(realmName).users().get(userId).roles().realmLevel().remove(List.of(role));
        Message message = getMessageForResponse(exchange);
        message.setBody("Role removed from user successfully");
    }

    private void createGroup(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GroupRepresentation) {
                Response response = keycloakClient.realm(realmName).groups().add((GroupRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            GroupRepresentation group = new GroupRepresentation();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.GROUP_NAME))) {
                String groupName = exchange.getIn().getHeader(KeycloakConstants.GROUP_NAME, String.class);
                group.setName(groupName);
            } else {
                throw new IllegalArgumentException(MISSING_GROUP_NAME);
            }
            Response response = keycloakClient.realm(realmName).groups().add(group);
            Message message = getMessageForResponse(exchange);
            message.setBody(response);
        }
    }

    private void deleteGroup(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String groupId = exchange.getIn().getHeader(KeycloakConstants.GROUP_ID, String.class);
        if (ObjectHelper.isEmpty(groupId)) {
            throw new IllegalArgumentException(MISSING_GROUP_ID);
        }

        keycloakClient.realm(realmName).groups().group(groupId).remove();
        Message message = getMessageForResponse(exchange);
        message.setBody("Group deleted successfully");
    }

    private void getGroup(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String groupId = exchange.getIn().getHeader(KeycloakConstants.GROUP_ID, String.class);
        if (ObjectHelper.isEmpty(groupId)) {
            throw new IllegalArgumentException(MISSING_GROUP_ID);
        }

        GroupRepresentation group = keycloakClient.realm(realmName).groups().group(groupId).toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(group);
    }

    private void updateGroup(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String groupId = exchange.getIn().getHeader(KeycloakConstants.GROUP_ID, String.class);
        if (ObjectHelper.isEmpty(groupId)) {
            throw new IllegalArgumentException(MISSING_GROUP_ID);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GroupRepresentation) {
                keycloakClient.realm(realmName).groups().group(groupId).update((GroupRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Group updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update group requires POJO request with GroupRepresentation");
        }
    }

    private void listGroups(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<GroupRepresentation> groups = keycloakClient.realm(realmName).groups().groups();
        Message message = getMessageForResponse(exchange);
        message.setBody(groups);
    }

    private void addUserToGroup(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String groupId = exchange.getIn().getHeader(KeycloakConstants.GROUP_ID, String.class);
        if (ObjectHelper.isEmpty(groupId)) {
            throw new IllegalArgumentException(MISSING_GROUP_ID);
        }

        keycloakClient.realm(realmName).users().get(userId).joinGroup(groupId);
        Message message = getMessageForResponse(exchange);
        message.setBody("User added to group successfully");
    }

    private void removeUserFromGroup(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String groupId = exchange.getIn().getHeader(KeycloakConstants.GROUP_ID, String.class);
        if (ObjectHelper.isEmpty(groupId)) {
            throw new IllegalArgumentException(MISSING_GROUP_ID);
        }

        keycloakClient.realm(realmName).users().get(userId).leaveGroup(groupId);
        Message message = getMessageForResponse(exchange);
        message.setBody("User removed from group successfully");
    }

    private void listUserGroups(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        List<GroupRepresentation> groups = keycloakClient.realm(realmName).users().get(userId).groups();
        Message message = getMessageForResponse(exchange);
        message.setBody(groups);
    }

    private void createClient(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ClientRepresentation) {
                Response response = keycloakClient.realm(realmName).clients().create((ClientRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            ClientRepresentation client = new ClientRepresentation();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.CLIENT_ID))) {
                String clientId = exchange.getIn().getHeader(KeycloakConstants.CLIENT_ID, String.class);
                client.setClientId(clientId);
                client.setEnabled(true);
            } else {
                throw new IllegalArgumentException(MISSING_CLIENT_ID);
            }
            Response response = keycloakClient.realm(realmName).clients().create(client);
            Message message = getMessageForResponse(exchange);
            message.setBody(response);
        }
    }

    private void deleteClient(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        keycloakClient.realm(realmName).clients().get(clientUuid).remove();
        Message message = getMessageForResponse(exchange);
        message.setBody("Client deleted successfully");
    }

    private void getClient(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        ClientRepresentation client = keycloakClient.realm(realmName).clients().get(clientUuid).toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(client);
    }

    private void updateClient(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ClientRepresentation) {
                keycloakClient.realm(realmName).clients().get(clientUuid).update((ClientRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Client updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update client requires POJO request with ClientRepresentation");
        }
    }

    private void listClients(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<ClientRepresentation> clients = keycloakClient.realm(realmName).clients().findAll();
        Message message = getMessageForResponse(exchange);
        message.setBody(clients);
    }

    private void resetUserPassword(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String password = exchange.getIn().getHeader(KeycloakConstants.USER_PASSWORD, String.class);
        if (ObjectHelper.isEmpty(password)) {
            throw new IllegalArgumentException(MISSING_PASSWORD);
        }

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);

        // Check if password is temporary (defaults to false if not specified)
        Boolean temporary = exchange.getIn().getHeader(KeycloakConstants.PASSWORD_TEMPORARY, Boolean.class);
        credential.setTemporary(temporary != null ? temporary : false);

        keycloakClient.realm(realmName).users().get(userId).resetPassword(credential);
        Message message = getMessageForResponse(exchange);
        message.setBody("User password reset successfully");
    }

    private void getUserRoles(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        List<RoleRepresentation> roles = keycloakClient.realm(realmName).users().get(userId).roles().realmLevel().listAll();
        Message message = getMessageForResponse(exchange);
        message.setBody(roles);
    }

    private void createClientRole(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RoleRepresentation) {
                keycloakClient.realm(realmName).clients().get(clientUuid).roles().create((RoleRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Client role created successfully");
            }
        } else {
            RoleRepresentation role = new RoleRepresentation();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME))) {
                String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
                role.setName(roleName);

                if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.ROLE_DESCRIPTION))) {
                    role.setDescription(exchange.getIn().getHeader(KeycloakConstants.ROLE_DESCRIPTION, String.class));
                }
            } else {
                throw new IllegalArgumentException(MISSING_ROLE_NAME);
            }
            keycloakClient.realm(realmName).clients().get(clientUuid).roles().create(role);
            Message message = getMessageForResponse(exchange);
            message.setBody("Client role created successfully");
        }
    }

    private void deleteClientRole(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        keycloakClient.realm(realmName).clients().get(clientUuid).roles().deleteRole(roleName);
        Message message = getMessageForResponse(exchange);
        message.setBody("Client role deleted successfully");
    }

    private void getClientRole(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        RoleRepresentation role
                = keycloakClient.realm(realmName).clients().get(clientUuid).roles().get(roleName).toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(role);
    }

    private void updateClientRole(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RoleRepresentation) {
                keycloakClient.realm(realmName).clients().get(clientUuid).roles().get(roleName)
                        .update((RoleRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Client role updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update client role requires POJO request with RoleRepresentation");
        }
    }

    private void listClientRoles(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        List<RoleRepresentation> roles = keycloakClient.realm(realmName).clients().get(clientUuid).roles().list();
        Message message = getMessageForResponse(exchange);
        message.setBody(roles);
    }

    private void assignClientRoleToUser(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        RoleRepresentation role
                = keycloakClient.realm(realmName).clients().get(clientUuid).roles().get(roleName).toRepresentation();
        keycloakClient.realm(realmName).users().get(userId).roles().clientLevel(clientUuid).add(List.of(role));
        Message message = getMessageForResponse(exchange);
        message.setBody("Client role assigned to user successfully");
    }

    private void removeClientRoleFromUser(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        RoleRepresentation role
                = keycloakClient.realm(realmName).clients().get(clientUuid).roles().get(roleName).toRepresentation();
        keycloakClient.realm(realmName).users().get(userId).roles().clientLevel(clientUuid).remove(List.of(role));
        Message message = getMessageForResponse(exchange);
        message.setBody("Client role removed from user successfully");
    }

    private void searchUsers(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String searchQuery = exchange.getIn().getHeader(KeycloakConstants.SEARCH_QUERY, String.class);
        Integer first = exchange.getIn().getHeader(KeycloakConstants.FIRST_RESULT, Integer.class);
        Integer max = exchange.getIn().getHeader(KeycloakConstants.MAX_RESULTS, Integer.class);

        List<UserRepresentation> users;
        if (ObjectHelper.isNotEmpty(searchQuery)) {
            if (first != null && max != null) {
                users = keycloakClient.realm(realmName).users().search(searchQuery, first, max);
            } else {
                users = keycloakClient.realm(realmName).users().search(searchQuery);
            }
        } else {
            // If no search query, list all users with pagination
            if (first != null && max != null) {
                users = keycloakClient.realm(realmName).users().list(first, max);
            } else {
                users = keycloakClient.realm(realmName).users().list();
            }
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(users);
    }

    private void listUserSessions(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        List<UserSessionRepresentation> sessions = keycloakClient.realm(realmName).users().get(userId).getUserSessions();
        Message message = getMessageForResponse(exchange);
        message.setBody(sessions);
    }

    private void logoutUser(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        keycloakClient.realm(realmName).users().get(userId).logout();
        Message message = getMessageForResponse(exchange);
        message.setBody("User logged out successfully");
    }

    private void createClientScope(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ClientScopeRepresentation) {
                Response response = keycloakClient.realm(realmName).clientScopes().create((ClientScopeRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            ClientScopeRepresentation clientScope = new ClientScopeRepresentation();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(KeycloakConstants.CLIENT_SCOPE_NAME))) {
                String scopeName = exchange.getIn().getHeader(KeycloakConstants.CLIENT_SCOPE_NAME, String.class);
                clientScope.setName(scopeName);
            } else {
                throw new IllegalArgumentException("Client scope name must be specified");
            }
            Response response = keycloakClient.realm(realmName).clientScopes().create(clientScope);
            Message message = getMessageForResponse(exchange);
            message.setBody(response);
        }
    }

    private void deleteClientScope(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientScopeId = exchange.getIn().getHeader(KeycloakConstants.CLIENT_SCOPE_ID, String.class);
        if (ObjectHelper.isEmpty(clientScopeId)) {
            throw new IllegalArgumentException("Client scope ID must be specified");
        }

        keycloakClient.realm(realmName).clientScopes().get(clientScopeId).remove();
        Message message = getMessageForResponse(exchange);
        message.setBody("Client scope deleted successfully");
    }

    private void getClientScope(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientScopeId = exchange.getIn().getHeader(KeycloakConstants.CLIENT_SCOPE_ID, String.class);
        if (ObjectHelper.isEmpty(clientScopeId)) {
            throw new IllegalArgumentException("Client scope ID must be specified");
        }

        ClientScopeRepresentation clientScope
                = keycloakClient.realm(realmName).clientScopes().get(clientScopeId).toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(clientScope);
    }

    private void updateClientScope(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientScopeId = exchange.getIn().getHeader(KeycloakConstants.CLIENT_SCOPE_ID, String.class);
        if (ObjectHelper.isEmpty(clientScopeId)) {
            throw new IllegalArgumentException("Client scope ID must be specified");
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ClientScopeRepresentation) {
                keycloakClient.realm(realmName).clientScopes().get(clientScopeId).update((ClientScopeRepresentation) payload);
                Message message = getMessageForResponse(exchange);
                message.setBody("Client scope updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update client scope requires POJO request with ClientScopeRepresentation");
        }
    }

    private void listClientScopes(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<ClientScopeRepresentation> clientScopes = keycloakClient.realm(realmName).clientScopes().findAll();
        Message message = getMessageForResponse(exchange);
        message.setBody(clientScopes);
    }

    // Identity Provider operations
    private void createIdentityProvider(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof IdentityProviderRepresentation idpRepresentation) {
                Response response
                        = keycloakClient.realm(realmName).identityProviders().create(idpRepresentation);
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            throw new IllegalArgumentException(
                    "Create identity provider requires POJO request with IdentityProviderRepresentation");
        }
    }

    private void deleteIdentityProvider(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String idpAlias = exchange.getIn().getHeader(KeycloakConstants.IDP_ALIAS, String.class);
        if (ObjectHelper.isEmpty(idpAlias)) {
            throw new IllegalArgumentException("Identity provider alias must be specified");
        }

        keycloakClient.realm(realmName).identityProviders().get(idpAlias).remove();
        Message message = getMessageForResponse(exchange);
        message.setBody("Identity provider deleted successfully");
    }

    private void getIdentityProvider(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String idpAlias = exchange.getIn().getHeader(KeycloakConstants.IDP_ALIAS, String.class);
        if (ObjectHelper.isEmpty(idpAlias)) {
            throw new IllegalArgumentException("Identity provider alias must be specified");
        }

        IdentityProviderRepresentation idp
                = keycloakClient.realm(realmName).identityProviders().get(idpAlias).toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(idp);
    }

    private void updateIdentityProvider(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String idpAlias = exchange.getIn().getHeader(KeycloakConstants.IDP_ALIAS, String.class);
        if (ObjectHelper.isEmpty(idpAlias)) {
            throw new IllegalArgumentException("Identity provider alias must be specified");
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof IdentityProviderRepresentation idpRepresentation) {
                keycloakClient.realm(realmName).identityProviders().get(idpAlias)
                        .update(idpRepresentation);
                Message message = getMessageForResponse(exchange);
                message.setBody("Identity provider updated successfully");
            }
        } else {
            throw new IllegalArgumentException(
                    "Update identity provider requires POJO request with IdentityProviderRepresentation");
        }
    }

    private void listIdentityProviders(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<IdentityProviderRepresentation> idps = keycloakClient.realm(realmName).identityProviders().findAll();
        Message message = getMessageForResponse(exchange);
        message.setBody(idps);
    }

    // Authorization Services operations
    private void createResource(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ResourceRepresentation resourceRepresentation) {
                Response response = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().resources()
                        .create(resourceRepresentation);
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            throw new IllegalArgumentException("Create resource requires POJO request with ResourceRepresentation");
        }
    }

    private void deleteResource(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String resourceId = exchange.getIn().getHeader(KeycloakConstants.RESOURCE_ID, String.class);
        if (ObjectHelper.isEmpty(resourceId)) {
            throw new IllegalArgumentException("Resource ID must be specified");
        }

        keycloakClient.realm(realmName).clients().get(clientUuid).authorization().resources().resource(resourceId).remove();
        Message message = getMessageForResponse(exchange);
        message.setBody("Resource deleted successfully");
    }

    private void getResource(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String resourceId = exchange.getIn().getHeader(KeycloakConstants.RESOURCE_ID, String.class);
        if (ObjectHelper.isEmpty(resourceId)) {
            throw new IllegalArgumentException("Resource ID must be specified");
        }

        ResourceRepresentation resource
                = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().resources().resource(resourceId)
                        .toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(resource);
    }

    private void updateResource(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String resourceId = exchange.getIn().getHeader(KeycloakConstants.RESOURCE_ID, String.class);
        if (ObjectHelper.isEmpty(resourceId)) {
            throw new IllegalArgumentException("Resource ID must be specified");
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ResourceRepresentation resourceRepresentation) {
                keycloakClient.realm(realmName).clients().get(clientUuid).authorization().resources().resource(resourceId)
                        .update(resourceRepresentation);
                Message message = getMessageForResponse(exchange);
                message.setBody("Resource updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update resource requires POJO request with ResourceRepresentation");
        }
    }

    private void listResources(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        List<ResourceRepresentation> resources
                = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().resources().resources();
        Message message = getMessageForResponse(exchange);
        message.setBody(resources);
    }

    private void createResourcePolicy(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PolicyRepresentation policyRepresentation) {
                Response response = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies()
                        .create(policyRepresentation);
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            throw new IllegalArgumentException("Create policy requires POJO request with PolicyRepresentation");
        }
    }

    private void deleteResourcePolicy(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String policyId = exchange.getIn().getHeader(KeycloakConstants.POLICY_ID, String.class);
        if (ObjectHelper.isEmpty(policyId)) {
            throw new IllegalArgumentException("Policy ID must be specified");
        }

        keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies().policy(policyId).remove();
        Message message = getMessageForResponse(exchange);
        message.setBody("Policy deleted successfully");
    }

    private void getResourcePolicy(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String policyId = exchange.getIn().getHeader(KeycloakConstants.POLICY_ID, String.class);
        if (ObjectHelper.isEmpty(policyId)) {
            throw new IllegalArgumentException("Policy ID must be specified");
        }

        PolicyRepresentation policy
                = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies().policy(policyId)
                        .toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(policy);
    }

    private void updateResourcePolicy(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String policyId = exchange.getIn().getHeader(KeycloakConstants.POLICY_ID, String.class);
        if (ObjectHelper.isEmpty(policyId)) {
            throw new IllegalArgumentException("Policy ID must be specified");
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PolicyRepresentation policyRepresentation) {
                keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies().policy(policyId)
                        .update(policyRepresentation);
                Message message = getMessageForResponse(exchange);
                message.setBody("Policy updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update policy requires POJO request with PolicyRepresentation");
        }
    }

    private void listResourcePolicies(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        List<PolicyRepresentation> policies
                = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies().policies();
        Message message = getMessageForResponse(exchange);
        message.setBody(policies);
    }

    private void createResourcePermission(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ResourcePermissionRepresentation permissionRepresentation) {
                Response response = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().permissions()
                        .resource().create(permissionRepresentation);
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            throw new IllegalArgumentException("Create permission requires POJO request with ResourcePermissionRepresentation");
        }
    }

    private void deleteResourcePermission(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String permissionId = exchange.getIn().getHeader(KeycloakConstants.PERMISSION_ID, String.class);
        if (ObjectHelper.isEmpty(permissionId)) {
            throw new IllegalArgumentException("Permission ID must be specified");
        }

        // Use policy endpoint for permissions
        keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies().policy(permissionId).remove();
        Message message = getMessageForResponse(exchange);
        message.setBody("Permission deleted successfully");
    }

    private void getResourcePermission(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String permissionId = exchange.getIn().getHeader(KeycloakConstants.PERMISSION_ID, String.class);
        if (ObjectHelper.isEmpty(permissionId)) {
            throw new IllegalArgumentException("Permission ID must be specified");
        }

        // Use policy endpoint for permissions
        PolicyRepresentation permission
                = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies().policy(permissionId)
                        .toRepresentation();
        Message message = getMessageForResponse(exchange);
        message.setBody(permission);
    }

    private void updateResourcePermission(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        String permissionId = exchange.getIn().getHeader(KeycloakConstants.PERMISSION_ID, String.class);
        if (ObjectHelper.isEmpty(permissionId)) {
            throw new IllegalArgumentException("Permission ID must be specified");
        }

        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PolicyRepresentation policyRepresentation) {
                // Use policy endpoint for permissions
                keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies().policy(permissionId)
                        .update(policyRepresentation);
                Message message = getMessageForResponse(exchange);
                message.setBody("Permission updated successfully");
            }
        } else {
            throw new IllegalArgumentException("Update permission requires POJO request with PolicyRepresentation");
        }
    }

    private void listResourcePermissions(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        // List all policies (which includes permissions)
        List<PolicyRepresentation> permissions
                = keycloakClient.realm(realmName).clients().get(clientUuid).authorization().policies().policies();
        Message message = getMessageForResponse(exchange);
        message.setBody(permissions);
    }

    private void evaluatePermission(Keycloak keycloakClient, Exchange exchange) {
        KeycloakConfiguration config = getConfiguration();

        // Validate required configuration
        if (ObjectHelper.isEmpty(config.getServerUrl())) {
            throw new IllegalArgumentException("Server URL must be specified for permission evaluation");
        }
        if (ObjectHelper.isEmpty(config.getRealm())) {
            throw new IllegalArgumentException("Realm must be specified for permission evaluation");
        }
        if (ObjectHelper.isEmpty(config.getClientId())) {
            throw new IllegalArgumentException("Client ID must be specified for permission evaluation");
        }
        if (ObjectHelper.isEmpty(config.getClientSecret())) {
            throw new IllegalArgumentException("Client secret must be specified for permission evaluation");
        }

        // Create AuthzClient configuration
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("secret", config.getClientSecret());

        Configuration authzConfig = new Configuration(
                config.getServerUrl(),
                config.getRealm(),
                config.getClientId(),
                credentials,
                null);

        AuthzClient authzClient = AuthzClient.create(authzConfig);

        // Get access token from header or use username/password credentials
        String accessToken = exchange.getIn().getHeader(KeycloakConstants.ACCESS_TOKEN, String.class);
        String subjectToken = exchange.getIn().getHeader(KeycloakConstants.SUBJECT_TOKEN, String.class);

        AuthorizationResource authzResource;
        if (ObjectHelper.isNotEmpty(accessToken)) {
            // Use provided access token
            authzResource = authzClient.authorization(accessToken);
        } else if (ObjectHelper.isNotEmpty(config.getUsername()) && ObjectHelper.isNotEmpty(config.getPassword())) {
            // Use username/password to obtain token
            authzResource = authzClient.authorization(config.getUsername(), config.getPassword());
        } else {
            // Use client credentials (default for service accounts)
            authzResource = authzClient.authorization();
        }

        // Build authorization request
        AuthorizationRequest request = new AuthorizationRequest();

        // Set subject token if provided (for token exchange scenarios)
        if (ObjectHelper.isNotEmpty(subjectToken)) {
            request.setSubjectToken(subjectToken);
        }

        // Set audience if provided
        String audience = exchange.getIn().getHeader(KeycloakConstants.PERMISSION_AUDIENCE, String.class);
        if (ObjectHelper.isNotEmpty(audience)) {
            request.setAudience(audience);
        }

        // Add specific resource permissions if provided
        String resourceNames = exchange.getIn().getHeader(KeycloakConstants.PERMISSION_RESOURCE_NAMES, String.class);
        String scopes = exchange.getIn().getHeader(KeycloakConstants.PERMISSION_SCOPES, String.class);

        if (ObjectHelper.isNotEmpty(resourceNames)) {
            String[] resources = resourceNames.split(",");
            String[] scopeArray = ObjectHelper.isNotEmpty(scopes) ? scopes.split(",") : new String[0];

            for (String resource : resources) {
                String trimmedResource = resource.trim();
                if (!trimmedResource.isEmpty()) {
                    if (scopeArray.length > 0) {
                        // Trim each scope
                        String[] trimmedScopes = Arrays.stream(scopeArray)
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toArray(String[]::new);
                        request.addPermission(trimmedResource, trimmedScopes);
                    } else {
                        request.addPermission(trimmedResource);
                    }
                }
            }
        } else if (ObjectHelper.isNotEmpty(scopes)) {
            // If only scopes are provided without resources, add them to the request
            String[] scopeArray = scopes.split(",");
            for (String scope : scopeArray) {
                String trimmedScope = scope.trim();
                if (!trimmedScope.isEmpty()) {
                    // When no resource is specified, use null resource with scope
                    request.addPermission(null, trimmedScope);
                }
            }
        }

        // Check if we should only return permissions without obtaining RPT
        Boolean permissionsOnly = exchange.getIn().getHeader(KeycloakConstants.PERMISSIONS_ONLY, Boolean.class);

        Message message = getMessageForResponse(exchange);

        if (Boolean.TRUE.equals(permissionsOnly)) {
            // Get permissions directly without RPT
            List<Permission> permissions = authzResource.getPermissions(request);
            Map<String, Object> result = new HashMap<>();
            result.put("permissions", permissions);
            result.put("permissionCount", permissions.size());
            result.put("granted", !permissions.isEmpty());
            message.setBody(result);
        } else {
            // Obtain RPT (Requesting Party Token) with permissions
            AuthorizationResponse authzResponse = authzResource.authorize(request);
            Map<String, Object> result = new HashMap<>();
            result.put("token", authzResponse.getToken());
            result.put("tokenType", authzResponse.getTokenType());
            result.put("expiresIn", authzResponse.getExpiresIn());
            result.put("refreshToken", authzResponse.getRefreshToken());
            result.put("refreshExpiresIn", authzResponse.getRefreshExpiresIn());
            result.put("upgraded", authzResponse.isUpgraded());
            message.setBody(result);
        }
    }

    // User Attribute operations
    private void getUserAttributes(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        UserRepresentation user = keycloakClient.realm(realmName).users().get(userId).toRepresentation();
        Map<String, List<String>> attributes = user.getAttributes();
        Message message = getMessageForResponse(exchange);
        message.setBody(attributes != null ? attributes : new HashMap<>());
    }

    private void setUserAttribute(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String attributeName = exchange.getIn().getHeader(KeycloakConstants.ATTRIBUTE_NAME, String.class);
        if (ObjectHelper.isEmpty(attributeName)) {
            throw new IllegalArgumentException("Attribute name must be specified");
        }

        String attributeValue = exchange.getIn().getHeader(KeycloakConstants.ATTRIBUTE_VALUE, String.class);
        if (ObjectHelper.isEmpty(attributeValue)) {
            throw new IllegalArgumentException("Attribute value must be specified");
        }

        UserRepresentation user = keycloakClient.realm(realmName).users().get(userId).toRepresentation();
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
            user.setAttributes(attributes);
        }
        attributes.put(attributeName, Arrays.asList(attributeValue));
        keycloakClient.realm(realmName).users().get(userId).update(user);
        Message message = getMessageForResponse(exchange);
        message.setBody("User attribute set successfully");
    }

    private void deleteUserAttribute(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String attributeName = exchange.getIn().getHeader(KeycloakConstants.ATTRIBUTE_NAME, String.class);
        if (ObjectHelper.isEmpty(attributeName)) {
            throw new IllegalArgumentException("Attribute name must be specified");
        }

        UserRepresentation user = keycloakClient.realm(realmName).users().get(userId).toRepresentation();
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes != null) {
            attributes.remove(attributeName);
            keycloakClient.realm(realmName).users().get(userId).update(user);
        }
        Message message = getMessageForResponse(exchange);
        message.setBody("User attribute deleted successfully");
    }

    // User Credential operations
    private void getUserCredentials(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        List<CredentialRepresentation> credentials = keycloakClient.realm(realmName).users().get(userId).credentials();
        Message message = getMessageForResponse(exchange);
        message.setBody(credentials);
    }

    private void deleteUserCredential(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String credentialId = exchange.getIn().getHeader(KeycloakConstants.CREDENTIAL_ID, String.class);
        if (ObjectHelper.isEmpty(credentialId)) {
            throw new IllegalArgumentException("Credential ID must be specified");
        }

        keycloakClient.realm(realmName).users().get(userId).removeCredential(credentialId);
        Message message = getMessageForResponse(exchange);
        message.setBody("User credential deleted successfully");
    }

    // User Action operations
    private void sendVerifyEmail(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        keycloakClient.realm(realmName).users().get(userId).sendVerifyEmail();
        Message message = getMessageForResponse(exchange);
        message.setBody("Verify email sent successfully");
    }

    private void sendPasswordResetEmail(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        // Reset password by executing required action
        List<String> actions = Arrays.asList("UPDATE_PASSWORD");
        keycloakClient.realm(realmName).users().get(userId).executeActionsEmail(actions);
        Message message = getMessageForResponse(exchange);
        message.setBody("Password reset email sent successfully");
    }

    private void addRequiredAction(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String requiredAction = exchange.getIn().getHeader(KeycloakConstants.REQUIRED_ACTION, String.class);
        if (ObjectHelper.isEmpty(requiredAction)) {
            throw new IllegalArgumentException("Required action must be specified");
        }

        UserRepresentation user = keycloakClient.realm(realmName).users().get(userId).toRepresentation();
        List<String> actions = user.getRequiredActions();
        if (actions == null) {
            actions = new ArrayList<>();
            user.setRequiredActions(actions);
        }
        if (!actions.contains(requiredAction)) {
            actions.add(requiredAction);
        }
        keycloakClient.realm(realmName).users().get(userId).update(user);
        Message message = getMessageForResponse(exchange);
        message.setBody("Required action added successfully");
    }

    private void removeRequiredAction(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        String requiredAction = exchange.getIn().getHeader(KeycloakConstants.REQUIRED_ACTION, String.class);
        if (ObjectHelper.isEmpty(requiredAction)) {
            throw new IllegalArgumentException("Required action must be specified");
        }

        UserRepresentation user = keycloakClient.realm(realmName).users().get(userId).toRepresentation();
        List<String> actions = user.getRequiredActions();
        if (actions != null) {
            actions.remove(requiredAction);
            keycloakClient.realm(realmName).users().get(userId).update(user);
        }
        Message message = getMessageForResponse(exchange);
        message.setBody("Required action removed successfully");
    }

    @SuppressWarnings("unchecked")
    private void executeActionsEmail(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        List<String> actions = exchange.getIn().getHeader(KeycloakConstants.ACTIONS, List.class);
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("Actions list must be specified");
        }

        Integer lifespan = exchange.getIn().getHeader(KeycloakConstants.LIFESPAN, Integer.class);
        String redirectUri = exchange.getIn().getHeader(KeycloakConstants.REDIRECT_URI, String.class);

        if (lifespan != null && redirectUri != null) {
            keycloakClient.realm(realmName).users().get(userId).executeActionsEmail(redirectUri, lifespan.toString(), actions);
        } else if (redirectUri != null) {
            keycloakClient.realm(realmName).users().get(userId).executeActionsEmail(actions);
        } else {
            keycloakClient.realm(realmName).users().get(userId).executeActionsEmail(actions);
        }
        Message message = getMessageForResponse(exchange);
        message.setBody("Actions email sent successfully");
    }

    // Client Secret Management operations
    private void getClientSecret(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        CredentialRepresentation secret = keycloakClient.realm(realmName).clients().get(clientUuid).getSecret();
        Message message = getMessageForResponse(exchange);
        message.setBody(secret);
    }

    private void regenerateClientSecret(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String clientUuid = exchange.getIn().getHeader(KeycloakConstants.CLIENT_UUID, String.class);
        if (ObjectHelper.isEmpty(clientUuid)) {
            throw new IllegalArgumentException(MISSING_CLIENT_UUID);
        }

        CredentialRepresentation newSecret = keycloakClient.realm(realmName).clients().get(clientUuid).generateNewSecret();
        Message message = getMessageForResponse(exchange);
        message.setBody(newSecret);
    }

    // Bulk operations
    private void bulkCreateUsers(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<UserRepresentation> users = exchange.getIn().getHeader(KeycloakConstants.USERS, List.class);
        if (users == null || users.isEmpty()) {
            // Try to get from body
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof List) {
                users = CastUtils.cast((List<?>) payload);
            } else {
                throw new IllegalArgumentException("Users list must be provided via header or body");
            }
        }

        boolean continueOnError
                = exchange.getIn().getHeader(KeycloakConstants.CONTINUE_ON_ERROR, Boolean.FALSE, Boolean.class);
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (UserRepresentation user : users) {
            Map<String, Object> result = new HashMap<>();
            result.put("username", user.getUsername());
            try (Response response = keycloakClient.realm(realmName).users().create(user)) {
                result.put("status", "success");
                result.put("statusCode", response.getStatus());
                successCount++;
                response.close();
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("error", e.getMessage());
                failureCount++;
                if (!continueOnError) {
                    throw new RuntimeException("Failed to create user: " + user.getUsername(), e);
                }
            }
            results.add(result);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", users.size());
        summary.put("success", successCount);
        summary.put("failed", failureCount);
        summary.put("results", results);

        Message message = getMessageForResponse(exchange);
        message.setBody(summary);
    }

    private void bulkDeleteUsers(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<String> userIds = exchange.getIn().getHeader(KeycloakConstants.USER_IDS, List.class);
        if (userIds == null || userIds.isEmpty()) {
            // Try usernames
            List<String> usernames = exchange.getIn().getHeader(KeycloakConstants.USERNAMES, List.class);
            if (usernames == null || usernames.isEmpty()) {
                // Try to get from body
                Object payload = exchange.getIn().getBody();
                if (payload instanceof List) {
                    userIds = CastUtils.cast((List<?>) payload);
                } else {
                    throw new IllegalArgumentException("User IDs or usernames must be provided via header or body");
                }
            } else {
                // Convert usernames to user IDs
                userIds = new ArrayList<>();
                for (String username : usernames) {
                    List<UserRepresentation> foundUsers
                            = keycloakClient.realm(realmName).users().searchByUsername(username, true);
                    if (!foundUsers.isEmpty()) {
                        userIds.add(foundUsers.get(0).getId());
                    }
                }
            }
        }

        boolean continueOnError
                = exchange.getIn().getHeader(KeycloakConstants.CONTINUE_ON_ERROR, Boolean.FALSE, Boolean.class);
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (String userId : userIds) {
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            try {
                Response response = keycloakClient.realm(realmName).users().delete(userId);
                result.put("status", "success");
                result.put("statusCode", response.getStatus());
                successCount++;
                response.close();
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("error", e.getMessage());
                failureCount++;
                if (!continueOnError) {
                    throw new RuntimeException("Failed to delete user: " + userId, e);
                }
            }
            results.add(result);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", userIds.size());
        summary.put("success", successCount);
        summary.put("failed", failureCount);
        summary.put("results", results);

        Message message = getMessageForResponse(exchange);
        message.setBody(summary);
    }

    private void bulkAssignRolesToUser(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String userId = exchange.getIn().getHeader(KeycloakConstants.USER_ID, String.class);
        if (ObjectHelper.isEmpty(userId)) {
            throw new IllegalArgumentException(MISSING_USER_ID);
        }

        List<String> roleNames = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAMES, List.class);
        if (roleNames == null || roleNames.isEmpty()) {
            // Try to get from body
            Object payload = exchange.getIn().getBody();
            if (payload instanceof List) {
                roleNames = CastUtils.cast((List<?>) payload);
            } else {
                throw new IllegalArgumentException("Role names must be provided via header or body");
            }
        }

        boolean continueOnError
                = exchange.getIn().getHeader(KeycloakConstants.CONTINUE_ON_ERROR, Boolean.FALSE, Boolean.class);
        List<Map<String, Object>> results = new ArrayList<>();
        List<RoleRepresentation> rolesToAssign = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        // Collect all roles first
        for (String roleName : roleNames) {
            Map<String, Object> result = new HashMap<>();
            result.put("roleName", roleName);
            try {
                RoleRepresentation role = keycloakClient.realm(realmName).roles().get(roleName).toRepresentation();
                rolesToAssign.add(role);
                result.put("status", "found");
                successCount++;
            } catch (Exception e) {
                result.put("status", "not_found");
                result.put("error", e.getMessage());
                failureCount++;
                if (!continueOnError) {
                    throw new RuntimeException("Failed to find role: " + roleName, e);
                }
            }
            results.add(result);
        }

        // Assign all roles at once if any were found
        if (!rolesToAssign.isEmpty()) {
            try {
                keycloakClient.realm(realmName).users().get(userId).roles().realmLevel().add(rolesToAssign);
            } catch (Exception e) {
                throw new RuntimeException("Failed to assign roles to user: " + userId, e);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", roleNames.size());
        summary.put("success", successCount);
        summary.put("failed", failureCount);
        summary.put("assigned", rolesToAssign.size());
        summary.put("results", results);

        Message message = getMessageForResponse(exchange);
        message.setBody(summary);
    }

    private void bulkAssignRoleToUsers(Keycloak keycloakClient, Exchange exchange) {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        String roleName = exchange.getIn().getHeader(KeycloakConstants.ROLE_NAME, String.class);
        if (ObjectHelper.isEmpty(roleName)) {
            throw new IllegalArgumentException(MISSING_ROLE_NAME);
        }

        // Get the role first
        RoleRepresentation role;
        try {
            role = keycloakClient.realm(realmName).roles().get(roleName).toRepresentation();
        } catch (Exception e) {
            throw new RuntimeException("Failed to find role: " + roleName, e);
        }

        List<String> userIds = exchange.getIn().getHeader(KeycloakConstants.USER_IDS, List.class);
        if (userIds == null || userIds.isEmpty()) {
            // Try usernames
            List<String> usernames = exchange.getIn().getHeader(KeycloakConstants.USERNAMES, List.class);
            if (usernames == null || usernames.isEmpty()) {
                // Try to get from body
                Object payload = exchange.getIn().getBody();
                if (payload instanceof List) {
                    userIds = CastUtils.cast((List<?>) payload);
                } else {
                    throw new IllegalArgumentException("User IDs or usernames must be provided via header or body");
                }
            } else {
                // Convert usernames to user IDs
                userIds = new ArrayList<>();
                for (String username : usernames) {
                    List<UserRepresentation> foundUsers
                            = keycloakClient.realm(realmName).users().searchByUsername(username, true);
                    if (!foundUsers.isEmpty()) {
                        userIds.add(foundUsers.get(0).getId());
                    }
                }
            }
        }

        boolean continueOnError
                = exchange.getIn().getHeader(KeycloakConstants.CONTINUE_ON_ERROR, Boolean.FALSE, Boolean.class);
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (String userId : userIds) {
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            try {
                keycloakClient.realm(realmName).users().get(userId).roles().realmLevel().add(List.of(role));
                result.put("status", "success");
                successCount++;
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("error", e.getMessage());
                failureCount++;
                if (!continueOnError) {
                    throw new RuntimeException("Failed to assign role to user: " + userId, e);
                }
            }
            results.add(result);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", userIds.size());
        summary.put("success", successCount);
        summary.put("failed", failureCount);
        summary.put("roleName", roleName);
        summary.put("results", results);

        Message message = getMessageForResponse(exchange);
        message.setBody(summary);
    }

    private void bulkUpdateUsers(Keycloak keycloakClient, Exchange exchange) throws InvalidPayloadException {
        String realmName = exchange.getIn().getHeader(KeycloakConstants.REALM_NAME, String.class);
        if (ObjectHelper.isEmpty(realmName)) {
            throw new IllegalArgumentException(MISSING_REALM_NAME);
        }

        List<UserRepresentation> users = exchange.getIn().getHeader(KeycloakConstants.USERS, List.class);
        if (users == null || users.isEmpty()) {
            // Try to get from body
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof List) {
                users = CastUtils.cast((List<?>) payload);
            } else {
                throw new IllegalArgumentException("Users list must be provided via header or body");
            }
        }

        boolean continueOnError
                = exchange.getIn().getHeader(KeycloakConstants.CONTINUE_ON_ERROR, Boolean.FALSE, Boolean.class);
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (UserRepresentation user : users) {
            Map<String, Object> result = new HashMap<>();
            result.put("userId", user.getId());
            result.put("username", user.getUsername());
            try {
                if (ObjectHelper.isEmpty(user.getId())) {
                    throw new IllegalArgumentException("User ID is required for update operation");
                }
                keycloakClient.realm(realmName).users().get(user.getId()).update(user);
                result.put("status", "success");
                successCount++;
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("error", e.getMessage());
                failureCount++;
                if (!continueOnError) {
                    throw new RuntimeException("Failed to update user: " + user.getId(), e);
                }
            }
            results.add(result);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", users.size());
        summary.put("success", successCount);
        summary.put("failed", failureCount);
        summary.put("results", results);

        Message message = getMessageForResponse(exchange);
        message.setBody(summary);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
