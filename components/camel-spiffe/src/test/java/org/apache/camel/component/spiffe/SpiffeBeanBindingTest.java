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
package org.apache.camel.component.spiffe;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the SPIFFE beans the way a YAML {@code beans:} entry does: every value a string, a nested mapping a nested map.
 */
class SpiffeBeanBindingTest {

    @Test
    void bindsTheSslContextParametersOwnAndInheritedOptions() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Map<String, Object> serverParameters = new LinkedHashMap<>();
            serverParameters.put("clientAuthentication", "REQUIRE");
            Map<String, Object> protocols = new LinkedHashMap<>();
            protocols.put("secureSocketProtocol[0]", "TLSv1.3");
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("spiffeSocketPath", "unix:///run/spire/agent.sock");
            properties.put("acceptedSpiffeIds", "spiffe://example.org/frontend,spiffe://example.org/batch");
            properties.put("initTimeout", "5000");
            // inherited from SSLContextParameters, which the generated configurer does not cover
            properties.put("serverParameters", serverParameters);
            properties.put("secureSocketProtocols", protocols);
            properties.put("secureSocketProtocol", "TLSv1.3");

            SpiffeSSLContextParameters ssl = new SpiffeSSLContextParameters();
            PropertyBindingSupport.setPropertiesOnTarget(context, ssl, properties);

            assertThat(ssl.getSpiffeSocketPath()).isEqualTo("unix:///run/spire/agent.sock");
            assertThat(ssl.getAcceptedSpiffeIds()).isEqualTo("spiffe://example.org/frontend,spiffe://example.org/batch");
            assertThat(ssl.isAcceptAnySpiffeId()).isFalse();
            assertThat(ssl.getInitTimeout()).isEqualTo(5000L);
            assertThat(ssl.getServerParameters().getClientAuthentication()).isEqualTo("REQUIRE");
            assertThat(ssl.getSecureSocketProtocols().getSecureSocketProtocol()).containsExactly("TLSv1.3");
            assertThat(ssl.getSecureSocketProtocol()).isEqualTo("TLSv1.3");
        }
    }

    @Test
    void bindsTheSecurityPolicyOptions() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("acceptedSpiffeIds", "spiffe://example.org/frontend");
            properties.put("sslSessionHeader", "CamelCustomSSLSession");

            SpiffeSecurityPolicy policy = new SpiffeSecurityPolicy();
            PropertyBindingSupport.setPropertiesOnTarget(context, policy, properties);

            assertThat(policy.getAcceptedSpiffeIds()).isEqualTo("spiffe://example.org/frontend");
            assertThat(policy.isAcceptAnySpiffeId()).isFalse();
            assertThat(policy.getSslSessionHeader()).isEqualTo("CamelCustomSSLSession");

            SpiffeSecurityPolicy acceptAny = new SpiffeSecurityPolicy();
            PropertyBindingSupport.setPropertiesOnTarget(context, acceptAny, Map.of("acceptAnySpiffeId", "true"));

            assertThat(acceptAny.isAcceptAnySpiffeId()).isTrue();
            assertThat(acceptAny.getSslSessionHeader()).isEqualTo(SpiffeSecurityPolicy.DEFAULT_SSL_SESSION_HEADER);
        }
    }
}
