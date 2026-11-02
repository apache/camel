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

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer that asks for client certificates completes the handshake when {@code hostnameVerification=true}.
 * <p/>
 * The JDK does not support host name verification on the server side of a handshake: when the option was passed to the
 * server engine, it rejected every client certificate with
 * {@code Endpoint Identification Algorithm HTTPS is not supported on the server side}.
 */
@DisabledIfSystemProperty(named = "java.vendor", matches = ".*ibm.*")
public class NettyHttpSSLServerHostnameVerificationTest extends BaseNettyTestSupport {

    @BindToRegistry("clientCertParameters")
    public SSLContextParameters loadClientCertParameters() {
        SSLContextParameters answer = loadTrustOnlyParameters();
        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(localhostKeyStore());
        answer.setKeyManagers(kmp);
        return answer;
    }

    @BindToRegistry("trustOnlyParameters")
    public SSLContextParameters loadTrustOnlyParameters() {
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(localhostKeyStore());

        SSLContextParameters answer = new SSLContextParameters();
        answer.setTrustManagers(tmp);
        return answer;
    }

    private static KeyStoreParameters localhostKeyStore() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource("jsse/localhost.p12");
        ksp.setPassword("changeit");
        return ksp;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:https://localhost:{{port}}/hello?sslContextParameters=#clientCertParameters"
                     + "&needClientAuth=true&hostnameVerification=true")
                        .transform().constant("Bye World");
            }
        };
    }

    @Test
    public void testClientCertificateAccepted() {
        String out = template.requestBody(
                "netty-http:https://localhost:{{port}}/hello?sslContextParameters=#clientCertParameters",
                "Hello World", String.class);

        assertEquals("Bye World", out);
    }

    /**
     * The consumer still requires a client certificate, so the handshake in the test above did not pass because the
     * client authentication was dropped.
     */
    @Test
    public void testNoClientCertificateRejected() {
        assertThrows(CamelExecutionException.class,
                () -> template.requestBody(
                        "netty-http:https://localhost:{{port}}/hello?sslContextParameters=#trustOnlyParameters",
                        "Hello World", String.class));
    }

    /**
     * The option still applies to the producer: localhost.p12 lists only DNS:localhost as subject alternative name, so
     * it does not match 127.0.0.1.
     */
    @Test
    public void testProducerRejectsCertificateNotMatchingTheHost() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(
                        "netty-http:https://127.0.0.1:{{port}}/hello?sslContextParameters=#clientCertParameters"
                                           + "&hostnameVerification=true",
                        "Hello World", String.class));

        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause.getMessage() != null && cause.getMessage().contains("127.0.0.1"),
                "the host name check should have rejected the certificate, but got: " + cause.getMessage());
    }
}
