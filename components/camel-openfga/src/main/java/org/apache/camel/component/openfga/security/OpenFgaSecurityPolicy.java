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
public class OpenFgaSecurityPolicy implements AuthorizationPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFgaSecurityPolicy.class);

    private final OpenFgaConfiguration configuration = new OpenFgaConfiguration();

    private boolean healthCheckEnabled = true;

    private volatile OpenFgaAuthorizer authorizer;
    private volatile OpenFgaSecurityPolicyHealthCheck healthCheck;
    private volatile boolean ownsClient;
    private volatile SSLContext sslContext;
    private volatile CamelContext camelContext;
    private int activeProcessors;

    public OpenFgaSecurityPolicy() {
    }

    public OpenFgaSecurityPolicy(String apiUrl, String storeId) {
        configuration.setApiUrl(apiUrl);
        configuration.setStoreId(storeId);
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        this.camelContext = route.getCamelContext();
        if (authorizer == null) {
            StringHelper.notEmpty(configuration.getStoreId(), "storeId", this);
            StringHelper.notEmpty(configuration.getRelation(), "relation", this);
            StringHelper.notEmpty(configuration.getUser(), "user", this);
            StringHelper.notEmpty(configuration.getObject(), "object", this);
            if (ObjectHelper.isEmpty(configuration.getAuthorizationModelId())) {
                LOG.warn("authorizationModelId is not set on the OpenFGA policy guarding route {}: it will evaluate"
                         + " against store {}'s latest authorization model, which changes as soon as a new model is"
                         + " written. Pin the model id in production.",
                        route.getRouteId(), configuration.getStoreId());
            }
            OpenFgaClient client = configuration.getOpenFgaClient();
            if (client == null) {
                sslContext = createSslContext(route.getCamelContext());
                try {
                    client = OpenFgaClientFactory.createClient(configuration, sslContext);
                } catch (Exception e) {
                    // beforeWrap cannot throw a checked exception, and a policy that could not build its client must
                    // not start a route it would then fail to guard
                    throw new RuntimeCamelException(
                            "Could not build the OpenFGA client for store " + configuration.getStoreId(), e);
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
                || ObjectHelper.isEmpty(configuration.getApiUrl()) || camelContext == null) {
            return;
        }
        HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
        if (registry == null) {
            return;
        }
        healthCheck = new OpenFgaSecurityPolicyHealthCheck(
                configuration.getApiUrl(), configuration.getApiToken(), configuration.getStoreId(),
                configuration.getRelation(), sslContext);
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
                    configuration.getRelation(), configuration.getStoreId());
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
        if (configuration.getSslContextParameters() == null) {
            return null;
        }
        try {
            return configuration.getSslContextParameters().createSSLContext(context);
        } catch (GeneralSecurityException | IOException e) {
            // as above: a policy whose TLS configuration is broken must not start a route that would then talk to
            // OpenFGA over the JVM default trust material instead
            throw new RuntimeCamelException(
                    "Could not build the SSLContext for the OpenFGA policy on store " + configuration.getStoreId(), e);
        }
    }

    public String getApiUrl() {
        return configuration.getApiUrl();
    }

    /**
     * The base URL of the OpenFGA HTTP API.
     */
    public void setApiUrl(String apiUrl) {
        configuration.setApiUrl(apiUrl);
    }

    public String getStoreId() {
        return configuration.getStoreId();
    }

    /**
     * The identifier of the OpenFGA store holding the relationship tuples and the authorization model.
     */
    public void setStoreId(String storeId) {
        configuration.setStoreId(storeId);
    }

    public String getAuthorizationModelId() {
        return configuration.getAuthorizationModelId();
    }

    /**
     * The authorization model revision to evaluate against. Pin it in production: left empty, the store's latest model
     * is used and that moves whenever a new model is written.
     */
    public void setAuthorizationModelId(String authorizationModelId) {
        configuration.setAuthorizationModelId(authorizationModelId);
    }

    public String getUser() {
        return configuration.getUser();
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
        configuration.setUser(user);
    }

    public String getObject() {
        return configuration.getObject();
    }

    /**
     * The object being accessed, as a Simple expression resolving to an OpenFGA object identifier such as
     * <code>document:${header.documentId}</code>. Taking this from a header is normal: the caller may say which
     * resource it wants, and the check decides whether it may have it.
     */
    public void setObject(String object) {
        configuration.setObject(object);
    }

    public String getRelation() {
        return configuration.getRelation();
    }

    /**
     * The relation to demand, such as {@code reader}. Keep it literal: it is the permission being demanded, so
     * resolving it from an inbound header would let the caller pick the weakest one the model defines.
     */
    public void setRelation(String relation) {
        configuration.setRelation(relation);
    }

    public String getContextualTuples() {
        return configuration.getContextualTuples();
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
        configuration.setContextualTuples(contextualTuples);
    }

    public Map<String, Object> getConditionContext() {
        return configuration.getConditionContext();
    }

    /**
     * Context the CEL expression of any conditioned relation this check touches is evaluated with. A map rather than an
     * expression, so values carry the Java type the authorization model declares ({@code int}, {@code bool},
     * {@code timestamp}, {@code ipaddress}) instead of strings the server would reject.
     */
    public void setConditionContext(Map<String, Object> conditionContext) {
        configuration.setConditionContext(conditionContext);
    }

    public String getConsistency() {
        return configuration.getConsistency();
    }

    /**
     * The consistency the check is answered with. Set {@code HIGHER_CONSISTENCY} where the window in which a
     * just-revoked tuple can still grant access matters.
     */
    public void setConsistency(String consistency) {
        configuration.setConsistency(consistency);
    }

    public String getApiToken() {
        return configuration.getApiToken();
    }

    /**
     * Pre-shared token for an OpenFGA server started with {@code --authn-method preshared}.
     */
    public void setApiToken(String apiToken) {
        configuration.setApiToken(apiToken);
    }

    public String getClientId() {
        return configuration.getClientId();
    }

    /**
     * Client identifier for the OAuth 2.0 client-credentials flow.
     */
    public void setClientId(String clientId) {
        configuration.setClientId(clientId);
    }

    public String getClientSecret() {
        return configuration.getClientSecret();
    }

    /**
     * Client secret for the OAuth 2.0 client-credentials flow.
     */
    public void setClientSecret(String clientSecret) {
        configuration.setClientSecret(clientSecret);
    }

    public String getApiTokenIssuer() {
        return configuration.getApiTokenIssuer();
    }

    /**
     * The token endpoint the client-credentials flow exchanges its credentials at.
     */
    public void setApiTokenIssuer(String apiTokenIssuer) {
        configuration.setApiTokenIssuer(apiTokenIssuer);
    }

    public String getApiAudience() {
        return configuration.getApiAudience();
    }

    /**
     * The audience to request the access token for in the client-credentials flow.
     */
    public void setApiAudience(String apiAudience) {
        configuration.setApiAudience(apiAudience);
    }

    public String getScopes() {
        return configuration.getScopes();
    }

    /**
     * Space-separated scopes to request in the OAuth 2.0 client-credentials flow.
     */
    public void setScopes(String scopes) {
        configuration.setScopes(scopes);
    }

    public boolean isFailOpen() {
        return configuration.isFailOpen();
    }

    /**
     * Whether to let the exchange proceed when OpenFGA could not be asked at all. Disabled by default so that an
     * unreachable decision point denies rather than grants access. Do not enable this in production.
     * <p/>
     * It never applies to a deny, nor to an exchange whose subject or object did not resolve: those are decisions, not
     * failures.
     */
    public void setFailOpen(boolean failOpen) {
        configuration.setFailOpen(failOpen);
    }

    public long getConnectTimeout() {
        return configuration.getConnectTimeout();
    }

    /**
     * How long to wait for the connection to OpenFGA to be established.
     */
    public void setConnectTimeout(long connectTimeout) {
        configuration.setConnectTimeout(connectTimeout);
    }

    public long getReadTimeout() {
        return configuration.getReadTimeout();
    }

    /**
     * How long to wait for the check to complete once connected. A request that times out is a failure to obtain a
     * verdict rather than a deny, so the policy denies the exchange unless {@code failOpen} is set.
     */
    public void setReadTimeout(long readTimeout) {
        configuration.setReadTimeout(readTimeout);
    }

    public int getMaxRetries() {
        return configuration.getMaxRetries();
    }

    /**
     * How many times to retry a request that failed in a way worth retrying.
     */
    public void setMaxRetries(int maxRetries) {
        configuration.setMaxRetries(maxRetries);
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration.getSslContextParameters();
    }

    /**
     * TLS configuration for the connection to OpenFGA. Needed to trust a server whose certificate comes from a private
     * CA, and to present a client certificate to a server requiring mutual TLS.
     * <p/>
     * Note: {@code useGlobalSslContextParameters} on the {@code OpenFgaComponent} has no effect here. This policy is a
     * standalone bean and is not bound to any component instance, so the global SSL context cannot be resolved
     * automatically. Set this field explicitly when TLS is required.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        configuration.setSslContextParameters(sslContextParameters);
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
        return configuration.getOpenFgaClient();
    }

    /**
     * An already-configured {@link OpenFgaClient} to use instead of building one.
     */
    public void setOpenFgaClient(OpenFgaClient openFgaClient) {
        configuration.setOpenFgaClient(openFgaClient);
    }
}
