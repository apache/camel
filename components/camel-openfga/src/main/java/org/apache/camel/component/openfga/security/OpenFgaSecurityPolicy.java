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
package org.apache.camel.component.openfga.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

import javax.net.ssl.SSLContext;

import dev.openfga.sdk.api.client.OpenFgaClient;
import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.openfga.OpenFgaAuthorizer;
import org.apache.camel.component.openfga.OpenFgaClientFactory;
import org.apache.camel.component.openfga.OpenFgaConfiguration;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AuthorizationPolicy} that asks OpenFGA whether the exchange may proceed.
 * <p/>
 * Wrap a part of a route with it to enforce a relationship check without writing the check into the route:
 *
 * <pre>
 * OpenFgaSecurityPolicy policy = new OpenFgaSecurityPolicy();
 * policy.setStoreId(storeId);
 * policy.setAuthorizationModelId(modelId);
 * policy.setRelation("reader");
 * policy.setUser("user:${exchangeProperty.CamelKeycloakTokenSubject}");
 * policy.setObject("document:${header.documentId}");
 *
 * from("platform-http:/documents")
 *         .policy(policy)
 *         .to("direct:serveDocument");
 * </pre>
 *
 * A deny throws a {@link org.apache.camel.CamelAuthorizationException} so the route stops and the regular
 * {@code onException} machinery applies. The decision is also recorded on the exchange in the {@code CamelOpenFga}
 * headers.
 */
@Metadata(label = "bean",
          description = "An authorization policy that asks OpenFGA whether a subject has a relation to an object before the"
                        + " part of the route it guards runs. A deny throws CamelAuthorizationException.",
          annotations = { "interfaceName=org.apache.camel.spi.AuthorizationPolicy" })
@Configurer(metadataOnly = true)
public class OpenFgaSecurityPolicy implements AuthorizationPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFgaSecurityPolicy.class);

    // the defaults of the endpoint options this policy shares, so that the two cannot drift apart
    private static final OpenFgaConfiguration DEFAULTS = new OpenFgaConfiguration();

    @Metadata(defaultValue = "http://localhost:8080", description = "The base URL of the OpenFGA HTTP API.")
    private String apiUrl = DEFAULTS.getApiUrl();
    @Metadata(required = true,
              description = "The identifier of the OpenFGA store holding the relationship tuples and the authorization"
                            + " model.")
    private String storeId;
    @Metadata(description = "The authorization model revision to evaluate against. Pin it in production: left empty, the"
                            + " store's latest model is used, and that moves whenever a new model is written.")
    private String authorizationModelId;
    @Metadata(required = true,
              description = "The subject to authorize, as a Simple expression resolving to an OpenFGA user identifier"
                            + " such as user:${exchangeProperty.CamelKeycloakTokenSubject}. Read it from an exchange"
                            + " property the step that verified the caller set, rather than from a header the caller sent."
                            + " An exchange whose subject resolves to blank is denied.")
    private String user;
    @Metadata(required = true,
              description = "The object being accessed, as a Simple expression resolving to an OpenFGA object"
                            + " identifier such as document:${header.documentId}. Taking it from a header is normal: the"
                            + " caller may say which resource it wants, and the check decides whether it may have it.")
    private String object;
    @Metadata(required = true,
              description = "The relation to demand, such as reader. Keep it literal: it is the permission being"
                            + " demanded, so resolving it from an inbound header would let the caller pick the weakest one"
                            + " the model defines.")
    private String relation;
    @Metadata(description = "Relationship tuples supplied for the duration of one check and never stored, as"
                            + " semicolon-separated user,relation,object triples, each part a Simple expression. A"
                            + " contextual tuple grants exactly like a stored one, so derive it from something the route"
                            + " established, never from caller input.")
    private String contextualTuples;
    @Metadata(description = "Context the CEL expression of any conditioned relation the check touches is evaluated with,"
                            + " as a map of parameter values. A value written in YAML or a properties file is a string,"
                            + " which OpenFGA rejects for a bool parameter: give that one as #valueAs(boolean):true.")
    private Map<String, Object> conditionContext;
    @Metadata(enums = "UNSPECIFIED,MINIMIZE_LATENCY,HIGHER_CONSISTENCY",
              description = "The consistency the check is answered with. Set HIGHER_CONSISTENCY where the window in which"
                            + " a just-revoked tuple can still grant access matters.")
    private String consistency;
    @Metadata(label = "security", security = "secret",
              description = "Pre-shared token sent to OpenFGA in the Authorization header, for a server started with"
                            + " --authn-method preshared.")
    private String apiToken;
    @Metadata(label = "security",
              description = "Client identifier for the OAuth 2.0 client-credentials flow. Setting it selects that flow,"
                            + " so clientSecret, apiTokenIssuer and apiAudience are then required too.")
    private String clientId;
    @Metadata(label = "security", security = "secret",
              description = "Client secret for the OAuth 2.0 client-credentials flow.")
    private String clientSecret;
    @Metadata(label = "security",
              description = "The token endpoint the client-credentials flow exchanges its credentials at.")
    private String apiTokenIssuer;
    @Metadata(label = "security",
              description = "The audience to request the access token for in the client-credentials flow.")
    private String apiAudience;
    @Metadata(label = "security", description = "Space-separated scopes to request in the client-credentials flow.")
    private String scopes;
    @Metadata(label = "security", defaultValue = "false", security = "insecure:dev",
              description = "Whether to let the exchange proceed when OpenFGA could not be asked at all, for example"
                            + " because the server is unreachable. It never applies to a deny, nor to an exchange whose"
                            + " subject or object did not resolve. Do not enable this in production.")
    private boolean failOpen;
    @Metadata(label = "advanced", defaultValue = "10000",
              description = "How long to wait, in milliseconds, for the connection to OpenFGA to be established.")
    private long connectTimeout = DEFAULTS.getConnectTimeout();
    @Metadata(label = "advanced", defaultValue = "10000",
              description = "How long to wait, in milliseconds, for the check to complete once connected. A request that"
                            + " times out is a failure to obtain a verdict rather than a deny, so the exchange is denied"
                            + " unless failOpen is set.")
    private long readTimeout = DEFAULTS.getReadTimeout();
    @Metadata(label = "advanced", defaultValue = "3",
              description = "How many times to retry a request that failed in a way worth retrying, such as a rate limit"
                            + " or a 5xx.")
    private int maxRetries = DEFAULTS.getMaxRetries();
    @Metadata(label = "security",
              description = "TLS configuration for the connection to OpenFGA, to trust a server whose certificate comes"
                            + " from a private CA or to present a client certificate to one that requires mutual TLS."
                            + " Takes precedence over useGlobalSslContextParameters.")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false",
              description = "Whether to use the global SSL context parameters, the context-wide SSLContextParameters"
                            + " camel-main builds from camel.ssl.*, when sslContextParameters is not set.")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "advanced", defaultValue = "true",
              description = "Whether to register a readiness check for the OpenFGA server this policy queries. The policy"
                            + " denies every exchange it guards while the server is unreachable, so a route that cannot"
                            + " reach it is not ready.")
    private boolean healthCheckEnabled = true;
    @Metadata(label = "advanced",
              description = "An existing OpenFgaClient to use instead of building one from apiUrl, the credentials and"
                            + " sslContextParameters. No readiness check is registered for it.")
    private OpenFgaClient openFgaClient;

    private volatile OpenFgaAuthorizer authorizer;
    private volatile OpenFgaSecurityPolicyHealthCheck healthCheck;
    private volatile boolean ownsClient;
    private volatile SSLContext sslContext;
    private volatile CamelContext camelContext;
    private int activeProcessors;

    public OpenFgaSecurityPolicy() {
    }

    public OpenFgaSecurityPolicy(String apiUrl, String storeId) {
        this.apiUrl = apiUrl;
        this.storeId = storeId;
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        this.camelContext = route.getCamelContext();
        if (authorizer == null) {
            StringHelper.notEmpty(storeId, "storeId", this);
            StringHelper.notEmpty(relation, "relation", this);
            StringHelper.notEmpty(user, "user", this);
            StringHelper.notEmpty(object, "object", this);
            if (ObjectHelper.isEmpty(authorizationModelId)) {
                LOG.warn("authorizationModelId is not set on the OpenFGA policy guarding route {}: it will evaluate"
                         + " against store {}'s latest authorization model, which changes as soon as a new model is"
                         + " written. Pin the model id in production.",
                        route.getRouteId(), storeId);
            }
            OpenFgaConfiguration configuration = toConfiguration();
            OpenFgaClient client = openFgaClient;
            if (client == null) {
                sslContext = createSslContext(route.getCamelContext());
                try {
                    client = OpenFgaClientFactory.createClient(configuration, sslContext);
                } catch (Exception e) {
                    // beforeWrap cannot throw a checked exception, and a policy that could not build its client must
                    // not start a route it would then fail to guard
                    throw new RuntimeCamelException(
                            "Could not build the OpenFGA client for store " + storeId, e);
                }
                ownsClient = true;
            }
            authorizer = new OpenFgaAuthorizer(client, configuration, route.getCamelContext());
        }
        // The health check is registered and unregistered from the wrapped processors' lifecycle
        // (onProcessorStart/onProcessorStop), not here: beforeWrap does not run again when a route is merely
        // restarted, so a check registered here would be left behind when the guarded routes stop (CAMEL-24751).
    }

    /**
     * Registers a readiness check for the OpenFGA server, once per policy however many routes it wraps.
     * <p/>
     * Only for a client this policy built itself. An injected {@code openFgaClient} can point anywhere and this policy
     * has no way to ask it where, so probing the configured URL would report on a server it may never talk to - hence
     * {@code ownsClient} rather than a null check on the client, which by the time this runs is set either way.
     */
    private synchronized void registerHealthCheck() {
        if (!healthCheckEnabled || healthCheck != null || !ownsClient
                || ObjectHelper.isEmpty(apiUrl) || camelContext == null) {
            return;
        }
        HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
        if (registry == null) {
            return;
        }
        healthCheck = new OpenFgaSecurityPolicyHealthCheck(apiUrl, apiToken, storeId, relation, sslContext);
        registry.register(healthCheck);
    }

    private synchronized void unregisterHealthCheck() {
        if (healthCheck == null || camelContext == null) {
            return;
        }
        HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
        if (registry != null) {
            registry.unregister(healthCheck);
        }
        healthCheck = null;
    }

    /**
     * Called by {@link OpenFgaSecurityProcessor} when a route this policy guards starts. The readiness check is
     * registered on the first start and, after a stop/restart cycle, restored here - {@link #beforeWrap} does not run
     * again when a route is merely restarted.
     */
    synchronized void onProcessorStart() {
        activeProcessors++;
        registerHealthCheck();
    }

    /**
     * Called by {@link OpenFgaSecurityProcessor} when a route this policy guards stops. The check is unregistered once
     * the last guarded route has gone, so a policy shared by several routes keeps its check until all of them stop, and
     * a route reload does not leave a check behind reporting on a policy that no longer enforces anything
     * (CAMEL-24751).
     */
    synchronized void onProcessorStop() {
        if (activeProcessors > 0 && --activeProcessors == 0) {
            unregisterHealthCheck();
        }
    }

    @Override
    public Processor wrap(Route route, final Processor processor) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Securing route {} with OpenFGA relation {} in store {}", route.getRouteId(),
                    relation, storeId);
        }
        return new OpenFgaSecurityProcessor(processor, this);
    }

    OpenFgaAuthorizer getAuthorizer() {
        return authorizer;
    }

    /**
     * The policy is a bean rather than a {@code CamelContextAware} service, so the context comes from the route it is
     * wrapping - which is the only place one is available.
     */
    private SSLContext createSslContext(CamelContext context) {
        // the same precedence as the endpoint: an explicit configuration wins over the context-wide one
        SSLContextParameters parameters = sslContextParameters;
        if (parameters == null && useGlobalSslContextParameters) {
            parameters = context.getSSLContextParameters();
        }
        if (parameters == null) {
            return null;
        }
        try {
            return parameters.createSSLContext(context);
        } catch (GeneralSecurityException | IOException e) {
            // as above: a policy whose TLS configuration is broken must not start a route that would then talk to
            // OpenFGA over the JVM default trust material instead
            throw new RuntimeCamelException(
                    "Could not build the SSLContext for the OpenFGA policy on store " + storeId, e);
        }
    }

    /**
     * The endpoint configuration the client and the authorizer are built from, holding this policy's options. An option
     * this method does not copy would be accepted and then silently ignored, which
     * {@code OpenFgaSecurityPolicyOptionsTest} guards against.
     */
    OpenFgaConfiguration toConfiguration() {
        OpenFgaConfiguration answer = new OpenFgaConfiguration();
        answer.setApiUrl(apiUrl);
        answer.setStoreId(storeId);
        answer.setAuthorizationModelId(authorizationModelId);
        answer.setUser(user);
        answer.setObject(object);
        answer.setRelation(relation);
        answer.setContextualTuples(contextualTuples);
        answer.setConditionContext(conditionContext);
        answer.setConsistency(consistency);
        answer.setApiToken(apiToken);
        answer.setClientId(clientId);
        answer.setClientSecret(clientSecret);
        answer.setApiTokenIssuer(apiTokenIssuer);
        answer.setApiAudience(apiAudience);
        answer.setScopes(scopes);
        answer.setFailOpen(failOpen);
        answer.setConnectTimeout(connectTimeout);
        answer.setReadTimeout(readTimeout);
        answer.setMaxRetries(maxRetries);
        answer.setSslContextParameters(sslContextParameters);
        answer.setOpenFgaClient(openFgaClient);
        return answer;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * The base URL of the OpenFGA HTTP API.
     */
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getStoreId() {
        return storeId;
    }

    /**
     * The identifier of the OpenFGA store holding the relationship tuples and the authorization model.
     */
    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getAuthorizationModelId() {
        return authorizationModelId;
    }

    /**
     * The authorization model revision to evaluate against. Pin it in production: left empty, the store's latest model
     * is used and that moves whenever a new model is written.
     */
    public void setAuthorizationModelId(String authorizationModelId) {
        this.authorizationModelId = authorizationModelId;
    }

    public String getUser() {
        return user;
    }

    /**
     * The subject to authorize, as a Simple expression resolving to an OpenFGA user identifier such as
     * <code>user:${exchangeProperty.CamelKeycloakTokenSubject}</code>.
     * <p/>
     * Read it from an exchange property rather than a header wherever you can: a property is set by the route itself,
     * by the step that verified the caller, while a header is often whatever the caller sent.
     * {@code CamelKeycloakTokenSubject} is the name worth using - {@code camel-keycloak}'s own security policy reads
     * the subject from that property in preference to the header - though the step that validates the token is what has
     * to record it. An exchange whose subject resolves to blank is denied.
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getObject() {
        return object;
    }

    /**
     * The object being accessed, as a Simple expression resolving to an OpenFGA object identifier such as
     * <code>document:${header.documentId}</code>. Taking this from a header is normal: the caller may say which
     * resource it wants, and the check decides whether it may have it.
     */
    public void setObject(String object) {
        this.object = object;
    }

    public String getRelation() {
        return relation;
    }

    /**
     * The relation to demand, such as {@code reader}. Keep it literal: it is the permission being demanded, so
     * resolving it from an inbound header would let the caller pick the weakest one the model defines.
     */
    public void setRelation(String relation) {
        this.relation = relation;
    }

    public String getContextualTuples() {
        return contextualTuples;
    }

    /**
     * Relationship tuples supplied for the duration of one check and never stored, as semicolon-separated
     * {@code user,relation,object} triples. Each part is a Simple expression, evaluated per exchange.
     * <p/>
     * Safe here for the same reason it is safe on an endpoint, and for no other: the value comes from whoever
     * configured the policy, never from the message. A contextual tuple is read exactly like a stored one, so one built
     * from caller input would let the caller assert the relationship being demanded - derive it from something the
     * route established, such as a claim an earlier authentication step put on an exchange property.
     */
    public void setContextualTuples(String contextualTuples) {
        this.contextualTuples = contextualTuples;
    }

    public Map<String, Object> getConditionContext() {
        return conditionContext;
    }

    /**
     * Context the CEL expression of any conditioned relation this check touches is evaluated with. A map rather than an
     * expression, so values carry the Java type the authorization model declares ({@code int}, {@code bool},
     * {@code timestamp}, {@code ipaddress}) instead of strings the server would reject.
     */
    public void setConditionContext(Map<String, Object> conditionContext) {
        this.conditionContext = conditionContext;
    }

    public String getConsistency() {
        return consistency;
    }

    /**
     * The consistency the check is answered with. Set {@code HIGHER_CONSISTENCY} where the window in which a
     * just-revoked tuple can still grant access matters.
     */
    public void setConsistency(String consistency) {
        this.consistency = consistency;
    }

    public String getApiToken() {
        return apiToken;
    }

    /**
     * Pre-shared token for an OpenFGA server started with {@code --authn-method preshared}.
     */
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * Client identifier for the OAuth 2.0 client-credentials flow.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Client secret for the OAuth 2.0 client-credentials flow.
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getApiTokenIssuer() {
        return apiTokenIssuer;
    }

    /**
     * The token endpoint the client-credentials flow exchanges its credentials at.
     */
    public void setApiTokenIssuer(String apiTokenIssuer) {
        this.apiTokenIssuer = apiTokenIssuer;
    }

    public String getApiAudience() {
        return apiAudience;
    }

    /**
     * The audience to request the access token for in the client-credentials flow.
     */
    public void setApiAudience(String apiAudience) {
        this.apiAudience = apiAudience;
    }

    public String getScopes() {
        return scopes;
    }

    /**
     * Space-separated scopes to request in the OAuth 2.0 client-credentials flow.
     */
    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    /**
     * Whether to let the exchange proceed when OpenFGA could not be asked at all. Disabled by default so that an
     * unreachable decision point denies rather than grants access. Do not enable this in production.
     * <p/>
     * It never applies to a deny, nor to an exchange whose subject or object did not resolve: those are decisions, not
     * failures.
     */
    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * How long to wait for the connection to OpenFGA to be established.
     */
    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    /**
     * How long to wait for the check to complete once connected. A request that times out is a failure to obtain a
     * verdict rather than a deny, so the policy denies the exchange unless {@code failOpen} is set.
     */
    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * How many times to retry a request that failed in a way worth retrying.
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * TLS configuration for the connection to OpenFGA. Needed to trust a server whose certificate comes from a private
     * CA, and to present a client certificate to a server requiring mutual TLS. Takes precedence over
     * {@code useGlobalSslContextParameters}.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    /**
     * Whether to use the global SSL context parameters, the context-wide {@link SSLContextParameters} that camel-main
     * builds from {@code camel.ssl.*}, when {@code sslContextParameters} is not set. This is the policy's own option:
     * {@code useGlobalSslContextParameters} on the {@code OpenFgaComponent} does not reach a policy, which is a
     * standalone bean rather than an endpoint of that component.
     */
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    /**
     * Whether to register a readiness check for the OpenFGA server this policy queries. Enabled by default: the policy
     * denies every exchange it guards while the server is unreachable, so a route that is up but cannot reach OpenFGA
     * is not ready. Disable it for a policy whose route should stay ready regardless - one wrapped in {@code failOpen},
     * for instance - rather than excluding the check by pattern.
     */
    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    public OpenFgaClient getOpenFgaClient() {
        return openFgaClient;
    }

    /**
     * An already-configured {@link OpenFgaClient} to use instead of building one.
     */
    public void setOpenFgaClient(OpenFgaClient openFgaClient) {
        this.openFgaClient = openFgaClient;
    }
}
