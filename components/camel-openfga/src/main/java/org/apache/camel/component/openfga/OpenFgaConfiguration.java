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
package org.apache.camel.component.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.SSLContextParameters;

@UriParams
public class OpenFgaConfiguration implements Cloneable {

    static final String DEFAULT_API_URL = "http://localhost:8080";

    @UriParam(defaultValue = DEFAULT_API_URL)
    private String apiUrl = DEFAULT_API_URL;

    @UriParam
    @Metadata(required = true)
    private String storeId;

    @UriParam
    private String authorizationModelId;

    @UriParam
    private String user;

    @UriParam
    private String object;

    @UriParam
    private String relation;

    @UriParam
    private String type;

    @UriParam
    private String relations;

    @UriParam
    private String userFilters;

    @UriParam(enums = "UNSPECIFIED,MINIMIZE_LATENCY,HIGHER_CONSISTENCY")
    private String consistency;

    @UriParam(label = "security", security = "secret")
    private String apiToken;

    @UriParam(label = "security")
    private String clientId;

    @UriParam(label = "security", security = "secret")
    private String clientSecret;

    @UriParam(label = "security")
    private String apiTokenIssuer;

    @UriParam(label = "security")
    private String apiAudience;

    @UriParam(label = "security")
    private String scopes;

    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

    @UriParam(label = "security", security = "insecure:dev")
    private boolean failOpen;

    @UriParam(label = "advanced", defaultValue = "10000", javaType = "java.time.Duration")
    private long connectTimeout = 10000;

    @UriParam(label = "advanced", defaultValue = "10000", javaType = "java.time.Duration")
    private long readTimeout = 10000;

    @UriParam(label = "advanced", defaultValue = "3")
    private int maxRetries = 3;

    @UriParam(label = "advanced", defaultValue = "10")
    private int maxParallelRequests = 10;

    @UriParam(label = "advanced",
              description = "An existing OpenFgaClient to use. When set, every option describing how to reach the"
                            + " server - apiUrl, storeId, the credentials, the timeouts and sslContextParameters - is"
                            + " ignored, because they are baked into the client that was handed over.")
    @Metadata(autowired = true)
    private OpenFgaClient openFgaClient;

    /**
     * The base URL of the OpenFGA HTTP API, without a trailing path. The default assumes OpenFGA running as a sidecar
     * on its standard HTTP port.
     */
    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /**
     * The identifier of the OpenFGA store holding the relationship tuples and the authorization model, as returned by
     * {@code fga store create}.
     * <p/>
     * The store is the relationship graph that judges the exchange, so it comes from the endpoint only and is never
     * taken from a message header.
     */
    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    /**
     * The identifier of the authorization model revision to evaluate against. Leave it empty to use whichever model the
     * store considers latest.
     * <p/>
     * Pin it in production. A store keeps every model it was ever given and "latest" moves the moment somebody writes a
     * new one, so an unpinned endpoint can start answering a different question than the one it was reviewed with -
     * without any change to the route. Pinning also makes a model rollout a deliberate, reviewable configuration
     * change.
     */
    public String getAuthorizationModelId() {
        return authorizationModelId;
    }

    public void setAuthorizationModelId(String authorizationModelId) {
        this.authorizationModelId = authorizationModelId;
    }

    /**
     * The subject to authorize, as an OpenFGA user identifier such as {@code user:anne}. Evaluated as a Simple
     * expression against each exchange, so a literal is used as-is and
     * <code>user:${exchangeProperty.CamelKeycloakTokenSubject}</code> resolves whatever an earlier step established.
     * <p/>
     * Read it from an exchange property rather than a header wherever you can. An exchange property is set by the route
     * itself - by the step that <em>verified</em> the caller - and nothing outside the route can set one. A header, by
     * contrast, is often whatever the caller sent, and an endpoint configured as <code>user:${header.userId}</code>
     * lets the caller choose who to be.
     * <p/>
     * {@code camel-keycloak}'s {@code KeycloakSecurityPolicy} already follows that reasoning: it reads the subject from
     * the {@code CamelKeycloakTokenSubject} exchange property in preference to the header of the same name, its
     * {@code preferPropertyOverHeader} option defaulting to true. Nothing in Camel sets the property for you, so the
     * step that validates the token has to record it - but recording it under that name lets one identity serve both.
     * <p/>
     * An expression that resolves to blank, or to a bare {@code user:} prefix, denies the exchange: an exchange
     * carrying no identity is not authorized, and {@code failOpen} does not apply to it.
     */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /**
     * The object being accessed, as an OpenFGA object identifier such as {@code document:budget}. Evaluated as a Simple
     * expression against each exchange, so <code>document:${header.documentId}</code> names the resource the message is
     * about.
     * <p/>
     * Unlike the subject, taking the object from a header is normal and safe: the caller is entitled to say
     * <em>which</em> resource it wants, and the check is what decides whether it may have it.
     */
    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    /**
     * The relation to demand, such as {@code reader} or {@code owner}. Evaluated as a Simple expression against each
     * exchange, though a literal is what you usually want.
     * <p/>
     * The relation is the permission being demanded, so resolving it from an inbound header lets the caller pick the
     * weakest one the model defines. Keep it literal, or derive it from something the route controls such as
     * <code>${header.CamelHttpMethod}</code>.
     */
    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    /**
     * The object type to enumerate for the {@code listObjects} operation, for example {@code document}. This is a type
     * name from the authorization model, so it is taken literally rather than evaluated.
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Comma-separated list of relations the {@code listRelations} operation asks about, for example
     * {@code reader,writer,owner}. Only the ones the subject actually holds come back.
     */
    public String getRelations() {
        return relations;
    }

    public void setRelations(String relations) {
        this.relations = relations;
    }

    /**
     * Comma-separated list of user filters for the {@code listUsers} operation, naming which kinds of subject to
     * return. An entry is either a type, {@code user}, or a type and a relation, {@code team#member}, to return the
     * usersets holding the relation rather than the individual subjects. Defaults to {@code user}.
     */
    public String getUserFilters() {
        return userFilters;
    }

    public void setUserFilters(String userFilters) {
        this.userFilters = userFilters;
    }

    /**
     * The consistency the query is answered with. OpenFGA's default, {@code MINIMIZE_LATENCY}, may answer from a
     * replica that has not caught up yet, which right after a revoke means a tuple that was deleted can still grant
     * access for a moment. Set {@code HIGHER_CONSISTENCY} on the paths where that window matters, at the cost of
     * latency. Left unset, OpenFGA's own default applies.
     */
    public String getConsistency() {
        return consistency;
    }

    public void setConsistency(String consistency) {
        this.consistency = consistency;
    }

    /**
     * Pre-shared token sent to OpenFGA in the Authorization header, for a server started with
     * {@code --authn-method preshared}.
     */
    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    /**
     * Client identifier for the OAuth 2.0 client-credentials flow, for a server that authenticates through an OIDC
     * provider. Setting it selects that flow, so {@code clientSecret}, {@code apiTokenIssuer} and {@code apiAudience}
     * are then required too.
     */
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Client secret for the OAuth 2.0 client-credentials flow.
     */
    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * The token endpoint the client-credentials flow exchanges its credentials at.
     */
    public String getApiTokenIssuer() {
        return apiTokenIssuer;
    }

    public void setApiTokenIssuer(String apiTokenIssuer) {
        this.apiTokenIssuer = apiTokenIssuer;
    }

    /**
     * The audience to request the access token for in the client-credentials flow.
     */
    public String getApiAudience() {
        return apiAudience;
    }

    public void setApiAudience(String apiAudience) {
        this.apiAudience = apiAudience;
    }

    /**
     * Space-separated scopes to request in the client-credentials flow.
     */
    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    /**
     * TLS configuration for the connection to OpenFGA. Needed to trust a server whose certificate comes from a private
     * CA, and to present a client certificate to a server that requires mutual TLS - a SPIFFE X.509-SVID obtained with
     * {@code camel-spiffe}, for instance, so the workload authenticates to the decision point as itself.
     */
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    /**
     * Whether to let the exchange proceed when OpenFGA could not be asked at all, for example because the server is
     * unreachable. Disabled by default so that an unavailable decision point denies rather than grants access. Do not
     * enable this in production.
     * <p/>
     * It applies to the {@code check} operation and to {@code OpenFgaSecurityPolicy}, the two places where "proceed"
     * has a meaning, and it covers only a failure to obtain a verdict. An exchange that was denied, and an exchange
     * that carried no usable subject or object, are decisions rather than failures and are never turned into an allow
     * by this flag.
     * <p/>
     * The other operations ignore it. A {@code batchCheck} or {@code listObjects} that failed has no safe way to
     * proceed - returning the objects it never managed to filter would be the leak the filtering was there to prevent -
     * so a failure there is reported as an error for the route's own error handling to deal with.
     */
    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    /**
     * How long to wait for the connection to OpenFGA to be established.
     * <p/>
     * The component applies this itself rather than through the SDK's own {@code connectTimeout} setting, which as of
     * openfga-sdk 0.10.1 is accepted and then never read, leaving the connect phase bounded only by the operating
     * system.
     */
    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * How long to wait for one request to OpenFGA to complete once connected. A request that times out is a failure to
     * obtain a verdict rather than a deny, so it fails closed - or proceeds when {@code failOpen} is set.
     */
    public long getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * How many times the SDK retries a request that failed in a way worth retrying, such as a rate limit or a 5xx. Set
     * it to 0 to disable retries; the overall wait a routing thread can spend on one exchange grows with it.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * How many of a {@code batchCheck}'s checks may be in flight at once. The batch is issued as one request per
     * object, so this bounds the load one exchange puts on the server.
     */
    public int getMaxParallelRequests() {
        return maxParallelRequests;
    }

    public void setMaxParallelRequests(int maxParallelRequests) {
        this.maxParallelRequests = maxParallelRequests;
    }

    /**
     * An already-configured {@link OpenFgaClient} to use instead of letting the endpoint build one.
     */
    public OpenFgaClient getOpenFgaClient() {
        return openFgaClient;
    }

    public void setOpenFgaClient(OpenFgaClient openFgaClient) {
        this.openFgaClient = openFgaClient;
    }

    public OpenFgaConfiguration copy() {
        try {
            return (OpenFgaConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
