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
package org.apache.camel.component.spiffe.integration;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLContext;

import io.spiffe.spiffeid.TrustDomain;
import io.spiffe.workloadapi.DefaultX509Source;
import io.spiffe.workloadapi.DefaultX509Source.X509SourceOptions;
import io.spiffe.workloadapi.X509Source;
import org.apache.camel.component.spiffe.SpiffeSSLContextParameters;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.test.infra.spiffe.services.SpiffeLocalContainerInfraService;
import org.apache.camel.test.infra.spiffe.services.SpiffeService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/** Dedicated containers: short TTLs and agent interruption must never affect the shared Workload API fixture. */
@EnabledOnOs(OS.LINUX)
class SpiffeRotationIT extends CamelTestSupport {

    @RegisterExtension
    static RotationService service = new RotationService();

    static class RotationService extends SpiffeLocalContainerInfraService implements SpiffeService {
        RotationService() {
            // SPIRE guarantees SVID lifetime only up to CA TTL / 6. Its CA rotator ticks every ten seconds.
            super(Duration.ofSeconds(20), Duration.ofSeconds(120));
        }
    }

    @Test
    void rotatesRecoversAndRecreatesSources() throws Exception {
        List<DefaultX509Source> sources = new ArrayList<>();
        try (MockedStatic<DefaultX509Source> factory = mockStatic(DefaultX509Source.class, CALLS_REAL_METHODS)) {
            factory.when(() -> DefaultX509Source.newSource(any(X509SourceOptions.class))).thenAnswer(invocation -> {
                DefaultX509Source realSource = (DefaultX509Source) invocation.callRealMethod();
                // Delegate rather than spy: SDK callbacks update the original source snapshot asynchronously.
                DefaultX509Source source = mock(DefaultX509Source.class, delegatesTo(realSource));
                sources.add(source);
                return source;
            });
            SpiffeSSLContextParameters serverParameters = parameters(true);
            SpiffeSSLContextParameters clientParameters = parameters(false);
            SSLContext server = serverParameters.createSSLContext(context);
            SSLContext client = clientParameters.createSSLContext(context);
            try (X509Source observer = DefaultX509Source.newSource(X509SourceOptions.builder()
                    .spiffeSocketPath(service.getWorkloadApiSocketPath()).initTimeout(Duration.ofSeconds(20)).build())) {
                Set<X509Certificate> originalAuthorities = authorities(observer);
                X509Certificate first = SpiffeMutualTlsIT.handshake(server, client);

                await().alias("leaf rotation on existing SSLContexts").atMost(Duration.ofSeconds(90))
                        .pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                            X509Certificate rotated = SpiffeMutualTlsIT.handshake(server, client);
                            assertThat(rotated.getSerialNumber()).isNotEqualTo(first.getSerialNumber());
                        });

                // Stop/start the process, preserving its node key and data. Cached, still-valid credentials can complete
                // fresh handshakes during disconnection; this does not assert that an already-open TLS session revalidates.
                service.stopAgent();
                try {
                    assertThat(SpiffeMutualTlsIT.handshake(server, client)).isNotNull();
                } finally {
                    service.startAgent();
                }
                X509Certificate beforeRecovery = SpiffeMutualTlsIT.handshake(server, client);
                await().alias("watcher reconnect and fresh SVID after agent restart").atMost(Duration.ofSeconds(90))
                        .pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                            assertThat(SpiffeMutualTlsIT.handshake(server, client).getSerialNumber())
                                    .isNotEqualTo(beforeRecovery.getSerialNumber());
                        });

                // A changed CA certificate alone is insufficient: the peer leaf must verify with a newly published CA
                // and fail verification against every original CA, while the original SSLContext still trusts it.
                await().alias("new trust authority published and used in fresh TLS handshakes")
                        .atMost(Duration.ofSeconds(180)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                            Set<X509Certificate> added = authorities(observer);
                            added.removeAll(originalAuthorities);
                            assertThat(added).isNotEmpty();
                            X509Certificate peer = SpiffeMutualTlsIT.handshake(server, client);
                            assertThat(added.stream().anyMatch(authority -> signedBy(peer, authority))).isTrue();
                            assertThat(originalAuthorities.stream().noneMatch(authority -> signedBy(peer, authority))).isTrue();
                        });

                context.stop();
                verify(sources.get(0)).close();
                verify(sources.get(1)).close();
                context.start();
                SSLContext restartedServer = serverParameters.createSSLContext(context);
                SSLContext restartedClient = clientParameters.createSSLContext(context);
                assertThat(restartedServer).isNotSameAs(server);
                assertThat(restartedClient).isNotSameAs(client);
                assertThat(sources).hasSize(5);
                assertThat(SpiffeMutualTlsIT.handshake(restartedServer, restartedClient)).isNotNull();
                X509Certificate restartedLeaf = SpiffeMutualTlsIT.handshake(restartedServer, restartedClient);
                await().alias("recreated source continues observing rotation").atMost(Duration.ofSeconds(90))
                        .pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                            assertThat(SpiffeMutualTlsIT.handshake(restartedServer, restartedClient).getSerialNumber())
                                    .isNotEqualTo(restartedLeaf.getSerialNumber());
                        });
            }
        }
    }

    private static Set<X509Certificate> authorities(X509Source source) throws Exception {
        return new HashSet<>(
                source.getBundleForTrustDomain(TrustDomain.parse(service.getTrustDomain()))
                        .getX509Authorities());
    }

    private static boolean signedBy(X509Certificate leaf, X509Certificate authority) {
        try {
            leaf.verify(authority.getPublicKey());
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static SpiffeSSLContextParameters parameters(boolean server) {
        SpiffeSSLContextParameters parameters = new SpiffeSSLContextParameters();
        parameters.setSpiffeSocketPath(service.getWorkloadApiSocketPath());
        parameters.setAcceptedSpiffeIds(service.getWorkloadSpiffeId());
        if (server) {
            SSLContextServerParameters configuration = new SSLContextServerParameters();
            configuration.setClientAuthentication(ClientAuthentication.REQUIRE.name());
            parameters.setServerParameters(configuration);
        }
        return parameters;
    }
}
