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

import com.styra.opa.OPAClient;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.component.opa.OpaPolicyEvaluator;
import org.apache.camel.spi.AuthorizationPolicy;
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

    private volatile OpaPolicyEvaluator evaluator;

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
            if (opaClient == null) {
                opaClient = OpaPolicyEvaluator.createClient(serverUrl, bearerToken);
            }
            evaluator = new OpaPolicyEvaluator(
                    opaClient, policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen);
        }
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
