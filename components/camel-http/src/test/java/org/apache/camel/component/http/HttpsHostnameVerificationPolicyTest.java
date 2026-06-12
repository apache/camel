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
package org.apache.camel.component.http;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that hostnameVerificationPolicy is honoured by HttpComponent / HttpEndpoint.
 *
 * The embedded server uses a self-signed cert issued to "localhost". Tests connect to "localhost" so BUILTIN/BOTH pass
 * the JDK hostname check; tests that set an invalid SNI host demonstrate that CLIENT delegates entirely to the
 * configured verifier (NoopHostnameVerifier → always passes).
 */
public class HttpsHostnameVerificationPolicyTest extends BaseHttpsTest {

    private HttpServer localServer;

    @BindToRegistry("noop")
    private NoopHostnameVerifier noop = new NoopHostnameVerifier();

    @BindToRegistry("ssl")
    private SSLContextParameters ssl = new SSLContextParameters();

    @Override
    public void doPreSetup() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/test/", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() {
        if (localServer != null) {
            localServer.stop();
        }
    }

    // CLIENT policy: NoopHostnameVerifier is the sole decider — request succeeds even though
    // the cert is self-signed, because Noop always returns true.
    @Test
    public void clientPolicyWithNoopSucceeds() {
        Exchange exchange = template.request(
                "https://localhost:" + localServer.getLocalPort()
                                             + "/test/?x509HostnameVerifier=#noop&sslContextParameters=#ssl&hostnameVerificationPolicy=CLIENT",
                e -> {
                });
        assertNotNull(exchange);
        assertNull(exchange.getException(), "Expected no exception with CLIENT + NoopHostnameVerifier");
    }

    // BOTH policy: JDK check runs first. The cert is issued to "localhost" and we connect to
    // "localhost", so the built-in check passes; Noop then also passes → success.
    @Test
    public void bothPolicyLocalhostCertSucceeds() {
        Exchange exchange = template.request(
                "https://localhost:" + localServer.getLocalPort()
                                             + "/test/?x509HostnameVerifier=#noop&sslContextParameters=#ssl&hostnameVerificationPolicy=BOTH",
                e -> {
                });
        assertNotNull(exchange);
        assertNull(exchange.getException(), "Expected no exception with BOTH when hostname matches cert");
    }

    // BUILTIN policy: only the JDK SSLParameters check runs. The cert is issued to "localhost" and
    // we connect to "localhost" → should pass regardless of the configured verifier.
    @Test
    public void builtinPolicyLocalhostCertSucceeds() {
        Exchange exchange = template.request(
                "https://localhost:" + localServer.getLocalPort()
                                             + "/test/?x509HostnameVerifier=#noop&sslContextParameters=#ssl&hostnameVerificationPolicy=BUILTIN",
                e -> {
                });
        assertNotNull(exchange);
        assertNull(exchange.getException(), "Expected no exception with BUILTIN when hostname matches cert");
    }

    // Verify that hostnameVerificationPolicy can also be set at the component level.
    @Test
    public void componentLevelPolicyIsRespected() {
        HttpComponent http = context.getComponent("https", HttpComponent.class);
        HostnameVerificationPolicy original = http.getHostnameVerificationPolicy();
        try {
            http.setHostnameVerificationPolicy(HostnameVerificationPolicy.CLIENT);
            Exchange exchange = template.request(
                    "https://localhost:" + localServer.getLocalPort()
                                                 + "/test/?x509HostnameVerifier=#noop&sslContextParameters=#ssl",
                    e -> {
                    });
            assertNotNull(exchange);
            assertNull(exchange.getException(), "Expected no exception when component policy is CLIENT + Noop");
        } finally {
            http.setHostnameVerificationPolicy(original);
        }
    }
}
