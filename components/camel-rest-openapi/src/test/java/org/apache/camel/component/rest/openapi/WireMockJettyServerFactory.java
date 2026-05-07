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
package org.apache.camel.component.rest.openapi;

import com.github.tomakehurst.wiremock.common.HttpsSettings;
import com.github.tomakehurst.wiremock.common.JettySettings;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty.JettyHttpServerFactory;
import com.github.tomakehurst.wiremock.jetty11.Jetty11HttpServer;
import com.github.tomakehurst.wiremock.jetty11.SslContexts;
import org.eclipse.jetty.io.NetworkTrafficListener;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Jetty 11.x {@link org.eclipse.jetty.util.ssl.SslContextFactory} removed {@code selectCipherSuites} method.
 */
public final class WireMockJettyServerFactory extends JettyHttpServerFactory {
    @Override
    public HttpServer buildHttpServer(
            final Options options, final AdminRequestHandler adminRequestHandler,
            final StubRequestHandler stubRequestHandler) {

        return new Jetty11HttpServer(options, adminRequestHandler, stubRequestHandler) {

            @Override
            protected ServerConnector createHttpsConnector(
                    String bindAddress, HttpsSettings httpsSettings, JettySettings jettySettings,
                    NetworkTrafficListener listener) {
                SslContextFactory.Server http2SslContextFactory = SslContexts.buildHttp2SslContextFactory(httpsSettings);
                http2SslContextFactory.setIncludeCipherSuites("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256");
                http2SslContextFactory.setProtocol("TLSv1.3");

                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.setRequestHeaderSize(
                        jettySettings.getRequestHeaderSize().orElse(32768));
                httpConfig.setResponseHeaderSize(
                        jettySettings.getResponseHeaderSize().orElse(32768));
                httpConfig.setSendDateHeader(false);
                httpConfig.setSendXPoweredBy(false);
                httpConfig.setSendServerVersion(false);
                httpConfig.addCustomizer(new SecureRequestCustomizer(false));
                httpConfig.setUriCompliance(org.eclipse.jetty.http.UriCompliance.UNSAFE);

                HttpConnectionFactory http = new HttpConnectionFactory(httpConfig);

                SslConnectionFactory ssl = new SslConnectionFactory(http2SslContextFactory, http.getProtocol());

                int acceptors = jettySettings.getAcceptors().orElse(3);

                NetworkTrafficServerConnector connector = new NetworkTrafficServerConnector(
                        jettyServer, null, null, null, acceptors, 2, ssl, http);

                connector.setPort(httpsSettings.port());
                connector.setNetworkTrafficListener(listener);
                jettySettings.getAcceptQueueSize().ifPresent(connector::setAcceptQueueSize);
                jettySettings.getIdleTimeout().ifPresent(connector::setIdleTimeout);
                connector.setShutdownIdleTimeout(jettySettings.getShutdownIdleTimeout().orElse(200L));
                connector.setHost(bindAddress);

                return connector;
            }
        };
    }
}
