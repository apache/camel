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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the policy the way a YAML {@code beans:} entry does, where every value arrives as a string.
 */
class OpaSecurityPolicyBindingTest {

    @Test
    void bindsTheOptionsAsAYamlBeanDefinitionGivesThem() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            SSLContextParameters tls = new SSLContextParameters();
            context.getRegistry().bind("tls", tls);
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("serverUrl", "https://opa:8181");
            properties.put("policyPath", "authz/orders/allow");
            properties.put("allowKey", "result.allow");
            properties.put("includeHeaders", "X-Tenant,Content-Type");
            properties.put("includeProperties", "CamelSpiffePeerId");
            properties.put("includeBody", "true");
            properties.put("bearerToken", "s3cr3t");
            properties.put("failOpen", "false");
            properties.put("connectionTimeout", "2000");
            properties.put("requestTimeout", "3000");
            properties.put("healthCheckEnabled", "false");
            properties.put("sslContextParameters", "#bean:tls");

            OpaSecurityPolicy policy = new OpaSecurityPolicy();
            PropertyBindingSupport.setPropertiesOnTarget(context, policy, properties);

            assertThat(policy.getServerUrl()).isEqualTo("https://opa:8181");
            assertThat(policy.getPolicyPath()).isEqualTo("authz/orders/allow");
            assertThat(policy.getAllowKey()).isEqualTo("result.allow");
            assertThat(policy.getIncludeHeaders()).isEqualTo("X-Tenant,Content-Type");
            assertThat(policy.getIncludeProperties()).isEqualTo("CamelSpiffePeerId");
            assertThat(policy.isIncludeBody()).isTrue();
            assertThat(policy.getBearerToken()).isEqualTo("s3cr3t");
            assertThat(policy.isFailOpen()).isFalse();
            assertThat(policy.getConnectionTimeout()).isEqualTo(2000L);
            assertThat(policy.getRequestTimeout()).isEqualTo(3000L);
            assertThat(policy.isHealthCheckEnabled()).isFalse();
            assertThat(policy.getSslContextParameters()).isSameAs(tls);
        }
    }

    @Test
    void bindsTheWasmOptions() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("policyPath", "authz/orders/allow");
            properties.put("evaluationMode", "wasm");
            properties.put("policyBundle", "classpath:orders-bundle.tar.gz");
            properties.put("entrypoint", "authz/orders/allow");
            properties.put("poolSize", "4");
            properties.put("borrowTimeout", "1500");

            OpaSecurityPolicy policy = new OpaSecurityPolicy();
            PropertyBindingSupport.setPropertiesOnTarget(context, policy, properties);

            assertThat(policy.getEvaluationMode()).isEqualTo("wasm");
            assertThat(policy.getPolicyBundle()).isEqualTo("classpath:orders-bundle.tar.gz");
            assertThat(policy.getEntrypoint()).isEqualTo("authz/orders/allow");
            assertThat(policy.getPoolSize()).isEqualTo(4);
            assertThat(policy.getBorrowTimeout()).isEqualTo(1500L);
        }
    }
}
