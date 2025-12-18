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
package org.apache.camel.component.netty.http;

import java.util.List;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextClientParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "java.vendor", matches = ".*ibm.*")
public class NettyHttpSSLSNITest extends BaseNettyTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSSLAddsDefaultServerNameIndication() throws Exception {
        getMockEndpoint("mock:output").expectedBodiesReceived("localhost");

        context.getRegistry().bind("customSSLContextParameters", createSSLContextParameters(null));
        context.addRoutes(nettyServerThatRespondsWithSNI());
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                        .to("netty-http:https://localhost:{{port}}?sslContextParameters=#customSSLContextParameters")
                        .to("mock:output");
            }
        });
        context.start();
        template.sendBody("direct:in", null);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSSLAddsCustomServerNameIndication() throws Exception {
        String customSNI = "custom.host.name";

        getMockEndpoint("mock:output").expectedBodiesReceived(customSNI);

        SSLContextClientParameters customClientParameters = new SSLContextClientParameters();
        customClientParameters.setSniHostName(customSNI);

        context.getRegistry().bind("customSSLContextParameters", createSSLContextParameters(customClientParameters));
        context.addRoutes(nettyServerThatRespondsWithSNI());
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                        .to("netty-http:https://localhost:{{port}}?sslContextParameters=#customSSLContextParameters")
                        .to("mock:output");
            }
        });
        context.start();
        template.sendBody("direct:in", null);

        MockEndpoint.assertIsSatisfied(context);
    }

    private SSLContextParameters createSSLContextParameters(SSLContextClientParameters clientParameters) {
        SSLContextParameters sslContextParameters = new SSLContextParameters();

        sslContextParameters.setClientParameters(clientParameters);

        KeyStoreParameters trustStoreParameters = new KeyStoreParameters();
        trustStoreParameters.setResource("jsse/localhost.p12");
        trustStoreParameters.setPassword("changeit");

        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setKeyStore(trustStoreParameters);

        sslContextParameters.setTrustManagers(trustManagersParameters);

        return sslContextParameters;
    }

    private RouteBuilder nettyServerThatRespondsWithSNI() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:https://localhost:{{port}}?ssl=true&passphrase=changeit&keyStoreResource=jsse/localhost.p12&trustStoreResource=jsse/localhost.p12")
                        .process(exchange -> {
                            ExtendedSSLSession extendedSSLSession
                                    = exchange.getIn().getHeader(NettyConstants.NETTY_SSL_SESSION, ExtendedSSLSession.class);
                            if (extendedSSLSession != null) {
                                List<SNIServerName> serverNames = extendedSSLSession.getRequestedServerNames();
                                if (serverNames.size() == 1) {
                                    exchange.getMessage().setBody(((SNIHostName) serverNames.get(0)).getAsciiName());
                                } else {
                                    exchange.getMessage().setBody("SNI is missing or incorrect");
                                }
                            } else {
                                exchange.getMessage().setBody("Cannot determine success without ExtendedSSLSession");
                            }
                        });
            }
        };
    }
}
