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

import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.camel.component.openfga.OpenFgaHealthProbe;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.URISupport;

/**
 * Readiness check for the OpenFGA server behind an {@link OpenFgaSecurityPolicy}.
 * <p/>
 * The policy is the stricter of the component's two paths: a denied producer merely records a verdict the route can
 * inspect, while this one throws {@link org.apache.camel.CamelAuthorizationException} and stops the exchange. So an
 * unreachable server here fails every message outright, which is exactly the condition worth surfacing before traffic
 * arrives rather than after.
 */
public class OpenFgaSecurityPolicyHealthCheck extends AbstractHealthCheck {

    private final String apiUrl;
    private final String apiToken;
    private final String storeId;
    private final SSLContext sslContext;

    public OpenFgaSecurityPolicyHealthCheck(String apiUrl, String apiToken, String storeId, String relation,
                                            SSLContext sslContext) {
        // the server, the store and the relation together identify what this policy enforces, so two policies
        // demanding different relations stay distinct; sanitized because the id is published in the health output
        super("camel", "security-policy:openfga-" + URISupport.sanitizeUri(apiUrl + "/" + storeId + "/" + relation));
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.storeId = storeId;
        this.sslContext = sslContext;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        OpenFgaHealthProbe.probe(builder, apiUrl, apiToken, storeId, sslContext);
    }
}
