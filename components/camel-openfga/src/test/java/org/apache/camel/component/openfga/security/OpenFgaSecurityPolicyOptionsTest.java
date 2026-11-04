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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.openfga.sdk.api.client.OpenFgaClient;
import org.apache.camel.CamelContext;
import org.apache.camel.component.openfga.OpenFgaConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The policy keeps its options in fields of its own, so that the bean catalog can describe them, and builds the
 * endpoint configuration its client and authorizer use from them when it wraps a route. An option it forgets to copy
 * would be accepted and then silently ignored - for a security policy, a check that quietly does not apply.
 */
class OpenFgaSecurityPolicyOptionsTest {

    // options of the policy itself, which the endpoint configuration has no counterpart for
    private static final Set<String> POLICY_ONLY = Set.of("healthCheckEnabled");

    @Test
    void everyOptionReachesTheConfigurationTheClientAndAuthorizerAreBuiltFrom() throws Exception {
        OpenFgaSecurityPolicy policy = new OpenFgaSecurityPolicy();
        Map<String, Object> values = new TreeMap<>();
        int n = 0;
        for (Method setter : setters(OpenFgaSecurityPolicy.class).values()) {
            String option = optionName(setter);
            if (POLICY_ONLY.contains(option)) {
                continue;
            }
            Object value = distinctValue(setter.getParameterTypes()[0], option, n++);
            setter.invoke(policy, value);
            values.put(option, value);
        }

        // a new option of the policy is either an endpoint option the configuration carries, or listed in POLICY_ONLY
        assertThat(setters(OpenFgaConfiguration.class).keySet()).containsAll(values.keySet());
        // the reflection above found the options at all
        assertThat(values).hasSizeGreaterThanOrEqualTo(21);

        OpenFgaConfiguration configuration = policy.toConfiguration();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object copied = getter(OpenFgaConfiguration.class, entry.getKey()).invoke(configuration);
            assertThat(copied).as(entry.getKey()).isEqualTo(entry.getValue());
            assertThat(getter(OpenFgaSecurityPolicy.class, entry.getKey()).invoke(policy)).as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void keepsTheDefaultsOfTheEndpointOptions() {
        OpenFgaConfiguration defaults = new OpenFgaConfiguration();
        OpenFgaSecurityPolicy policy = new OpenFgaSecurityPolicy();

        assertThat(policy.getApiUrl()).isEqualTo(defaults.getApiUrl());
        assertThat(policy.getConnectTimeout()).isEqualTo(defaults.getConnectTimeout());
        assertThat(policy.getReadTimeout()).isEqualTo(defaults.getReadTimeout());
        assertThat(policy.getMaxRetries()).isEqualTo(defaults.getMaxRetries());
        assertThat(policy.isFailOpen()).isFalse();
        assertThat(policy.isHealthCheckEnabled()).isTrue();
    }

    @Test
    void bindsTheOptionsAsAYamlBeanDefinitionGivesThem() throws Exception {
        // the YAML DSL hands every bean property over as a string, and a map value as a nested map
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            SSLContextParameters tls = new SSLContextParameters();
            context.getRegistry().bind("tls", tls);
            Map<String, Object> conditionContext = new LinkedHashMap<>();
            conditionContext.put("region", "eu");
            conditionContext.put("trusted", "#valueAs(boolean):true");
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("apiUrl", "https://openfga:8443");
            properties.put("storeId", "01HSTORE");
            properties.put("authorizationModelId", "01HMODEL");
            properties.put("relation", "reader");
            properties.put("user", "user:${exchangeProperty.CamelSpiffePeerId}");
            properties.put("object", "document:${header.documentId}");
            properties.put("consistency", "HIGHER_CONSISTENCY");
            properties.put("connectTimeout", "1500");
            properties.put("maxRetries", "0");
            properties.put("failOpen", "false");
            properties.put("healthCheckEnabled", "false");
            properties.put("sslContextParameters", "#bean:tls");
            properties.put("conditionContext", conditionContext);

            OpenFgaSecurityPolicy policy = new OpenFgaSecurityPolicy();
            PropertyBindingSupport.setPropertiesOnTarget(context, policy, properties);

            assertThat(policy.getApiUrl()).isEqualTo("https://openfga:8443");
            assertThat(policy.getStoreId()).isEqualTo("01HSTORE");
            assertThat(policy.getAuthorizationModelId()).isEqualTo("01HMODEL");
            assertThat(policy.getRelation()).isEqualTo("reader");
            assertThat(policy.getUser()).isEqualTo("user:${exchangeProperty.CamelSpiffePeerId}");
            assertThat(policy.getObject()).isEqualTo("document:${header.documentId}");
            assertThat(policy.getConsistency()).isEqualTo("HIGHER_CONSISTENCY");
            assertThat(policy.getConnectTimeout()).isEqualTo(1500L);
            assertThat(policy.getMaxRetries()).isZero();
            assertThat(policy.isFailOpen()).isFalse();
            assertThat(policy.isHealthCheckEnabled()).isFalse();
            assertThat(policy.getSslContextParameters()).isSameAs(tls);
            assertThat(policy.getConditionContext()).containsEntry("region", "eu").containsEntry("trusted", true);
        }
    }

    private static Map<String, Method> setters(Class<?> type) {
        Map<String, Method> answer = new TreeMap<>();
        for (Method m : type.getMethods()) {
            if (m.getDeclaringClass() == type && m.getName().startsWith("set") && m.getParameterCount() == 1
                    && !Modifier.isStatic(m.getModifiers())) {
                answer.put(optionName(m), m);
            }
        }
        return answer;
    }

    private static String optionName(Method setter) {
        String name = setter.getName().substring(3);
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static Method getter(Class<?> type, String option) throws NoSuchMethodException {
        String suffix = Character.toUpperCase(option.charAt(0)) + option.substring(1);
        try {
            return type.getMethod("get" + suffix);
        } catch (NoSuchMethodException e) {
            return type.getMethod("is" + suffix);
        }
    }

    // a value no option has by default, and no two options share
    private static Object distinctValue(Class<?> type, String option, int n) {
        if (type == String.class) {
            return "value-of-" + option;
        } else if (type == boolean.class) {
            return true;
        } else if (type == long.class) {
            return 4200L + n;
        } else if (type == int.class) {
            return 42 + n;
        } else if (type == Map.class) {
            return Map.of("option", option);
        } else if (type == SSLContextParameters.class) {
            return new SSLContextParameters();
        } else if (type == OpenFgaClient.class) {
            return mock(OpenFgaClient.class);
        }
        throw new IllegalStateException("No test value for option " + option + " of type " + type.getName());
    }
}
