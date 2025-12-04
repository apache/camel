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

    @UriParam(
            description =
                    "Keycloak realm, the default is master because usually all the operations are done starting from the master realm",
            defaultValue = "master")
    private String realm = "master";

    @UriParam(
            description =
                    "Keycloak realm to authenticate against. If not specified, the realm parameter is used for authentication. "
                            + "This is useful when you want to authenticate against one realm (e.g., master) but perform operations on another realm.",
            defaultValue = "master")
    private String authRealm;

    @UriParam(description = "Keycloak client ID")
    private String clientId;

    @UriParam(description = "Keycloak client secret", secret = true)
    private String clientSecret;

    @UriParam(description = "Keycloak username", secret = true)
    private String username;

    @UriParam(description = "Keycloak password", secret = true)
    private String password;

    @UriParam(
            description =
                    "Pre-obtained access token for authentication. When provided, this token will be used directly instead of obtaining one through username/password or client credentials flow.",
            secret = true)
    private String accessToken;

    @UriParam(description = "The operation to perform")
    private KeycloakOperations operation;

    @UriParam(description = "If we want to use a POJO request as body or not")
    private boolean pojoRequest;

    @UriParam(description = "Type of events to consume: events or admin-events", defaultValue = "events")
    private String eventType = "events";

    @UriParam(description = "Maximum number of events to retrieve per poll", defaultValue = "100")
    private int maxResults = 100;

    @UriParam(description = "Offset for pagination (first result index)", defaultValue = "0")
    private int first = 0;

    // Consumer filter options - common to both events and admin events
    @UriParam(description = "Filter events by client ID")
    private String client;

    @UriParam(description = "Filter events by user ID")
    private String user;

    @UriParam(description = "Filter events by start date/time in milliseconds since epoch")
    private String dateFrom;

    @UriParam(description = "Filter events by end date/time in milliseconds since epoch")
    private String dateTo;

    @UriParam(description = "Filter events by IP address")
    private String ipAddress;

    // Consumer filter options - specific to regular events
    @UriParam(description = "Filter events by event types (comma-separated list, e.g., LOGIN,LOGOUT)")
    private String types;

    // Consumer filter options - specific to admin events
    @UriParam(description = "Filter admin events by operation types (comma-separated list, e.g., CREATE,UPDATE,DELETE)")
    private String operationTypes;

    @UriParam(description = "Filter admin events by authentication realm")
    private String authRealmFilter;

    @UriParam(description = "Filter admin events by authentication client ID")
    private String authClient;

    @UriParam(description = "Filter admin events by authentication user ID")
    private String authUser;

    @UriParam(description = "Filter admin events by authentication IP address")
    private String authIpAddress;

    @UriParam(description = "Filter admin events by resource path")
    private String resourcePath;

    // Token introspection configuration options
    @UriParam(
            description = "Enable OAuth 2.0 token introspection for real-time token validation. "
                    + "When enabled, tokens are validated by calling Keycloak's introspection endpoint "
                    + "instead of local JWT parsing. This allows detecting revoked tokens before expiration.",
            defaultValue = "false")
    private boolean useTokenIntrospection = false;

    @UriParam(
            description = "Enable caching of token introspection results to reduce API calls to Keycloak",
            defaultValue = "true")
    private boolean introspectionCacheEnabled = true;

    @UriParam(description = "Time-to-live for cached introspection results in seconds", defaultValue = "60")
    private long introspectionCacheTtl = 60;

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

    public String getAuthRealm() {
        return authRealm;
    }

    /**
     * Keycloak realm to authenticate against. If not specified, the realm parameter is used for authentication.
     */
    public void setAuthRealm(String authRealm) {
        this.authRealm = authRealm;
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

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Pre-obtained access token for authentication. When provided, this token will be used directly instead of
     * obtaining one through username/password or client credentials flow.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
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

    public String getEventType() {
        return eventType;
    }

    /**
     * Type of events to consume: 'events' for user events (login, logout, etc.) or 'admin-events' for admin events
     * (user created, role assigned, etc.). Default is 'events'.
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public int getMaxResults() {
        return maxResults;
    }

    /**
     * Maximum number of events to retrieve per poll. Default is 100.
     */
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getFirst() {
        return first;
    }

    /**
     * Offset for pagination (first result index). Default is 0.
     */
    public void setFirst(int first) {
        this.first = first;
    }

    public String getClient() {
        return client;
    }

    /**
     * Filter events by client ID
     */
    public void setClient(String client) {
        this.client = client;
    }

    public String getUser() {
        return user;
    }

    /**
     * Filter events by user ID
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getDateFrom() {
        return dateFrom;
    }

    /**
     * Filter events by start date/time in milliseconds since epoch
     */
    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }

    public String getDateTo() {
        return dateTo;
    }

    /**
     * Filter events by end date/time in milliseconds since epoch
     */
    public void setDateTo(String dateTo) {
        this.dateTo = dateTo;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Filter events by IP address
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getTypes() {
        return types;
    }

    /**
     * Filter events by event types (comma-separated list, e.g., LOGIN,LOGOUT)
     */
    public void setTypes(String types) {
        this.types = types;
    }

    public String getOperationTypes() {
        return operationTypes;
    }

    /**
     * Filter admin events by operation types (comma-separated list, e.g., CREATE,UPDATE,DELETE)
     */
    public void setOperationTypes(String operationTypes) {
        this.operationTypes = operationTypes;
    }

    public String getAuthRealmFilter() {
        return authRealmFilter;
    }

    /**
     * Filter admin events by authentication realm
     */
    public void setAuthRealmFilter(String authRealmFilter) {
        this.authRealmFilter = authRealmFilter;
    }

    public String getAuthClient() {
        return authClient;
    }

    /**
     * Filter admin events by authentication client ID
     */
    public void setAuthClient(String authClient) {
        this.authClient = authClient;
    }

    public String getAuthUser() {
        return authUser;
    }

    /**
     * Filter admin events by authentication user ID
     */
    public void setAuthUser(String authUser) {
        this.authUser = authUser;
    }

    public String getAuthIpAddress() {
        return authIpAddress;
    }

    /**
     * Filter admin events by authentication IP address
     */
    public void setAuthIpAddress(String authIpAddress) {
        this.authIpAddress = authIpAddress;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    /**
     * Filter admin events by resource path
     */
    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public boolean isUseTokenIntrospection() {
        return useTokenIntrospection;
    }

    /**
     * Enable OAuth 2.0 token introspection for real-time token validation. When enabled, tokens are validated by
     * calling Keycloak's introspection endpoint instead of local JWT parsing. This allows detecting revoked tokens
     * before expiration.
     */
    public void setUseTokenIntrospection(boolean useTokenIntrospection) {
        this.useTokenIntrospection = useTokenIntrospection;
    }

    public boolean isIntrospectionCacheEnabled() {
        return introspectionCacheEnabled;
    }

    /**
     * Enable caching of token introspection results to reduce API calls to Keycloak
     */
    public void setIntrospectionCacheEnabled(boolean introspectionCacheEnabled) {
        this.introspectionCacheEnabled = introspectionCacheEnabled;
    }

    public long getIntrospectionCacheTtl() {
        return introspectionCacheTtl;
    }

    /**
     * Time-to-live for cached introspection results in seconds
     */
    public void setIntrospectionCacheTtl(long introspectionCacheTtl) {
        this.introspectionCacheTtl = introspectionCacheTtl;
    }

    public KeycloakConfiguration copy() {
        try {
            return (KeycloakConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
