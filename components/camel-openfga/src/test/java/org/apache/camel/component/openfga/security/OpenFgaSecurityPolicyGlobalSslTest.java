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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The policy is a bean, not an endpoint of the {@code openfga} component, so the component's
 * {@code useGlobalSslContextParameters} cannot reach it: the policy has the option itself, with the endpoint's
 * precedence.
 */
class OpenFgaSecurityPolicyGlobalSslTest extends CamelTestSupport {

    private final RecordingSslContextParameters global = new RecordingSslContextParameters();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }

    private OpenFgaSecurityPolicy policy() {
        OpenFgaSecurityPolicy policy = new OpenFgaSecurityPolicy();
        policy.setApiUrl("https://openfga.example.org:8443");
        policy.setStoreId("01HSTORE");
        policy.setAuthorizationModelId("01HMODEL");
        policy.setRelation("reader");
        policy.setUser("user:anne");
        policy.setObject("document:budget");
        policy.setHealthCheckEnabled(false);
        return policy;
    }

    @Test
    void usesTheGlobalSslContextParametersWhenAskedTo() {
        context.setSSLContextParameters(global);
        OpenFgaSecurityPolicy policy = policy();
        policy.setUseGlobalSslContextParameters(true);

        policy.beforeWrap(context.getRoutes().get(0), null);

        assertThat(global.built).hasValue(1);
    }

    @Test
    void ignoresTheGlobalSslContextParametersByDefault() {
        context.setSSLContextParameters(global);
        OpenFgaSecurityPolicy policy = policy();

        policy.beforeWrap(context.getRoutes().get(0), null);

        assertThat(policy.isUseGlobalSslContextParameters()).isFalse();
        assertThat(global.built).hasValue(0);
    }

    @Test
    void prefersItsOwnSslContextParametersOverTheGlobalOnes() {
        context.setSSLContextParameters(global);
        RecordingSslContextParameters own = new RecordingSslContextParameters();
        OpenFgaSecurityPolicy policy = policy();
        policy.setSslContextParameters(own);
        policy.setUseGlobalSslContextParameters(true);

        policy.beforeWrap(context.getRoutes().get(0), null);

        assertThat(own.built).hasValue(1);
        assertThat(global.built).hasValue(0);
    }

    @Test
    void buildsNoSslContextWhenThereAreNoGlobalSslContextParameters() {
        OpenFgaSecurityPolicy policy = policy();
        policy.setUseGlobalSslContextParameters(true);

        // there is nothing to use, so the policy falls back to the JVM default, as it does without the option
        assertThat(context.getSSLContextParameters()).isNull();
        assertThatCode(() -> policy.beforeWrap(context.getRoutes().get(0), null)).doesNotThrowAnyException();
    }

    private static final class RecordingSslContextParameters extends SSLContextParameters {

        private final AtomicInteger built = new AtomicInteger();

        @Override
        public SSLContext createSSLContext(CamelContext camelContext) throws GeneralSecurityException, IOException {
            built.incrementAndGet();
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                throw new GeneralSecurityException(e);
            }
        }
    }
}
