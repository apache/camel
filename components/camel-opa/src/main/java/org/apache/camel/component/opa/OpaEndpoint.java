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
package org.apache.camel.component.opa;

import javax.net.ssl.SSLContext;

import com.styra.opa.OPAClient;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;

/**
 * Evaluate Open Policy Agent (Rego) policies against an Exchange and record the allow/deny decision on it.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "opa", title = "OPA",
             syntax = "opa:policyPath", producerOnly = true, category = { Category.SECURITY },
             headersClass = OpaConstants.class)
public class OpaEndpoint extends DefaultEndpoint {

    private static final String WASM_MODE = "wasm";
    private static final String REST_MODE = "rest";

    @UriPath(description = "Path of the Rego rule head to evaluate, relative to the OPA data document. For a rule"
                           + " named allow in a policy declaring package authz.orders, this is authz/orders/allow."
                           + " The path is taken from the endpoint only: it is deliberately not overridable by a"
                           + " message header, so that an inbound message cannot select which policy judges it.")
    @Metadata(required = true)
    private String policyPath;

    @UriParam
    private OpaConfiguration configuration;

    private OPAClient opaClient;
    private volatile OpaPolicyEvaluator evaluator;
    private volatile SSLContext sslContext;

    public OpaEndpoint(final String uri, final Component component, final OpaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String mode = configuration.getEvaluationMode();
        if (WASM_MODE.equalsIgnoreCase(mode)) {
            evaluator = createWasmEvaluator();
        } else if (!REST_MODE.equalsIgnoreCase(mode)) {
            // silently falling back to rest would leave a typo'd mode running against a server while quietly
            // ignoring policyBundle, which is a miserable thing to debug in production
            throw new IllegalArgumentException(
                    "Unknown evaluationMode '" + mode + "'; expected one of " + REST_MODE + ", " + WASM_MODE);
        } else {
            if (configuration.getOpaClient() != null) {
                opaClient = configuration.getOpaClient();
                evaluator = new OpaRestEvaluator(
                        opaClient, null, policyPath, configuration.getAllowKey(), configuration.getIncludeHeaders(),
                        configuration.getIncludeProperties(), configuration.isIncludeBody(),
                        configuration.isFailOpen());
            } else {
                sslContext = createSslContext();
                OpaHttpClient transport = OpaRestEvaluator.createTransport(
                        configuration.getBearerToken(),
                        configuration.getConnectionTimeout(), configuration.getRequestTimeout(),
                        sslContext);
                opaClient = OpaRestEvaluator.createClient(configuration.getServerUrl(), transport);
                evaluator = new OpaRestEvaluator(
                        opaClient, transport, policyPath, configuration.getAllowKey(),
                        configuration.getIncludeHeaders(),
                        configuration.getIncludeProperties(), configuration.isIncludeBody(),
                        configuration.isFailOpen());
            }
        }
    }

    /**
     * Resolves the endpoint's TLS configuration, falling back to the context's global one when the component opts in.
     */
    private SSLContext createSslContext() throws Exception {
        SSLContextParameters ssl = configuration.getSslContextParameters();
        if (ssl == null) {
            ssl = getComponent().retrieveGlobalSslContextParameters();
        }
        return ssl != null ? ssl.createSSLContext(getCamelContext()) : null;
    }

    /**
     * The TLS configuration the decision call resolved to, so the producer's readiness check probes the server the same
     * way rather than failing a handshake the decision call passes.
     */
    SSLContext getSslContext() {
        return sslContext;
    }

    private OpaPolicyEvaluator createWasmEvaluator() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getPolicyBundle())) {
            throw new IllegalArgumentException(
                    "policyBundle is required when evaluationMode=wasm; build one with"
                                               + " opa build -t wasm -e <entrypoint> <policy.rego>");
        }
        if (configuration.getPoolSize() < 1) {
            // OpaPolicyPool.create rejects this too, but as "maxSize must be positive" - naming its own parameter
            // rather than the option the operator set, on a component where poolSize is the only pool they see
            throw new IllegalArgumentException(
                    "poolSize must be at least 1 when evaluationMode=wasm, was " + configuration.getPoolSize());
        }
        // the entrypoint is fixed at build time and is not the same thing as a data path, but opa build names it
        // after the rule, so the policy path is the right default
        String entrypoint = ObjectHelper.isNotEmpty(configuration.getEntrypoint())
                ? configuration.getEntrypoint() : policyPath;
        OpaWasmEvaluator.Bundle bundle
                = OpaWasmEvaluator.loadPolicy(getCamelContext(), configuration.getPolicyBundle());
        return new OpaWasmEvaluator(
                bundle.wasm(), bundle.data(), entrypoint, configuration.getPoolSize(),
                configuration.getBorrowTimeout(), policyPath, configuration.getAllowKey(),
                configuration.getIncludeHeaders(), configuration.getIncludeProperties(),
                configuration.isIncludeBody(), configuration.isFailOpen());
    }

    @Override
    protected void doStop() throws Exception {
        if (evaluator instanceof AutoCloseable closeable) {
            closeable.close();
        }
        evaluator = null;
        opaClient = null;
        super.doStop();
    }

    @Override
    public OpaComponent getComponent() {
        return (OpaComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new OpaProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public String getPolicyPath() {
        return policyPath;
    }

    public void setPolicyPath(String policyPath) {
        this.policyPath = policyPath;
    }

    /**
     * The endpoint configuration.
     */
    public OpaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OpaConfiguration configuration) {
        this.configuration = configuration;
    }

    OpaPolicyEvaluator getEvaluator() {
        return evaluator;
    }
}
