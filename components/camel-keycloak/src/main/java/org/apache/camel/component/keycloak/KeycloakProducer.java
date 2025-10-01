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

import java.util.List;

import jakarta.ws.rs.core.Response;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to Keycloak Admin API
 */
public class KeycloakProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakProducer.class);
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

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
