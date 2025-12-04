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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Collections;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.protocol.UriPatternType;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Test;

public class HttpsTwoComponentsSslContextParametersGetTest extends BaseHttpsTest {

    private int port2;
    private HttpServer localServer;

    @BindToRegistry("x509HostnameVerifier")
    private NoopHostnameVerifier hostnameVerifier = new NoopHostnameVerifier();

    @BindToRegistry("sslContextParameters")
    private SSLContextParameters sslContextParameters = new SSLContextParameters();

    @BindToRegistry("sslContextParameters2")
    private SSLContextParameters sslContextParameters2 = new SSLContextParameters();

    @BindToRegistry("https-foo")
    private HttpComponent httpComponent = new HttpComponent();

    @BindToRegistry("https-bar")
    private HttpComponent httpComponent1 = new HttpComponent();

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setHttpProcessor(getBasicHttpProcessor())
                .setRequestRouter(RequestRouter.create(
                        new URIAuthority("localhost"),
                        UriPatternType.URI_PATTERN,
                        Collections.EMPTY_LIST,
                        RequestRouter.LOCAL_AUTHORITY_RESOLVER,
                        null))
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void httpsTwoDifferentSSLContextNotSupported() {
        assertDoesNotThrow(this::runTest);
    }

    private void runTest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                port2 = AvailablePortFinder.getNextAvailable();

                from("direct:foo")
                        .to(
                                "https-foo://localhost:" + localServer.getLocalPort()
                                        + "/mail?x509HostnameVerifier=#x509HostnameVerifier&sslContextParameters=#sslContextParameters");

                from("direct:bar")
                        .to(
                                "https-bar://localhost:" + port2
                                        + "/mail?x509HostnameVerifier=#x509HostnameVerifier&sslContextParameters=#sslContextParameters2");
            }
        });

        context.start();

        // should be able to startup
        Thread.sleep(500);

        context.stop();
    }
}
