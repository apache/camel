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
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
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

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
