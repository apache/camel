/**
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
package org.apache.camel.component.http4;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Now we support to set sslContextParameters on different endpoints")
public class HttpsTwoDifferentSslContextParametersGetTest extends BaseHttpsTest {
  
    private HttpServer localServer;
    
    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                create();
        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("x509HostnameVerifier", new NoopHostnameVerifier());
        registry.bind("sslContextParameters", new SSLContextParameters());
        registry.bind("sslContextParameters2", new SSLContextParameters());

        return registry;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void httpsTwoDifferentSSLContextNotSupported() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo")
                        .to("https4://127.0.0.1:" + localServer.getLocalPort() + "/mail?x509HostnameVerifier=x509HostnameVerifier&sslContextParameters=#sslContextParameters");

                from("direct:bar")
                        .to("https4://127.0.0.1:" + localServer.getLocalPort() + "/mail?x509HostnameVerifier=x509HostnameVerifier&sslContextParameters=#sslContextParameters2");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            IllegalArgumentException iae = (IllegalArgumentException) e.getCause().getCause();
            assertNotNull(iae);
            assertTrue(iae.getMessage().startsWith("Only same instance of SSLContextParameters is supported."));
        }
    }

}
