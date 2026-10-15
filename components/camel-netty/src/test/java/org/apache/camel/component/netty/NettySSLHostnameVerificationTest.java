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
package org.apache.camel.component.netty;

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
 * The producer checks that the certificate of the server matches the host it connects to when
 * {@code hostnameVerification=true}.
 * <p/>
 * keystore-localhost.jks lists only DNS:localhost as subject alternative name, so it matches "localhost" but not
 * "127.0.0.1" - which both routes below resolve to, so the same consumer serves them.
 */
@DisabledIfSystemProperty(named = "java.vendor", matches = ".*ibm.*")
class NettySSLHostnameVerificationTest extends BaseNettyTest {

    private static final String REPLY = "Bonjour";

    @BindToRegistry("sslContextParameters")
    public SSLContextParameters loadSSLContextParams() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore-localhost.jks").toString());
        ksp.setPassword("changeit");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        return sslContextParameters;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:tcp://localhost:{{port}}?sync=true&ssl=true&sslContextParameters=#sslContextParameters")
                        .setBody(constant(REPLY));
            }
        };
    }

    @Test
    void connectsWhenTheCertificateMatchesTheHost() {
        String out = template.requestBody(
                "netty:tcp://localhost:{{port}}?sync=true&ssl=true&sslContextParameters=#sslContextParameters"
                                          + "&hostnameVerification=true",
                "Hello", String.class);

        assertEquals(REPLY, out);
    }

    /**
     * Without a peer host on the engine the JDK cannot run the check at all and reports "Hostname or IP address is
     * undefined", which would make this pass for the wrong reason - so the message is asserted as well.
     */
    @Test
    void failsWhenTheCertificateDoesNotMatchTheHost() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(
                        "netty:tcp://127.0.0.1:{{port}}?sync=true&ssl=true&sslContextParameters=#sslContextParameters"
                                           + "&hostnameVerification=true",
                        "Hello", String.class));

        String message = rootCause(e).getMessage();
        assertTrue(message != null && message.contains("127.0.0.1"),
                "the host name check should have rejected the certificate, but got: " + message);
    }

    @Test
    void connectsToAHostTheCertificateDoesNotListWhenHostnameVerificationIsDisabled() {
        String out = template.requestBody(
                "netty:tcp://127.0.0.1:{{port}}?sync=true&ssl=true&sslContextParameters=#sslContextParameters",
                "Hello", String.class);

        assertEquals(REPLY, out);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable answer = t;
        while (answer.getCause() != null) {
            answer = answer.getCause();
        }
        return answer;
    }
}
