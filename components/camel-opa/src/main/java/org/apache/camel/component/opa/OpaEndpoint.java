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

/**
 * Evaluate Open Policy Agent (Rego) policies against an Exchange and record the allow/deny decision on it.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "opa", title = "OPA",
             syntax = "opa:policyPath", producerOnly = true, category = { Category.SECURITY },
             headersClass = OpaConstants.class)
public class OpaEndpoint extends DefaultEndpoint {

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

    public OpaEndpoint(final String uri, final Component component, final OpaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        opaClient = configuration.getOpaClient() != null
                ? configuration.getOpaClient()
                : OpaPolicyEvaluator.createClient(configuration.getServerUrl(), configuration.getBearerToken());
        evaluator = new OpaPolicyEvaluator(
                opaClient, policyPath, configuration.getAllowKey(), configuration.getIncludeHeaders(),
                configuration.getIncludeProperties(), configuration.isIncludeBody(), configuration.isFailOpen());
    }

    @Override
    protected void doStop() throws Exception {
        evaluator = null;
        opaClient = null;
        super.doStop();
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
