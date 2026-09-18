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

import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.camel.component.opa.OpaHealthProbe;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.URISupport;

/**
 * Readiness check for the OPA server behind an {@link OpaSecurityPolicy}.
 * <p/>
 * The policy is the stricter of the component's two paths: a denied producer merely records a verdict the route can
 * inspect, while this one throws {@link org.apache.camel.CamelAuthorizationException} and stops the exchange. So an
 * unreachable server here fails every message outright, which is exactly the condition worth surfacing before traffic
 * arrives rather than after.
 */
public class OpaSecurityPolicyHealthCheck extends AbstractHealthCheck {

    private final String serverUrl;
    private final String bearerToken;
    private final String policyPath;
    private final SSLContext sslContext;

    public OpaSecurityPolicyHealthCheck(String serverUrl, String bearerToken, String policyPath,
                                        SSLContext sslContext) {
        // serverUrl and policyPath together identify the decision this policy enforces, so two policies pointing at
        // different servers stay distinct; sanitized because the id is published in the health output
        super("camel", "security-policy:opa-" + URISupport.sanitizeUri(serverUrl + "/" + policyPath));
        this.serverUrl = serverUrl;
        this.bearerToken = bearerToken;
        this.policyPath = policyPath;
        this.sslContext = sslContext;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        OpaHealthProbe.probe(builder, serverUrl, bearerToken, policyPath, sslContext);
    }
}
