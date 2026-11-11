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

import javax.net.ssl.SSLContext;

import io.spiffe.provider.SpiffeSslContextFactory;
import io.spiffe.provider.SpiffeSslContextFactory.SslContextOptions;
import io.spiffe.workloadapi.DefaultX509Source;
import io.spiffe.workloadapi.DefaultX509Source.X509SourceOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SpiffeSSLContextParametersLifecycleTest {

    @Test
    void standaloneSourceIsClosedExactlyOnceAndCanBeRecreated() throws Exception {
        try (Sources sources = new Sources(); SpiffeSSLContextParameters parameters = parameters()) {
            parameters.close();
            SSLContext first = parameters.createSSLContext(null);
            assertThat(parameters.createSSLContext(null)).isSameAs(first);
            verify(sources.first, never()).close();

            parameters.close();
            parameters.close();
            verify(sources.first).close();

            SSLContext second = parameters.createSSLContext(null);
            assertThat(second).isNotSameAs(first);
            parameters.close();
            verify(sources.first).close();
            verify(sources.second).close();
        }
    }

    @Test
    void nullArgumentUsesTheAssociatedCamelContextForShutdown() throws Exception {
        try (Sources sources = new Sources(); CamelContext context = new DefaultCamelContext()) {
            context.start();
            SpiffeSSLContextParameters parameters = parameters();
            parameters.setCamelContext(context);
            parameters.createSSLContext(null);

            context.stop();
            verify(sources.first).close();
            parameters.close();
            verify(sources.first).close();
        }
    }

    @Test
    void contextRestartCreatesAFreshSource() throws Exception {
        try (Sources sources = new Sources(); CamelContext context = new DefaultCamelContext()) {
            context.start();
            SpiffeSSLContextParameters parameters = parameters();
            SSLContext first = parameters.createSSLContext(context);
            context.stop();
            verify(sources.first).close();

            context.start();
            assertThat(parameters.createSSLContext(context)).isNotSameAs(first);
            context.stop();
            verify(sources.first).close();
            verify(sources.second).close();
        }
    }

    @Test
    void stoppingAnOldContextDoesNotInvalidateARecreatedSource() throws Exception {
        try (Sources sources = new Sources(); CamelContext context = new DefaultCamelContext();
             CamelContext replacementContext = new DefaultCamelContext();
             SpiffeSSLContextParameters parameters = parameters()) {
            context.start();
            replacementContext.start();
            parameters.createSSLContext(context);
            parameters.close();
            SSLContext replacement = parameters.createSSLContext(replacementContext);

            context.stop();
            assertThat(parameters.createSSLContext(replacementContext)).isSameAs(replacement);
            verify(sources.first).close();
            verify(sources.second, never()).close();
            parameters.close();
            verify(sources.second).close();
        }
    }

    @Test
    void tlsContextCreationFailureClosesTheSourceAndAllowsRetry() throws Exception {
        try (Sources sources = new Sources(); SpiffeSSLContextParameters parameters = parameters()) {
            sources.factory.when(() -> SpiffeSslContextFactory.getSslContext(any(SslContextOptions.class)))
                    .thenThrow(new IllegalStateException("TLS initialization failed"))
                    .thenReturn(SSLContext.getDefault());

            assertThatThrownBy(() -> parameters.createSSLContext(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TLS initialization failed");
            verify(sources.first).close();

            assertThat(parameters.createSSLContext(null)).isNotNull();
            parameters.close();
            verify(sources.first).close();
            verify(sources.second).close();
        }
    }

    @Test
    void shutdownRegistrationFailureClosesTheSource() throws Exception {
        try (Sources sources = new Sources(); SpiffeSSLContextParameters parameters = parameters()) {
            CamelContext context = mock(CamelContext.class);
            doThrow(new IllegalStateException("Cannot register service")).when(context).addService(any());

            assertThatThrownBy(() -> parameters.createSSLContext(context))
                    .isInstanceOf(RuntimeCamelException.class)
                    .hasRootCauseMessage("Cannot register service");
            parameters.close();
            verify(sources.first, times(1)).close();
        }
    }

    private static SpiffeSSLContextParameters parameters() {
        SpiffeSSLContextParameters parameters = new SpiffeSSLContextParameters();
        parameters.setAcceptAnySpiffeId(true);
        return parameters;
    }

    private static final class Sources implements AutoCloseable {
        private final DefaultX509Source first = mock(DefaultX509Source.class);
        private final DefaultX509Source second = mock(DefaultX509Source.class);
        private final MockedStatic<DefaultX509Source> source = mockStatic(DefaultX509Source.class);
        private final MockedStatic<SpiffeSslContextFactory> factory = mockStatic(SpiffeSslContextFactory.class);

        private Sources() throws Exception {
            source.when(() -> DefaultX509Source.newSource(any(X509SourceOptions.class))).thenReturn(first, second);
            factory.when(() -> SpiffeSslContextFactory.getSslContext(any(SslContextOptions.class)))
                    .thenReturn(SSLContext.getDefault());
        }

        @Override
        public void close() {
            factory.close();
            source.close();
        }
    }
}
