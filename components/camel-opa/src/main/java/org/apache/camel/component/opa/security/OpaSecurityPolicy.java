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
import org.apache.camel.component.opa.OpaWasmEvaluator;
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
@Metadata(label = "bean",
          description = "An authorization policy that asks Open Policy Agent whether the exchange may proceed. A deny throws"
                        + " CamelAuthorizationException, so the part of the route it guards does not run.",
          annotations = { "interfaceName=org.apache.camel.spi.AuthorizationPolicy" })
@Configurer(metadataOnly = true)
public class OpaSecurityPolicy implements AuthorizationPolicy {

    @Metadata(label = "advanced", defaultValue = "false")
    private boolean asyncProcessing;
    @Metadata(label = "advanced", defaultValue = "4")
    private int asyncPoolSize = 4;
    @Metadata(label = "advanced", defaultValue = "100")
    private int asyncQueueSize = 100;
    @Metadata(label = "advanced", defaultValue = "30000")
    private long asyncTimeout = 30000;

    public boolean isAsyncProcessing() {
        return asyncProcessing;
    }

    /** Offload authorization with bounded managed resources instead of blocking the routing thread. */
    public void setAsyncProcessing(boolean asyncProcessing) {
        this.asyncProcessing = asyncProcessing;
    }

    public int getAsyncPoolSize() {
        return asyncPoolSize;
    }

    /** Fixed blocking authorization worker count; must be positive. */
    public void setAsyncPoolSize(int asyncPoolSize) {
        this.asyncPoolSize = asyncPoolSize;
    }

    public int getAsyncQueueSize() {
        return asyncQueueSize;
    }

    /** Maximum queued authorization submissions; saturation fails closed rather than running on the routing thread. */
    public void setAsyncQueueSize(int asyncQueueSize) {
        this.asyncQueueSize = asyncQueueSize;
    }

    public long getAsyncTimeout() {
        return asyncTimeout;
    }

    /** Total queued and running authorization deadline in milliseconds; must be positive. */
    public void setAsyncTimeout(long asyncTimeout) {
        this.asyncTimeout = asyncTimeout;
    }

    private static final Logger LOG = LoggerFactory.getLogger(OpaSecurityPolicy.class);

    private static final String WASM_MODE = "wasm";
    private static final String REST_MODE = "rest";
    private static final String DEFAULT_SERVER_URL = "http://localhost:8181";

    @Metadata(defaultValue = DEFAULT_SERVER_URL,
              description = "The base URL of the OPA server, without the /v1/data suffix. Not used in wasm mode.")
    private String serverUrl = DEFAULT_SERVER_URL;
    @Metadata(required = true,
              description = "Path of the Rego rule head to evaluate, relative to the OPA data document, for example"
                            + " authz/orders/allow. It is fixed by the policy: an inbound message cannot select which"
                            + " rule judges it.")
    private String policyPath;
    @Metadata(defaultValue = "allow",
              description = "The key to read the verdict from when the policy returns an object rather than a plain"
                            + " boolean. A dotted path such as result.allow reaches a verdict nested inside the document.")
    private String allowKey = "allow";
    @Metadata(defaultValue = "*",
              description = "Comma-separated list of message header names to send to OPA in the input document. The"
                            + " default sends every header except Authorization, Proxy-Authorization, Cookie and"
                            + " Set-Cookie, which carry a caller credential and are sent only when named here.")
    private String includeHeaders = "*";
    @Metadata(description = "Comma-separated list of exchange property names to send to OPA in the input document, or *"
                            + " for all of them. Empty by default. Use it to hand the policy an identity an earlier"
                            + " authentication step stored as an exchange property, such as CamelKeycloakTokenSubject or"
                            + " CamelSpiffePeerId.")
    private String includeProperties;
    @Metadata(defaultValue = "false",
              description = "Whether to send the message body to OPA as part of the input document.")
    private boolean includeBody;
    @Metadata(label = "security", security = "secret",
              description = "Bearer token sent to the OPA server in the Authorization header, for an OPA instance that"
                            + " has its API authentication enabled. Not used in wasm mode.")
    private String bearerToken;
    @Metadata(label = "security", defaultValue = "false", security = "insecure:dev",
              description = "Whether to let the exchange proceed when the policy decision point is unavailable: the OPA"
                            + " server cannot be reached, times out, or a gateway in front of it answers 502, 503, 504 or"
                            + " 429, or in wasm mode no instance frees up within borrowTimeout. It never applies to an"
                            + " answer: an undefined decision, a rejected request and an error evaluating the policy fail"
                            + " closed even when this is set. Do not enable this in production.")
    private boolean failOpen;
    @Metadata(defaultValue = REST_MODE, enums = "rest,wasm",
              description = "How the policy is evaluated. rest asks a running OPA server over its Data API; wasm"
                            + " evaluates a WebAssembly bundle in-process, with no server involved. serverUrl,"
                            + " bearerToken, connectionTimeout, requestTimeout, sslContextParameters and the readiness"
                            + " check do not apply in wasm mode.")
    private String evaluationMode = REST_MODE;
    @Metadata(description = "The WebAssembly policy to evaluate in wasm mode, as produced by opa build -t wasm: a file:,"
                            + " classpath: or http: location holding either the bundle.tar.gz that opa build emits or a"
                            + " bare .wasm module. Required when evaluationMode is wasm.")
    private String policyBundle;
    @Metadata(description = "The compiled entrypoint to evaluate in wasm mode, fixed when the bundle is built with opa"
                            + " build -e. Defaults to the policy path, which is the name opa build gives it.")
    private String entrypoint;
    @Metadata(label = "advanced", defaultValue = "8",
              description = "How many WebAssembly instances to pool in wasm mode. An instance is not thread-safe, so this"
                            + " bounds how many exchanges are authorized at once.")
    private int poolSize = 8;
    @Metadata(label = "advanced", defaultValue = "30000",
              description = "How long, in milliseconds, an exchange waits for a free WebAssembly instance in wasm mode."
                            + " Running out of time is an evaluation failure rather than a deny: it fails closed, or"
                            + " proceeds when failOpen is set.")
    private long borrowTimeout = 30000;
    @Metadata(label = "advanced",
              description = "An existing OPAClient to use instead of building one from serverUrl, bearerToken and"
                            + " sslContextParameters. No readiness check is registered for it.")
    private OPAClient opaClient;

    @Metadata(label = "advanced", defaultValue = "true",
              description = "Whether to register a readiness check for the OPA server this policy queries. The policy"
                            + " denies every exchange it guards while the server is unreachable, so a route that cannot"
                            + " reach it is not ready.")
    private boolean healthCheckEnabled = true;
    @Metadata(label = "advanced", defaultValue = "10000",
              description = "How long to wait, in milliseconds, for the connection to the OPA server to be established.")
    private long connectionTimeout = 10000;
    @Metadata(label = "advanced", defaultValue = "30000",
              description = "How long to wait, in milliseconds, for the decision once connected. A request that times"
                            + " out is an evaluation failure rather than a deny: it fails closed, or proceeds when"
                            + " failOpen is set.")
    private long requestTimeout = 30000;
    @Metadata(label = "security",
              description = "TLS configuration for the connection to the OPA server, to trust a server whose"
                            + " certificate comes from a private CA or to present a client certificate to one that"
                            + " requires mutual TLS. Takes precedence over useGlobalSslContextParameters.")
    private SSLContextParameters sslContextParameters;
    @Metadata(label = "security", defaultValue = "false",
              description = "Whether to use the global SSL context parameters, the context-wide SSLContextParameters"
                            + " camel-main builds from camel.ssl.*, when sslContextParameters is not set. Not used in"
                            + " wasm mode.")
    private boolean useGlobalSslContextParameters;

    private volatile OpaPolicyEvaluator evaluator;
    private volatile OpaSecurityPolicyHealthCheck healthCheck;
    private volatile boolean ownsClient;
    private volatile SSLContext sslContext;
    private volatile CamelContext camelContext;
    private int activeProcessors;

    public OpaSecurityPolicy() {
    }

    public OpaSecurityPolicy(String serverUrl, String policyPath) {
        this.serverUrl = serverUrl;
        this.policyPath = policyPath;
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
        this.camelContext = route.getCamelContext();
        if (evaluator == null) {
            StringHelper.notEmpty(policyPath, "policyPath", this);
            evaluator = buildEvaluator(route.getCamelContext());
            // a Policy has no stop hook of its own, so the evaluator - its HTTP transport in rest mode, or its
            // WebAssembly instance pool in wasm mode - would outlive the routes it was built for. Registering it as a
            // service hands its close() to the context's shutdown.
            try {
                route.getCamelContext().addService(evaluator);
            } catch (Exception e) {
                throw new RuntimeCamelException("Could not register the evaluator for policy " + policyPath, e);
            }
        }
        // The health check is registered and unregistered from the wrapped processors' lifecycle
        // (onProcessorStart/onProcessorStop), not here: beforeWrap does not run again when a route is merely
        // restarted, so a check registered here would be left behind when the guarded routes stop (CAMEL-24751).
        // In wasm mode nothing sets ownsClient, so registerHealthCheck skips anyway - the policy is evaluated
        // in-process and there is no server to probe (consistent with CAMEL-24743).
    }

    /**
     * Builds the evaluator for the configured {@code evaluationMode}. Both share {@link OpaPolicyEvaluator}, so the
     * decision contract - the {@code CamelOpaDecision} headers and a fail-closed default - is identical whichever
     * engine runs.
     */
    private OpaPolicyEvaluator buildEvaluator(CamelContext camelContext) {
        if (WASM_MODE.equalsIgnoreCase(evaluationMode)) {
            warnIgnoredServerOptions();
            try {
                return OpaWasmEvaluator.create(camelContext, policyBundle, entrypoint, poolSize, borrowTimeout,
                        policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen);
            } catch (RuntimeException e) {
                // the validation messages (policyBundle, poolSize) already read correctly; do not bury them
                throw e;
            } catch (Exception e) {
                throw new RuntimeCamelException("Could not load the wasm policy bundle for policy " + policyPath, e);
            }
        }
        if (!REST_MODE.equalsIgnoreCase(evaluationMode)) {
            throw new IllegalArgumentException(
                    "Unknown evaluationMode '" + evaluationMode + "'; expected one of " + REST_MODE + ", " + WASM_MODE);
        }
        OpaHttpClient transport = null;
        if (opaClient == null) {
            // createClient moved to OpaRestEvaluator when the evaluator became an abstract base
            sslContext = createSslContext(camelContext);
            transport = OpaRestEvaluator.createTransport(bearerToken, connectionTimeout, requestTimeout, sslContext);
            opaClient = OpaRestEvaluator.createClient(serverUrl, transport);
            ownsClient = true;
        }
        return new OpaRestEvaluator(
                opaClient, transport, policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen);
    }

    /**
     * Warns at startup when options that only make sense for the REST engine are set in {@code wasm} mode, matching
     * {@code OpaEndpoint.warnAboutIgnoredServerOptions} so both entry points behave alike. {@code failOpen} is not
     * among them - it still governs a {@code wasm} evaluation failure. {@code serverUrl}, {@code bearerToken} and an
     * injected {@code opaClient} address, authenticate to or replace a server there is none of in {@code wasm} mode.
     */
    private void warnIgnoredServerOptions() {
        if (opaClient != null) {
            LOG.warn("opaClient is ignored when evaluationMode=wasm: the policy is evaluated in-process");
        }
        if (ObjectHelper.isNotEmpty(bearerToken)) {
            LOG.warn("bearerToken is ignored when evaluationMode=wasm: there is no server to authenticate to");
        }
        if (ObjectHelper.isNotEmpty(serverUrl) && !DEFAULT_SERVER_URL.equals(serverUrl)) {
            LOG.warn("serverUrl '{}' is ignored when evaluationMode=wasm: the policy is evaluated in-process", serverUrl);
        }
    }

    /**
     * Registers a readiness check for the OPA server, once per policy however many routes it wraps.
     * <p/>
     * Only for a client this policy built itself from {@code serverUrl}. An injected {@code opaClient} can point
     * anywhere and this policy has no way to ask it where, so probing the configured URL would report on a server it
     * may never talk to - hence {@code ownsClient} rather than a null check on {@code opaClient}, which by the time
     * this runs is set either way.
     */
    private synchronized void registerHealthCheck() {
        if (!healthCheckEnabled || healthCheck != null || !ownsClient || ObjectHelper.isEmpty(serverUrl)
                || camelContext == null) {
            return;
        }
        HealthCheckRegistry registry = HealthCheckRegistry.get(camelContext);
        if (registry == null) {
            return;
        }
        healthCheck = new OpaSecurityPolicyHealthCheck(serverUrl, bearerToken, policyPath, sslContext);
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
        healthCheck.close();
        healthCheck = null;
    }

    /**
     * Called by {@link OpaSecurityProcessor} when a route this policy guards starts. The readiness check is registered
     * on the first start and, after a stop/restart cycle, restored here - {@link #beforeWrap} does not run again when a
     * route is merely restarted.
     */
    synchronized void onProcessorStart() {
        activeProcessors++;
        registerHealthCheck();
    }

    /**
     * Called by {@link OpaSecurityProcessor} when a route this policy guards stops. The check is unregistered once the
     * last guarded route has gone, so a policy shared by several routes keeps its check until all of them stop, and a
     * route reload does not leave a check behind reporting on a policy that no longer enforces anything (CAMEL-24751).
     */
    synchronized void onProcessorStop() {
        if (activeProcessors > 0 && --activeProcessors == 0) {
            unregisterHealthCheck();
        }
    }

    @Override
    public Processor wrap(Route route, final Processor processor) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Securing route {} with OPA policy {}", route.getRouteId(), policyPath);
        }
        return new OpaSecurityProcessor(processor, this);
    }

    org.apache.camel.CamelContext getCamelContext() {
        return camelContext;
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
     * Whether to let the exchange proceed when the policy decision point is unavailable: the OPA server cannot be
     * reached, times out, or a gateway in front of it answers 502, 503, 504 or 429, or in {@code wasm} mode no instance
     * frees up within {@code borrowTimeout}. It never applies to an answer: an undefined decision, a rejected request
     * and an error evaluating the policy fail closed even when this is set. Disabled by default so that an unavailable
     * OPA server denies rather than grants access. Do not enable this in production.
     */
    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public String getEvaluationMode() {
        return evaluationMode;
    }

    /**
     * How the policy is evaluated. {@code rest} (the default) asks a running OPA server; {@code wasm} evaluates a
     * WebAssembly bundle in-process, with no server involved - preferred for a hot path such as authorizing an AI tool
     * call. {@code serverUrl}, {@code bearerToken} and the readiness check do not apply in {@code wasm} mode;
     * {@code failOpen} still governs an unavailable decision point in both.
     */
    public void setEvaluationMode(String evaluationMode) {
        this.evaluationMode = evaluationMode;
    }

    public String getPolicyBundle() {
        return policyBundle;
    }

    /**
     * The WebAssembly policy to evaluate in {@code wasm} mode, as produced by {@code opa build -t wasm}. Accepts a
     * {@code file:}, {@code classpath:} or {@code http:} location holding either the {@code bundle.tar.gz} that
     * {@code opa build} emits or a bare {@code .wasm} module. Required when {@code evaluationMode=wasm}.
     */
    public void setPolicyBundle(String policyBundle) {
        this.policyBundle = policyBundle;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    /**
     * The compiled entrypoint to evaluate in {@code wasm} mode, fixed when the bundle is built with {@code opa build
     * -e}. This is not the same thing as the policy path; it defaults to the policy path, which is the name
     * {@code opa build} gives it.
     */
    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public int getPoolSize() {
        return poolSize;
    }

    /**
     * How many WebAssembly evaluation instances to pool in {@code wasm} mode. They carry mutable state and are not
     * thread-safe, so this bounds how many exchanges are authorized at once; an exchange that arrives when all are busy
     * waits up to {@code borrowTimeout}.
     */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public long getBorrowTimeout() {
        return borrowTimeout;
    }

    /**
     * How long, in milliseconds, an exchange waits for a free WebAssembly instance in {@code wasm} mode before the
     * evaluation fails. That failure is not a deny: it fails closed, or proceeds under {@code failOpen}.
     */
    public void setBorrowTimeout(long borrowTimeout) {
        this.borrowTimeout = borrowTimeout;
    }

    /**
     * The policy is a bean rather than a {@code CamelContextAware} service, so the context comes from the route it is
     * wrapping - which is the only place one is available.
     */
    private SSLContext createSslContext(CamelContext camelContext) {
        // the same precedence as the endpoint: an explicit configuration wins over the context-wide one
        SSLContextParameters parameters = sslContextParameters;
        if (parameters == null && useGlobalSslContextParameters) {
            parameters = camelContext.getSSLContextParameters();
        }
        if (parameters == null) {
            return null;
        }
        try {
            return parameters.createSSLContext(camelContext);
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
     * private CA, and to present a client certificate to a server requiring mutual TLS. Takes precedence over
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
     * {@code useGlobalSslContextParameters} on the {@link org.apache.camel.component.opa.OpaComponent} does not reach a
     * policy, which is a standalone bean rather than an endpoint of that component. Not used in {@code wasm} mode.
     */
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
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
