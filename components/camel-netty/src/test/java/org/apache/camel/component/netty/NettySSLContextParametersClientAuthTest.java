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
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The client authentication (REQUIRE) of the sslContextParameters is used, also when needClientAuth is not enabled.
 */
@DisabledIfSystemProperty(named = "java.vendor", matches = ".*ibm.*")
public class NettySSLContextParametersClientAuthTest extends BaseNettyTest {

    private KeyStoreParameters keyStore() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("changeit");
        return ksp;
    }

    private KeyManagersParameters keyManagers() {
        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(keyStore());
        return kmp;
    }

    private TrustManagersParameters trustManagers() {
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(keyStore());
        return tmp;
    }

    @BindToRegistry("serverParameters")
    public SSLContextParameters serverParameters() {
        SSLContextServerParameters scsp = new SSLContextServerParameters();
        scsp.setClientAuthentication(ClientAuthentication.REQUIRE.name());

        SSLContextParameters scp = new SSLContextParameters();
        scp.setKeyManagers(keyManagers());
        scp.setTrustManagers(trustManagers());
        scp.setServerParameters(scsp);
        return scp;
    }

    @BindToRegistry("clientParameters")
    public SSLContextParameters clientParameters() {
        SSLContextParameters scp = new SSLContextParameters();
        scp.setKeyManagers(keyManagers());
        scp.setTrustManagers(trustManagers());
        return scp;
    }

    @BindToRegistry("noCertificateParameters")
    public SSLContextParameters noCertificateParameters() {
        SSLContextParameters scp = new SSLContextParameters();
        scp.setTrustManagers(trustManagers());
        return scp;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:tcp://localhost:{{port}}?sync=true&ssl=true&sslContextParameters=#serverParameters")
                        .transform().constant("Bye World");
            }
        };
    }

    @Test
    public void testClientWithCertificate() {
        String response = template.requestBody(
                "netty:tcp://localhost:{{port}}?sync=true&ssl=true&sslContextParameters=#clientParameters",
                "Hello World", String.class);
        assertEquals("Bye World", response);
    }

    @Test
    public void testClientWithoutCertificate() {
        assertThrows(CamelExecutionException.class, () -> template.requestBody(
                "netty:tcp://localhost:{{port}}?sync=true&ssl=true&sslContextParameters=#noCertificateParameters",
                "Hello World", String.class));
    }
}
