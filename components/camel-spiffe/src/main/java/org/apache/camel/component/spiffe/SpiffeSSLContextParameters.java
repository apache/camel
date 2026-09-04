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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.net.ssl.SSLContext;

import io.spiffe.provider.SpiffeSslContextFactory;
import io.spiffe.provider.SpiffeSslContextFactory.SslContextOptions;
import io.spiffe.spiffeid.SpiffeId;
import io.spiffe.workloadapi.DefaultX509Source;
import io.spiffe.workloadapi.DefaultX509Source.X509SourceOptions;
import io.spiffe.workloadapi.X509Source;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;

/**
 * An {@link SSLContextParameters} whose {@link SSLContext} is backed by the SPIFFE Workload API.
 * <p>
 * The X.509-SVID (certificate chain and private key) and the trust bundles are sourced live from a SPIFFE Workload API
 * endpoint - for example the one exposed by a SPIRE agent - and rotated automatically, providing zero-trust mutual TLS
 * to any Camel component that accepts an {@code sslContextParameters} reference (camel-http, camel-netty-http,
 * camel-jetty, camel-vertx-http, ...).
 * <p>
 * Peer authentication must be constrained explicitly: set {@link #setAcceptedSpiffeIds(String)} to an allow-list of
 * peer SPIFFE IDs, or {@link #setAcceptAnySpiffeId(boolean)} to {@code true} to accept any SVID that validates against
 * the trust bundle. Setting both is rejected, and setting neither fails closed.
 * <p>
 * The inherited {@code SSLContextParameters} configuration still applies: the built context is wrapped with the same
 * decorator as the parent, so {@code serverParameters.clientAuthentication}, {@code cipherSuites} and
 * {@code secureSocketProtocols} are honoured. The base handshake protocol is taken from
 * {@link #getSecureSocketProtocol()} (default {@code TLSv1.3}).
 * <p>
 * The underlying {@code X509Source} is created lazily on the first call, closed when the {@link CamelContext} shuts
 * down, and the cached context is invalidated at the same time so a restarted context rebuilds a fresh source.
 */
public class SpiffeSSLContextParameters extends SSLContextParameters {

    private static final String DEFAULT_PROTOCOL = "TLSv1.3";

    @Metadata(label = "security",
              description = "Path to the SPIFFE Workload API endpoint (for example unix:///tmp/agent.sock)."
                            + " When not set, the SPIFFE_ENDPOINT_SOCKET environment variable is used.")
    private String spiffeSocketPath;
    @Metadata(label = "security",
              description = "Comma-separated allow-list of peer SPIFFE IDs to accept during the TLS handshake"
                            + " (for example spiffe://example.org/client). Mutually exclusive with acceptAnySpiffeId.")
    private String acceptedSpiffeIds;
    @Metadata(label = "security", defaultValue = "false",
              description = "Accept any peer SPIFFE ID that validates against the trust bundle, instead of an"
                            + " explicit acceptedSpiffeIds allow-list. Use with care; mutually exclusive with"
                            + " acceptedSpiffeIds.")
    private boolean acceptAnySpiffeId;
    @Metadata(label = "security", defaultValue = "30000",
              description = "Timeout in milliseconds to wait for the first SVID from the Workload API when creating"
                            + " the source, so a slow or unreachable endpoint cannot block indefinitely.")
    private long initTimeout = 30000L;

    private volatile SSLContext sslContext;

    public String getSpiffeSocketPath() {
        return spiffeSocketPath;
    }

    /**
     * Path to the SPIFFE Workload API endpoint. When not set, the {@code SPIFFE_ENDPOINT_SOCKET} environment variable
     * is used.
     */
    public void setSpiffeSocketPath(String spiffeSocketPath) {
        this.spiffeSocketPath = spiffeSocketPath;
    }

    public String getAcceptedSpiffeIds() {
        return acceptedSpiffeIds;
    }

    /**
     * Comma-separated allow-list of peer SPIFFE IDs to accept during the TLS handshake. Mutually exclusive with
     * {@code acceptAnySpiffeId}.
     */
    public void setAcceptedSpiffeIds(String acceptedSpiffeIds) {
        this.acceptedSpiffeIds = acceptedSpiffeIds;
    }

    public boolean isAcceptAnySpiffeId() {
        return acceptAnySpiffeId;
    }

    /**
     * Accept any peer SPIFFE ID that validates against the trust bundle, instead of an explicit allow-list. Mutually
     * exclusive with {@code acceptedSpiffeIds}.
     */
    public void setAcceptAnySpiffeId(boolean acceptAnySpiffeId) {
        this.acceptAnySpiffeId = acceptAnySpiffeId;
    }

    public long getInitTimeout() {
        return initTimeout;
    }

    /**
     * Timeout in milliseconds to wait for the first SVID from the Workload API when creating the source (default
     * {@code 30000}).
     */
    public void setInitTimeout(long initTimeout) {
        this.initTimeout = initTimeout;
    }

    @Override
    public SSLContext createSSLContext(CamelContext camelContext) throws GeneralSecurityException, IOException {
        if (camelContext != null) {
            setCamelContext(camelContext);
        }
        SSLContext existing = sslContext;
        if (existing != null) {
            return existing;
        }

        String acceptedIds = parsePropertyValue(acceptedSpiffeIds);
        boolean haveAllowList = ObjectHelper.isNotEmpty(acceptedIds);
        if (acceptAnySpiffeId && haveAllowList) {
            throw new IllegalStateException(
                    "acceptAnySpiffeId and acceptedSpiffeIds are mutually exclusive; set only one");
        }
        if (!acceptAnySpiffeId && !haveAllowList) {
            throw new IllegalStateException(
                    "A SPIFFE-backed SSLContext requires either acceptAnySpiffeId=true or a non-empty"
                                            + " acceptedSpiffeIds allow-list");
        }

        synchronized (this) {
            if (sslContext != null) {
                return sslContext;
            }
            // parse the (cheap, non-IO) allow-list before opening a Workload API connection, so a malformed id
            // fails fast without a wasted round-trip
            Set<SpiffeId> acceptedSet = haveAllowList ? parseSpiffeIds(acceptedIds) : Set.of();
            if (!acceptAnySpiffeId && acceptedSet.isEmpty()) {
                throw new IllegalStateException(
                        "acceptedSpiffeIds did not contain any SPIFFE ID after trimming");
            }

            String protocol = parsePropertyValue(getSecureSocketProtocol());
            if (ObjectHelper.isEmpty(protocol)) {
                protocol = DEFAULT_PROTOCOL;
            }

            X509Source source = createX509Source();
            try {
                SslContextOptions.SslContextOptionsBuilder options
                        = SslContextOptions.builder().x509Source(source).sslProtocol(protocol);
                if (acceptAnySpiffeId) {
                    options.acceptAnySpiffeId();
                } else {
                    options.acceptedSpiffeIdsSupplier(() -> acceptedSet);
                }
                SSLContext spiffeContext = SpiffeSslContextFactory.getSslContext(options.build());
                // wrap with the same decorator the parent uses, so clientAuthentication / cipherSuites /
                // secureSocketProtocols configured on this instance are still applied to every engine and socket
                SSLContext decorated = new SSLContextDecorator(
                        new SSLContextSpiDecorator(
                                spiffeContext,
                                getSSLEngineConfigurers(spiffeContext),
                                getSSLSocketFactoryConfigurers(spiffeContext),
                                getSSLServerSocketFactoryConfigurers(spiffeContext)));
                registerForShutdown(camelContext, source);
                this.sslContext = decorated;
                return decorated;
            } catch (GeneralSecurityException | RuntimeException e) {
                closeQuietly(source);
                throw e;
            }
        }
    }

    private X509Source createX509Source() {
        try {
            X509SourceOptions.Builder builder = X509SourceOptions.builder().initTimeout(Duration.ofMillis(initTimeout));
            if (ObjectHelper.isNotEmpty(spiffeSocketPath)) {
                builder.spiffeSocketPath(parsePropertyValue(spiffeSocketPath));
            }
            return DefaultX509Source.newSource(builder.build());
        } catch (Exception e) {
            throw new RuntimeCamelException(
                    "Could not create the SPIFFE X509Source (is a Workload API endpoint reachable?)", e);
        }
    }

    private void registerForShutdown(CamelContext camelContext, X509Source source) {
        // fall back to the context this instance already knows (JsseParameters is CamelContextAware), so callers
        // that pass null - e.g. some HTTP server factories - still get the source closed on shutdown
        CamelContext context = camelContext != null ? camelContext : getCamelContext();
        if (context != null) {
            try {
                context.addService(new X509SourceService(source));
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    static Set<SpiffeId> parseSpiffeIds(String csv) {
        Set<SpiffeId> ids = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                ids.add(SpiffeId.parse(trimmed));
            }
        }
        return ids;
    }

    private static void closeQuietly(X509Source source) {
        if (source != null) {
            try {
                source.close();
            } catch (Exception e) {
                // ignore on cleanup
            }
        }
    }

    /**
     * Closes the {@link X509Source} and invalidates the cached context when the {@link CamelContext} shuts down, so the
     * Workload API watcher is not leaked and a restarted context rebuilds a fresh source.
     */
    private final class X509SourceService implements Service {
        private final X509Source source;

        X509SourceService(X509Source source) {
            this.source = source;
        }

        @Override
        public void start() {
            // nothing to start; the X509Source is already active once created
        }

        @Override
        public void stop() {
            closeQuietly(source);
            synchronized (SpiffeSSLContextParameters.this) {
                sslContext = null;
            }
        }
    }
}
