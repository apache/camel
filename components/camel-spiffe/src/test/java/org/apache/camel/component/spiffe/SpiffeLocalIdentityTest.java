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

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;

import io.spiffe.bundle.x509bundle.X509BundleSet;
import io.spiffe.provider.SpiffeSslContextFactory;
import io.spiffe.provider.SpiffeSslContextFactory.SslContextOptions;
import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.svid.x509svid.X509Svid;
import io.spiffe.workloadapi.DefaultX509Source;
import io.spiffe.workloadapi.DefaultX509Source.X509SourceOptions;
import io.spiffe.workloadapi.Watcher;
import io.spiffe.workloadapi.WorkloadApiClient;
import io.spiffe.workloadapi.X509Context;
import io.spiffe.workloadapi.X509Source;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpiffeLocalIdentityTest {
    private static final String DEFAULT = "spiffe://example.org/default";
    private static final String SELECTED = "spiffe://example.org/selected";

    @Test
    void selectsTheConfiguredIdentityAndIgnoresIdentityHeaders() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            X509Svid defaultSvid = svid(DEFAULT, 1);
            X509Svid selected = svid(SELECTED, 2);
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            when(client.fetchX509Context()).thenReturn(x509(defaultSvid, selected));
            SpiffeProducer producer = producer(context, client, SELECTED);
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader(SpiffeConstants.SPIFFE_ID, DEFAULT);
            exchange.getMessage().setHeader("CamelSpiffeLocalSpiffeId", DEFAULT);
            producer.process(exchange);
            assertThat(exchange.getMessage().getBody()).isSameAs(selected.getChain());
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo(SELECTED);
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.EXPIRY)).isEqualTo(selected.getLeaf().getNotAfter());
        }
    }

    @Test
    void retainsDefaultSelectionWhenUnset() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            X509Svid defaultSvid = svid(DEFAULT, 1);
            X509Context update = mock(X509Context.class);
            when(update.getDefaultSvid()).thenReturn(defaultSvid);
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            when(client.fetchX509Context()).thenReturn(update);
            Exchange exchange = new DefaultExchange(context);
            producer(context, client, null).process(exchange);
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isEqualTo(DEFAULT);
            verify(update, never()).getX509Svids();
        }
    }

    @Test
    void selectionTracksRotationAndFailsOnRemovalWithoutStaleOutputs() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            X509Svid defaultSvid = svid(DEFAULT, 1);
            X509Svid first = svid(SELECTED, 2);
            X509Svid rotated = svid(SELECTED, 3);
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            when(client.fetchX509Context()).thenReturn(x509(defaultSvid, first), x509(rotated, defaultSvid), x509(defaultSvid));
            SpiffeProducer producer = producer(context, client, SELECTED);
            Exchange exchange = new DefaultExchange(context);
            producer.process(exchange);
            assertThat(exchange.getMessage().getBody()).isSameAs(first.getChain());
            producer.process(exchange);
            assertThat(exchange.getMessage().getBody()).isSameAs(rotated.getChain());
            assertThatThrownBy(() -> producer.process(exchange)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(SELECTED).hasMessageContaining("not present");
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.SPIFFE_ID)).isNull();
            assertThat(exchange.getMessage().getHeader(SpiffeConstants.EXPIRY)).isNull();
        }
    }

    @Test
    void rejectsMalformedLocalIdentityBeforeFetchingMaterials() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            assertThatThrownBy(() -> producer(context, client, "not-a-spiffe-id"))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(client, never()).fetchX509Context();
        }
    }

    @Test
    void tlsOptionsUseTheSelectorAndGuardTheSource() throws Exception {
        DefaultX509Source source = mock(DefaultX509Source.class);
        X509Svid defaultSvid = svid(DEFAULT, 1);
        X509Svid selected = svid(SELECTED, 2);
        when(source.getX509Svid()).thenReturn(selected);
        AtomicReference<X509SourceOptions> seenSource = new AtomicReference<>();
        AtomicReference<SslContextOptions> seenTls = new AtomicReference<>();
        try (CamelContext context = new DefaultCamelContext();
             MockedStatic<DefaultX509Source> factory = mockStatic(DefaultX509Source.class);
             MockedStatic<SpiffeSslContextFactory> tls = mockStatic(SpiffeSslContextFactory.class)) {
            factory.when(() -> DefaultX509Source.newSource(any(X509SourceOptions.class))).thenAnswer(invocation -> {
                seenSource.set(invocation.getArgument(0));
                return source;
            });
            SSLContext defaultContext = SSLContext.getDefault();
            tls.when(() -> SpiffeSslContextFactory.getSslContext(any(SslContextOptions.class))).thenAnswer(invocation -> {
                seenTls.set(invocation.getArgument(0));
                return defaultContext;
            });
            SpiffeSSLContextParameters parameters = new SpiffeSSLContextParameters();
            parameters.setAcceptedSpiffeIds("spiffe://example.org/peer");
            parameters.setLocalSpiffeId(SELECTED);
            parameters.createSSLContext(context);

            assertThat(seenSource.get().getSvidPicker().apply(List.of(defaultSvid, selected))).isSameAs(selected);
            assertThat(seenSource.get().getSvidPicker().apply(List.of(defaultSvid))).isNull();
            X509Source guarded = seenTls.get().getX509Source();
            assertThat(guarded.getX509Svid()).isSameAs(selected);
            when(source.getX509Svid()).thenReturn(null);
            assertThatThrownBy(guarded::getX509Svid).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(SELECTED);
        }
        verify(source).close();
    }

    @Test
    void initiallyMissingTlsIdentityClosesTheOpenedSource() throws Exception {
        DefaultX509Source source = mock(DefaultX509Source.class);
        try (MockedStatic<DefaultX509Source> factory = mockStatic(DefaultX509Source.class)) {
            factory.when(() -> DefaultX509Source.newSource(any(X509SourceOptions.class))).thenReturn(source);
            SpiffeSSLContextParameters parameters = new SpiffeSSLContextParameters();
            parameters.setAcceptAnySpiffeId(true);
            parameters.setLocalSpiffeId(SELECTED);
            assertThatThrownBy(() -> parameters.createSSLContext(null)).isInstanceOf(RuntimeCamelException.class)
                    .hasRootCauseMessage("Configured local SPIFFE ID " + SELECTED
                                         + " is not present in the Workload API X.509 context");
            verify(source).close();
        }
    }

    @Test
    void unsetTlsSelectorPreservesTheSdkDefaultPicker() throws Exception {
        DefaultX509Source source = mock(DefaultX509Source.class);
        AtomicReference<X509SourceOptions> seen = new AtomicReference<>();
        try (CamelContext context = new DefaultCamelContext();
             MockedStatic<DefaultX509Source> factory = mockStatic(DefaultX509Source.class);
             MockedStatic<SpiffeSslContextFactory> tls = mockStatic(SpiffeSslContextFactory.class)) {
            factory.when(() -> DefaultX509Source.newSource(any(X509SourceOptions.class))).thenAnswer(invocation -> {
                seen.set(invocation.getArgument(0));
                return source;
            });
            SSLContext defaultContext = SSLContext.getDefault();
            tls.when(() -> SpiffeSslContextFactory.getSslContext(any(SslContextOptions.class))).thenReturn(defaultContext);
            SpiffeSSLContextParameters parameters = new SpiffeSSLContextParameters();
            parameters.setAcceptAnySpiffeId(true);
            parameters.createSSLContext(context);
            assertThat(seen.get().getSvidPicker()).isNull();
            verify(source, never()).getX509Svid();
        }
        verify(source).close();
    }

    @Test
    void malformedTlsSelectorFailsBeforeOpeningASource() {
        try (MockedStatic<DefaultX509Source> factory = mockStatic(DefaultX509Source.class)) {
            SpiffeSSLContextParameters parameters = new SpiffeSSLContextParameters();
            parameters.setAcceptAnySpiffeId(true);
            parameters.setLocalSpiffeId("not-a-spiffe-id");
            assertThatThrownBy(() -> parameters.createSSLContext(null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("localSpiffeId");
            factory.verifyNoInteractions();
        }
    }

    @Test
    void actualSdkSourcePublishesRemovalAndRotatesWithoutFallingBack() throws Exception {
        X509Svid defaultSvid = svid(DEFAULT, 1);
        X509Svid selected = svid(SELECTED, 2);
        X509Svid rotated = svid(SELECTED, 3);
        WorkloadApiClient client = mock(WorkloadApiClient.class);
        AtomicReference<Watcher<X509Context>> updates = new AtomicReference<>();
        doAnswer(invocation -> {
            Watcher<X509Context> watcher = invocation.getArgument(0);
            updates.set(watcher);
            watcher.onUpdate(x509(defaultSvid, selected));
            return null;
        }).when(client).watchX509Context(any());
        SpiffeId requested = SpiffeId.parse(SELECTED);
        X509SourceOptions options = X509SourceOptions.builder().workloadApiClient(client).initTimeout(Duration.ofSeconds(1))
                .svidPicker(svids -> SpiffeX509SvidSelector.select(svids, requested)).build();
        try (X509Source source = SpiffeX509SvidSelector.guard(DefaultX509Source.newSource(options), requested)) {
            assertThat(source.getX509Svid()).isSameAs(selected);
            updates.get().onUpdate(x509(defaultSvid));
            assertThatThrownBy(source::getX509Svid).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(SELECTED);
            updates.get().onUpdate(x509(rotated, defaultSvid));
            assertThat(source.getX509Svid()).isSameAs(rotated);
        }
    }

    private static X509Svid svid(String id, int generation) throws Exception {
        X509Svid svid = mock(X509Svid.class);
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getNotAfter()).thenReturn(new Date(2000000000000L + generation));
        when(svid.getSpiffeId()).thenReturn(SpiffeId.parse(id));
        when(svid.getLeaf()).thenReturn(certificate);
        when(svid.getChain()).thenReturn(List.of(certificate));
        return svid;
    }

    private static X509Context x509(X509Svid... svids) {
        return X509Context.of(List.of(svids), X509BundleSet.emptySet());
    }

    private static SpiffeProducer producer(CamelContext context, WorkloadApiClient client, String localSpiffeId) {
        SpiffeConfiguration configuration = new SpiffeConfiguration();
        configuration.setLocalSpiffeId(localSpiffeId);
        SpiffeEndpoint endpoint = new SpiffeEndpoint("spiffe:selection", new SpiffeComponent(context), configuration) {
            @Override
            public WorkloadApiClient getWorkloadApiClient() {
                return client;
            }
        };
        return new SpiffeProducer(endpoint);
    }
}
