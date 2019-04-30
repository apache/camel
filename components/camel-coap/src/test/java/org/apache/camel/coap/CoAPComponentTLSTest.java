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
package org.apache.camel.coap;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.KeyGenerator;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.californium.scandium.dtls.rpkstore.TrustedRpkStore;
import org.junit.Test;

public class CoAPComponentTLSTest extends CamelTestSupport {

    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static final int PORT2 = AvailablePortFinder.getNextAvailable();
    private static final int PORT3 = AvailablePortFinder.getNextAvailable();
    private static final int PORT4 = AvailablePortFinder.getNextAvailable();
    private static final int PORT5 = AvailablePortFinder.getNextAvailable();
    private static final int PORT6 = AvailablePortFinder.getNextAvailable();
    private static final int PORT7 = AvailablePortFinder.getNextAvailable();
    private static final int PORT8 = AvailablePortFinder.getNextAvailable();

    @Test
    public void testSuccessfulCall() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:start", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoTruststore() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        sendBodyAndHeader("direct:notruststore", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTrustValidationFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        sendBodyAndHeader("direct:failedtrust", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSelfSigned() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:selfsigned", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testClientAuthentication() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:clientauth", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailedClientAuthentication() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        sendBodyAndHeader("direct:failedclientauth", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCipherSuites() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:ciphersuites", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRawPublicKey() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:rpk", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRawPublicKeyNoTruststore() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        sendBodyAndHeader("direct:rpknotruststore", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRawPublicKeyFailedTrust() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        sendBodyAndHeader("direct:rpkfailedtrust", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRawPublicKeyClientAuth() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:rpkclientauth", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPreSharedKey() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:psk", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPreSharedKeyCipherSuite() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:pskciphersuite", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPreSharedKeyX509() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(Exchange.CONTENT_TYPE, MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:pskx509", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("service.jks");
        keystoreParameters.setPassword("security");

        KeyStore keyStore = keystoreParameters.createKeyStore();
        PrivateKey privateKey = (PrivateKey)keyStore.getKey("service", "security".toCharArray());
        PublicKey publicKey = keyStore.getCertificate("service").getPublicKey();

        KeyStoreParameters keystoreParameters2 = new KeyStoreParameters();
        keystoreParameters2.setResource("selfsigned.jks");
        keystoreParameters2.setPassword("security");

        KeyStoreParameters keystoreParameters3 = new KeyStoreParameters();
        keystoreParameters3.setResource("client.jks");
        keystoreParameters3.setPassword("security");

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("truststore.jks");
        truststoreParameters.setPassword("storepass");

        KeyStoreParameters truststoreParameters2 = new KeyStoreParameters();
        truststoreParameters2.setResource("truststore2.jks");
        truststoreParameters2.setPassword("storepass");

        TrustedRpkStore trustedRpkStore = id -> {
            return true;
        };
        TrustedRpkStore failedTrustedRpkStore = id -> {
            return false;
        };
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        PskStore pskStore = new StaticPskStore("some-identity", keyGenerator.generateKey().getEncoded());

        context.getRegistry().bind("keyParams", keystoreParameters);
        context.getRegistry().bind("keyParams2", keystoreParameters2);
        context.getRegistry().bind("keyParams3", keystoreParameters3);
        context.getRegistry().bind("trustParams", truststoreParameters);
        context.getRegistry().bind("trustParams2", truststoreParameters2);
        context.getRegistry().bind("privateKey", privateKey);
        context.getRegistry().bind("publicKey", publicKey);
        context.getRegistry().bind("trustedRpkStore", trustedRpkStore);
        context.getRegistry().bind("failedTrustedRpkStore", failedTrustedRpkStore);
        context.getRegistry().bind("pskStore", pskStore);

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&keyStoreParameters=#keyParams", PORT)
                    .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?alias=selfsigned&password=security&keyStoreParameters=#keyParams2", PORT2)
                    .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&trustStoreParameters=#trustParams&"
                      + "keyStoreParameters=#keyParams&clientAuthentication=REQUIRE", PORT3)
                    .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&keyStoreParameters=#keyParams&cipherSuites=TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8", PORT4)
                    .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?privateKey=#privateKey&publicKey=#publicKey", PORT5)
                    .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?privateKey=#privateKey&publicKey=#publicKey&clientAuthentication=REQUIRE&trustedRpkStore=#trustedRpkStore", PORT6)
                    .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?pskStore=#pskStore", PORT7)
                    .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&keyStoreParameters=#keyParams&pskStore=#pskStore", PORT8)
                    .transform(body().prepend("Hello "));

                from("direct:start")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams", PORT)
                    .to("mock:result");

                from("direct:notruststore")
                    .toF("coaps://localhost:%d/TestResource", PORT)
                    .to("mock:result");

                from("direct:failedtrust")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams2", PORT)
                    .to("mock:result");

                from("direct:selfsigned")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#keyParams2", PORT2)
                    .to("mock:result");

                from("direct:clientauth")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams&keyStoreParameters=#keyParams3&alias=client&password=security", PORT3)
                    .to("mock:result");

                from("direct:failedclientauth")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams&keyStoreParameters=#keyParams2&alias=selfsigned&password=security", PORT3)
                    .to("mock:result");

                from("direct:ciphersuites")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams&cipherSuites=TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8", PORT4)
                    .to("mock:result");

                from("direct:rpk")
                    .toF("coaps://localhost:%d/TestResource?trustedRpkStore=#trustedRpkStore", PORT5)
                    .to("mock:result");

                from("direct:rpknotruststore")
                    .toF("coaps://localhost:%d/TestResource", PORT5)
                    .to("mock:result");

                from("direct:rpkfailedtrust")
                    .toF("coaps://localhost:%d/TestResource?trustedRpkStore=#failedTrustedRpkStore", PORT5)
                    .to("mock:result");

                from("direct:rpkclientauth")
                    .toF("coaps://localhost:%d/TestResource?trustedRpkStore=#trustedRpkStore&privateKey=#privateKey&publicKey=#publicKey", PORT6)
                    .to("mock:result");

                from("direct:psk")
                    .toF("coaps://localhost:%d/TestResource?pskStore=#pskStore", PORT7)
                    .to("mock:result");

                from("direct:pskciphersuite")
                    .toF("coaps://localhost:%d/TestResource?pskStore=#pskStore&cipherSuites=TLS_PSK_WITH_AES_128_CBC_SHA256", PORT7)
                    .to("mock:result");

                from("direct:pskx509")
                    .toF("coaps://localhost:%d/TestResource?pskStore=#pskStore&trustStoreParameters=#trustParams", PORT8)
                    .to("mock:result");
            }
        };
    }

    protected void sendBodyAndHeader(String endpointUri, final Object body, String headerName, String headerValue) {
        template.send(endpointUri, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader(headerName, headerValue);
            }
        });
    }
}
