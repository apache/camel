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
package org.apache.camel.component.netty4;

import java.io.File;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class NettySSLClientCertHeadersTest extends BaseNettyTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("ksf", new File("src/test/resources/keystore.jks"));
        registry.bind("tsf", new File("src/test/resources/keystore.jks"));
        return registry;
    }
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSSLInOutWithNettyConsumer() throws Exception {
        // ibm jdks dont have sun security algorithms
        if (isJavaVendor("ibm")) {
            return;
        }

        getMockEndpoint("mock:input").expectedMessageCount(1);

        getMockEndpoint("mock:input").expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_SUBJECT_NAME,
                "CN=arlu15, OU=Sun Java System Application Server, O=Sun Microsystems, L=Santa Clara, ST=California, C=US");
        getMockEndpoint("mock:input").expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_ISSUER_NAME,
                "CN=arlu15, OU=Sun Java System Application Server, O=Sun Microsystems, L=Santa Clara, ST=California, C=US");
        getMockEndpoint("mock:input").expectedHeaderReceived(NettyConstants.NETTY_SSL_CLIENT_CERT_SERIAL_NO, "1210701502");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                // needClientAuth=true so we can get the client certificate details
                from("netty4:tcp://localhost:{{port}}?sync=true&ssl=true&passphrase=changeit&keyStoreFile=#ksf&trustStoreFile=#tsf"
                        + "&needClientAuth=true&sslClientCertHeaders=true")
                    .to("mock:input")
                    .transform().constant("Bye World");
            }
        });
        context.start();

        String response = template.requestBody(
                "netty4:tcp://localhost:{{port}}?sync=true&ssl=true&passphrase=changeit&keyStoreFile=#ksf&trustStoreFile=#tsf",
                "Hello World", String.class);
        assertEquals("Bye World", response);

        assertMockEndpointsSatisfied();
    }

}
