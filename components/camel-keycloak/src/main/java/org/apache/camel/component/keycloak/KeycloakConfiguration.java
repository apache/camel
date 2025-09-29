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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.keycloak.admin.client.Keycloak;

@UriParams
public class KeycloakConfiguration implements Cloneable {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;

    @UriParam
    @Metadata(autowired = true)
    private Keycloak keycloakClient;

    @UriParam(description = "Keycloak server URL")
    private String serverUrl;

    @UriParam(description = "Keycloak realm")
    private String realm = "master";

    @UriParam(description = "Keycloak client ID")
    private String clientId;

    @UriParam(description = "Keycloak client secret", secret = true)
    private String clientSecret;

    @UriParam(description = "Keycloak username", secret = true)
    private String username;

    @UriParam(description = "Keycloak password", secret = true)
    private String password;

    @UriParam(description = "The operation to perform")
    private KeycloakOperations operation;

    @UriParam(description = "If we want to use a POJO request as body or not")
    private boolean pojoRequest;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Keycloak getKeycloakClient() {
        return keycloakClient;
    }

    /**
     * To use an existing configured Keycloak admin client
     */
    public void setKeycloakClient(Keycloak keycloakClient) {
        this.keycloakClient = keycloakClient;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Keycloak server URL (e.g., http://localhost:8080/auth)
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getRealm() {
        return realm;
    }

    /**
     * Keycloak realm name (default: master)
     */
    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * Keycloak client ID for authentication
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Keycloak client secret for authentication
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Keycloak username for authentication
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Keycloak password for authentication
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public KeycloakOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform.
     *
     * You can configure a default operation on the component level, or the operation as part of the endpoint, or via a
     * message header with the key CamelKeycloakOperation.
     */
    public void setOperation(KeycloakOperations operation) {
        this.operation = operation;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * If we want to use a POJO request as body or not
     */
    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public KeycloakConfiguration copy() {
        try {
            return (KeycloakConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
