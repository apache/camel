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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Now we support to set sslContextParameters on different endpoints")
public class HttpsTwoDifferentSslContextParametersGetTest extends BaseHttpsTest {

    private HttpServer localServer;

    @BindToRegistry("x509HostnameVerifier")
    private NoopHostnameVerifier hostnameVerifier = new NoopHostnameVerifier();

    @BindToRegistry("sslContextParameters")
    private SSLContextParameters sslContextParameters = new SSLContextParameters();

    @BindToRegistry("sslContextParameters2")
    private SSLContextParameters sslContextParameters2 = new SSLContextParameters();

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
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
    public void httpsTwoDifferentSSLContextNotSupported() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo")
                        .to(
                                "https://localhost:" + localServer.getLocalPort()
                                        + "/mail?x509HostnameVerifier=x509HostnameVerifier&sslContextParameters=#sslContextParameters");

                from("direct:bar")
                        .to(
                                "https://localhost:" + localServer.getLocalPort()
                                        + "/mail?x509HostnameVerifier=x509HostnameVerifier&sslContextParameters=#sslContextParameters2");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException iae =
                    (IllegalArgumentException) e.getCause().getCause();
            assertNotNull(iae);
            assertTrue(iae.getMessage().startsWith("Only same instance of SSLContextParameters is supported."));
        }
    }
}
