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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

/**
 * Manage Keycloak instances via Admin API.
 */
@UriEndpoint(firstVersion = "4.15.0", scheme = "keycloak", title = "Keycloak",
             syntax = "keycloak:label", category = { Category.SECURITY, Category.MANAGEMENT },
             headersClass = KeycloakConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "org.keycloak.representations.idm.EventRepresentation",
                                   byOption = @BodyType.When(option = "eventType", value = "admin-events",
                                                             carriers = Carrier.POJO,
                                                             javaType = "org.keycloak.representations.idm.AdminEventRepresentation"),
                                   description = "One message per event: the EventRepresentation of a user event such as a"
                                                 + " login, or the AdminEventRepresentation with eventType=admin-events. The"
                                                 + " CamelKeycloakEventType header says event or admin-event"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "pojoRequest", value = "true", carriers = Carrier.POJO),
                                  description = "Mostly not read: the operations take their input from headers, such as"
                                                + " CamelKeycloakRealmName and CamelKeycloakUserId. With pojoRequest=true the"
                                                + " create and update operations take the Keycloak representation, such as a"
                                                + " UserRepresentation for createUser; a body of another type does nothing"
                                                + " and keeps the body. The bulk operations take a List when"
                                                + " the header is not set: of UserRepresentation for bulkCreateUsers and"
                                                + " bulkUpdateUsers, of String ids or role names for the others."
                                                + " introspectToken, revokeAccessToken and revokeRefreshToken take the token"
                                                + " as text when the CamelKeycloakToken header is not set"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "createRealm", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "deleteRealm", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getRealm", carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.RealmRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateRealm", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "createUser", carriers = Carrier.POJO,
                                                          javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteUser", carriers = Carrier.POJO,
                                                          javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "getUser", carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.UserRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateUser", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listUsers", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.UserRepresentation>"),
                                           @BodyType.When(option = "operation", value = "searchUsers", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.UserRepresentation>"),
                                           @BodyType.When(option = "operation", value = "createRole", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "deleteRole", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getRole", carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.RoleRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateRole", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listRoles", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.RoleRepresentation>"),
                                           @BodyType.When(option = "operation", value = "assignRoleToUser",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "removeRoleFromUser",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getUserRoles", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.RoleRepresentation>"),
                                           @BodyType.When(option = "operation", value = "createGroup", carriers = Carrier.POJO,
                                                          javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteGroup", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getGroup", carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.GroupRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateGroup", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listGroups", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.GroupRepresentation>"),
                                           @BodyType.When(option = "operation", value = "addUserToGroup",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "removeUserFromGroup",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listUserGroups",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.GroupRepresentation>"),
                                           @BodyType.When(option = "operation", value = "createClient", carriers = Carrier.POJO,
                                                          javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteClient", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getClient", carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.ClientRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateClient", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listClients", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.ClientRepresentation>"),
                                           @BodyType.When(option = "operation", value = "resetUserPassword",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "createClientRole",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "deleteClientRole",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getClientRole",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.RoleRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateClientRole",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listClientRoles",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.RoleRepresentation>"),
                                           @BodyType.When(option = "operation", value = "assignClientRoleToUser",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "removeClientRoleFromUser",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listUserSessions",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.UserSessionRepresentation>"),
                                           @BodyType.When(option = "operation", value = "logoutUser", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "logoutAllUsers",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "revokeAccessToken",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "revokeRefreshToken",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "introspectToken",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.Object>"),
                                           @BodyType.When(option = "operation", value = "pushNotBefore",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "createClientScope",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteClientScope",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getClientScope",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.ClientScopeRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateClientScope",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listClientScopes",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.ClientScopeRepresentation>"),
                                           @BodyType.When(option = "operation", value = "createIdentityProvider",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteIdentityProvider",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getIdentityProvider",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.IdentityProviderRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateIdentityProvider",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listIdentityProviders",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.IdentityProviderRepresentation>"),
                                           @BodyType.When(option = "operation", value = "createResource",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteResource",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getResource", carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.authorization.ResourceRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateResource",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listResources",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.authorization.ResourceRepresentation>"),
                                           @BodyType.When(option = "operation", value = "createResourcePolicy",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteResourcePolicy",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getResourcePolicy",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.authorization.PolicyRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateResourcePolicy",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listResourcePolicies",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.authorization.PolicyRepresentation>"),
                                           @BodyType.When(option = "operation", value = "createResourcePermission",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteResourcePermission",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getResourcePermission",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.authorization.PolicyRepresentation"),
                                           @BodyType.When(option = "operation", value = "updateResourcePermission",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listResourcePermissions",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.authorization.PolicyRepresentation>"),
                                           @BodyType.When(option = "operation", value = "evaluatePermission",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.Object>"),
                                           @BodyType.When(option = "operation", value = "getUserAttributes",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.util.List<java.lang.String>>"),
                                           @BodyType.When(option = "operation", value = "setUserAttribute",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "deleteUserAttribute",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getUserCredentials",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.CredentialRepresentation>"),
                                           @BodyType.When(option = "operation", value = "deleteUserCredential",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "sendVerifyEmail",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "sendPasswordResetEmail",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "addRequiredAction",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "removeRequiredAction",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "executeActionsEmail",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getClientSecret",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.CredentialRepresentation"),
                                           @BodyType.When(option = "operation", value = "regenerateClientSecret",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.CredentialRepresentation"),
                                           @BodyType.When(option = "operation", value = "bulkCreateUsers",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.Object>"),
                                           @BodyType.When(option = "operation", value = "bulkDeleteUsers",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.Object>"),
                                           @BodyType.When(option = "operation", value = "bulkAssignRolesToUser",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.Object>"),
                                           @BodyType.When(option = "operation", value = "bulkAssignRoleToUsers",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.Object>"),
                                           @BodyType.When(option = "operation", value = "bulkUpdateUsers",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.Object>"),
                                           @BodyType.When(option = "operation", value = "createOrganization",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "updateOrganization",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "deleteOrganization",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getOrganization",
                                                          carriers = Carrier.POJO,
                                                          javaType = "org.keycloak.representations.idm.OrganizationRepresentation"),
                                           @BodyType.When(option = "operation", value = "listOrganizations",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.OrganizationRepresentation>"),
                                           @BodyType.When(option = "operation", value = "searchOrganizations",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.OrganizationRepresentation>"),
                                           @BodyType.When(option = "operation", value = "addOrganizationMember",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "removeOrganizationMember",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listOrganizationMembers",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.MemberRepresentation>"),
                                           @BodyType.When(option = "operation", value = "linkOrganizationIdentityProvider",
                                                          carriers = Carrier.POJO, javaType = "jakarta.ws.rs.core.Response"),
                                           @BodyType.When(option = "operation", value = "unlinkOrganizationIdentityProvider",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listOrganizationIdentityProviders",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.IdentityProviderRepresentation>"),
                                           @BodyType.When(option = "operation", value = "addFederatedIdentity",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "removeFederatedIdentity",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "getFederatedIdentities",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<org.keycloak.representations.idm.FederatedIdentityRepresentation>") },
                                   description = "Depends on the operation, which the CamelKeycloakOperation header can set:"
                                                 + " get gives the Keycloak representation, such as a UserRepresentation, and"
                                                 + " list and search a List of them. Most create operations, deleteUser and"
                                                 + " some organization operations give the JAX-RS Response, whose status says"
                                                 + " whether it worked (a 409 or 404 is no error); the others"
                                                 + " a confirmation text such as Realm deleted successfully. The bulk"
                                                 + " operations give a Map with total, success, failed and results"))
public class KeycloakEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private Keycloak keycloakClient;

    @UriParam
    private KeycloakConfiguration configuration;

    public KeycloakEndpoint(String uri, Component component, KeycloakConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        KeycloakConsumer consumer = new KeycloakConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KeycloakProducer(this);
    }

    @Override
    public KeycloakComponent getComponent() {
        return (KeycloakComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.getKeycloakClient() != null) {
            keycloakClient = configuration.getKeycloakClient();
        } else {
            keycloakClient = createKeycloakClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getKeycloakClient()) && keycloakClient != null) {
            keycloakClient.close();
        }
        super.doStop();
    }

    public KeycloakConfiguration getConfiguration() {
        return configuration;
    }

    public Keycloak getKeycloakClient() {
        return keycloakClient;
    }

    private Keycloak createKeycloakClient() {
        // Use authRealm for authentication if specified, otherwise use realm
        String authenticationRealm = configuration.getAuthRealm() != null
                ? configuration.getAuthRealm()
                : configuration.getRealm();

        KeycloakBuilder builder = KeycloakBuilder.builder()
                .serverUrl(configuration.getServerUrl())
                .realm(authenticationRealm);

        if (configuration.getAccessToken() != null) {
            // Use pre-obtained access token
            builder.authorization(configuration.getAccessToken());
        } else if (configuration.getUsername() != null && configuration.getPassword() != null) {
            builder.username(configuration.getUsername())
                    .password(configuration.getPassword())
                    .clientId("admin-cli");
        } else if (configuration.getClientId() != null && configuration.getClientSecret() != null) {
            builder.clientId(configuration.getClientId())
                    .clientSecret(configuration.getClientSecret())
                    .grantType("client_credentials");
        } else {
            throw new IllegalArgumentException(
                    "Either accessToken, username/password, or clientId/clientSecret must be provided");
        }

        return builder.build();
    }

    @Override
    public String getServiceUrl() {
        return configuration.getServerUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "keycloak";
    }
}
