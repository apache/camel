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
package org.apache.camel.component.mllp;

import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MLLP producer checks that the certificate of the server matches the host it connects to.
 * <p>
 * The certificate in keystore.jks lists only DNS:localhost as subject alternative name, so it matches "localhost" but
 * not "127.0.0.1".
 */
class MllpTcpClientProducerHostnameVerificationTest extends CamelTestSupport {

    static final String TEST_PAYLOAD = "MSH|^~\\&|CLIENT|TEST|SERVER|ACK|20231118120000||ADT^A01|123456|T|2.6\r"
                                       + "EVN|A01|20231118120000\r"
                                       + "PID|1|12345|67890||DOE^JOHN||19800101|M|||123 Main St^^Springfield^IL^62704||(555)555-5555|||||S\r"
                                       + "PV1|1|O\r";

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @EndpointInject("mock://result")
    MockEndpoint result;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("sslContextParameters", createSslContextParameters());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no host, so the consumer listens on every address and the producer can use a name or an IP address
                fromF("mllp://%d?sslContextParameters=#sslContextParameters", port.getPort())
                        .to(result);
            }
        };
    }

    @Test
    void sendsWhenTheCertificateMatchesTheHost() throws Exception {
        result.expectedBodiesReceived(TEST_PAYLOAD);

        template.sendBody(producerUri("localhost", ""), TEST_PAYLOAD);

        result.assertIsSatisfied();
    }

    @Test
    void failsWhenTheCertificateDoesNotMatchTheHost() throws Exception {
        result.expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody(producerUri("127.0.0.1", ""), TEST_PAYLOAD));

        assertNotNull(ObjectHelper.getException(SSLHandshakeException.class, e), "The TLS handshake should fail");
        CertificateException rejection = ObjectHelper.getException(CertificateException.class, e);
        assertNotNull(rejection, "The certificate of the server should be rejected");
        assertTrue(rejection.getMessage().contains("127.0.0.1"), rejection.getMessage());
        result.assertIsSatisfied();
    }

    @Test
    void sendsToAHostTheCertificateDoesNotListWhenHostnameVerificationIsDisabled() throws Exception {
        result.expectedBodiesReceived(TEST_PAYLOAD);

        template.sendBody(producerUri("127.0.0.1", "&hostnameVerification=false"), TEST_PAYLOAD);

        result.assertIsSatisfied();
    }

    private String producerUri(String host, String options) {
        return String.format("mllp://%s:%d?sslContextParameters=#sslContextParameters%s", host, port.getPort(), options);
    }

    private SSLContextParameters createSslContextParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("password");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("password");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        return sslContextParameters;
    }
}
