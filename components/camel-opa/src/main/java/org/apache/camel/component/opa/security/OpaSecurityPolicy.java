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
package org.apache.camel.component.opa.security;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import com.styra.opa.OPAClient;
import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.opa.OpaHttpClient;
import org.apache.camel.component.opa.OpaPolicyEvaluator;
import org.apache.camel.component.opa.OpaRestEvaluator;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AuthorizationPolicy} that asks an OPA server whether the exchange may proceed.
 * <p/>
 * Wrap a part of a route with it to enforce a Rego policy without writing the check into the route:
 *
 * <pre>
 * from("platform-http:/orders")
 *         .policy(opaPolicy)
 *         .to("direct:handleOrder");
 * </pre>
 *
 * A deny throws a {@link org.apache.camel.CamelAuthorizationException} so the route stops and the regular
 * {@code onException} machinery applies. The decision is also recorded on the exchange in the {@code CamelOpaDecision}
 * headers.
 */
public class OpaSecurityPolicy implements AuthorizationPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(OpaSecurityPolicy.class);

    private String serverUrl = "http://localhost:8181";
    private String policyPath;
    private String allowKey = "allow";
    private String includeHeaders = "*";
    private String includeProperties;
    private boolean includeBody;
    private String bearerToken;
    private boolean failOpen;
    private OPAClient opaClient;

    private boolean healthCheckEnabled = true;
    private long connectionTimeout = 10000;
    private long requestTimeout = 30000;
    private SSLContextParameters sslContextParameters;

    private volatile OpaPolicyEvaluator evaluator;
    private volatile OpaSecurityPolicyHealthCheck healthCheck;
    private volatile boolean ownsClient;
    private volatile SSLContext sslContext;

    public OpaSecurityPolicy() {
    }

    public OpaSecurityPolicy(String serverUrl, String policyPath) {
        this.serverUrl = serverUrl;
        this.policyPath = policyPath;
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        if (evaluator == null) {
            StringHelper.notEmpty(policyPath, "policyPath", this);
            OpaHttpClient transport = null;
            if (opaClient == null) {
                // createClient moved to OpaRestEvaluator when the evaluator became an abstract base
                sslContext = createSslContext(route.getCamelContext());
                transport = OpaRestEvaluator.createTransport(
                        bearerToken, connectionTimeout, requestTimeout, sslContext);
                opaClient = OpaRestEvaluator.createClient(serverUrl, transport);
                ownsClient = true;
            }
            evaluator = new OpaRestEvaluator(
                    opaClient, transport, policyPath, allowKey, includeHeaders, includeProperties, includeBody,
                    failOpen);
            // a Policy has no stop hook of its own, so the transport would outlive the routes it was built for.
            // Registering the evaluator as a service hands its close() to the context's shutdown
            try {
                route.getCamelContext().addService(evaluator);
            } catch (Exception e) {
                throw new RuntimeCamelException("Could not register the evaluator for policy " + policyPath, e);
            }
        }
        // after validation, so a policy that is missing its policyPath fails without leaving a ".../null" check
        // behind in the registry
        registerHealthCheck(route);
    }

    /**
     * Registers a readiness check for the OPA server, once per policy however many routes it wraps.
     * <p/>
     * Only for a client this policy built itself from {@code serverUrl}. An injected {@code opaClient} can point
     * anywhere and this policy has no way to ask it where, so probing the configured URL would report on a server it
     * may never talk to - hence {@code ownsClient} rather than a null check on {@code opaClient}, which by the time
     * this runs is set either way.
     */
    private void registerHealthCheck(Route route) {
        if (!healthCheckEnabled || healthCheck != null || !ownsClient || ObjectHelper.isEmpty(serverUrl)) {
            return;
        }
        HealthCheckRegistry registry = HealthCheckRegistry.get(route.getCamelContext());
        if (registry == null) {
            return;
        }
        healthCheck = new OpaSecurityPolicyHealthCheck(serverUrl, bearerToken, policyPath, sslContext);
        registry.register(healthCheck);
    }

    @Override
    public Processor wrap(Route route, final Processor processor) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Securing route {} with OPA policy {}", route.getRouteId(), policyPath);
        }
        return new OpaSecurityProcessor(processor, this);
    }

    OpaPolicyEvaluator getEvaluator() {
        return evaluator;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * The base URL of the OPA server, without the {@code /v1/data} suffix.
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getPolicyPath() {
        return policyPath;
    }

    /**
     * Path of the Rego rule head to evaluate, for example {@code authz/orders/allow}.
     */
    public void setPolicyPath(String policyPath) {
        this.policyPath = policyPath;
    }

    public String getAllowKey() {
        return allowKey;
    }

    /**
     * The key to read the verdict from when the policy returns an object rather than a plain boolean.
     */
    public void setAllowKey(String allowKey) {
        this.allowKey = allowKey;
    }

    public String getIncludeHeaders() {
        return includeHeaders;
    }

    /**
     * Comma-separated list of header names to send to OPA, or {@code *} for all of them.
     */
    public void setIncludeHeaders(String includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public String getIncludeProperties() {
        return includeProperties;
    }

    /**
     * Comma-separated list of exchange property names to send to OPA, or {@code *} for all of them. Empty by default.
     * Use it to hand the policy an identity that an earlier authentication step stored as an exchange property.
     */
    public void setIncludeProperties(String includeProperties) {
        this.includeProperties = includeProperties;
    }

    public boolean isIncludeBody() {
        return includeBody;
    }

    /**
     * Whether to send the message body to OPA as part of the input document.
     */
    public void setIncludeBody(boolean includeBody) {
        this.includeBody = includeBody;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    /**
     * Bearer token for an OPA server that has API authentication enabled.
     */
    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    /**
     * Whether to let the exchange proceed when the policy cannot be evaluated at all. Disabled by default so that an
     * unreachable OPA server denies rather than grants access. Do not enable this in production.
     */
    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    /**
     * The policy is a bean rather than a {@code CamelContextAware} service, so the context comes from the route it is
     * wrapping - which is the only place one is available.
     */
    private SSLContext createSslContext(CamelContext camelContext) {
        if (sslContextParameters == null) {
            return null;
        }
        try {
            return sslContextParameters.createSSLContext(camelContext);
        } catch (GeneralSecurityException | IOException e) {
            // beforeWrap cannot throw checked exceptions, and a policy whose TLS configuration is broken must not
            // start a route that would then talk to OPA over the JVM default trust material instead
            throw new RuntimeCamelException("Could not build the SSLContext for policy " + policyPath, e);
        }
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * How long to wait for the connection to the OPA server to be established. The SDK's own transport applies no
     * timeout, so a server that never answers would otherwise park the routing thread rather than letting the policy
     * fail closed.
     */
    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * How long to wait for the decision once connected. A request that times out is an evaluation failure rather than a
     * deny, so the policy denies the exchange unless {@code failOpen} is set.
     */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * TLS configuration for the connection to the OPA server. Needed to trust a server whose certificate comes from a
     * private CA, and to present a client certificate to a server requiring mutual TLS.
     * <p/>
     * Note: {@code useGlobalSslContextParameters} on the {@link org.apache.camel.component.opa.OpaComponent} has no
     * effect here. {@code OpaSecurityPolicy} is a standalone bean and is not bound to any component instance, so the
     * global SSL context cannot be resolved automatically. Set this field explicitly when TLS is required.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    /**
     * Whether to register a readiness check for the OPA server this policy queries. Enabled by default: the policy
     * denies every exchange it guards while the server is unreachable, so a route that is up but cannot reach OPA is
     * not ready. Disable it for a policy whose route should stay ready regardless - for example one wrapped in
     * {@code failOpen} - rather than excluding the check by pattern.
     */
    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    public OPAClient getOpaClient() {
        return opaClient;
    }

    /**
     * An already-configured {@link OPAClient} to use instead of building one from {@code serverUrl}.
     */
    public void setOpaClient(OPAClient opaClient) {
        this.opaClient = opaClient;
    }
}
