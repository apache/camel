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

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty.JettyHttpServer;
import com.github.tomakehurst.wiremock.jetty.JettyHttpServerFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Jetty 9.x {@link org.eclipse.jetty.util.ssl.SslContextFactory} removed {@code selectCipherSuites} method.
 */
public final class WireMockJettyServerFactory extends JettyHttpServerFactory {
    @Override
    public HttpServer buildHttpServer(
            final Options options, final AdminRequestHandler adminRequestHandler,
            final StubRequestHandler stubRequestHandler) {

        return new JettyHttpServer(options, adminRequestHandler, stubRequestHandler) {
            @Override
            protected SslContextFactory.Server buildSslContextFactory() {
                SslContextFactory.Server sslContextFactory = super.buildSslContextFactory();
                sslContextFactory.setIncludeCipherSuites("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256");
                sslContextFactory.setProtocol("TLSv1.3");
                return sslContextFactory;
            }
        };
    }
}
