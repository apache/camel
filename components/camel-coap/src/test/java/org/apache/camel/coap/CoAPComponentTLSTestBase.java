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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.KeyGenerator;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.californium.scandium.dtls.rpkstore.TrustedRpkStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class CoAPComponentTLSTestBase extends CamelTestSupport {

    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private static final int PORT2 = AvailablePortFinder.getNextAvailable();
    private static final int PORT3 = AvailablePortFinder.getNextAvailable();
    private static final int PORT4 = AvailablePortFinder.getNextAvailable();
    private static final int PORT5 = AvailablePortFinder.getNextAvailable();
    private static final int PORT6 = AvailablePortFinder.getNextAvailable();
    private static final int PORT7 = AvailablePortFinder.getNextAvailable();
    private static final int PORT8 = AvailablePortFinder.getNextAvailable();

    @ParameterizedTest
    @ValueSource(strings = { "direct:start", "direct:selfsigned", "direct:clientauth", "direct:ciphersuites" })
    @DisplayName("Test calls with/without certificates")
    void testCall(String endpointUri) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(CoAPConstants.CONTENT_TYPE,
                MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader(endpointUri, "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testSuccessfulCall() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello Camel CoAP");
        mock.expectedHeaderReceived(CoAPConstants.CONTENT_TYPE,
                MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
        mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
        sendBodyAndHeader("direct:start", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        MockEndpoint.assertIsSatisfied(context);
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:notruststore", "direct:failedtrust", "direct:failedclientauth" })
    @DisplayName("Tests different types of trust stores")
    void testTrustStores(String endpointUri) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        sendBodyAndHeader(endpointUri, "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testRawPublicKey() throws Exception {
        if (isRPKSupported()) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            mock.expectedBodiesReceived("Hello Camel CoAP");
            mock.expectedHeaderReceived(CoAPConstants.CONTENT_TYPE,
                    MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
            mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
            sendBodyAndHeader("direct:rpk", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    void testRawPublicKeyNoTruststore() throws Exception {
        if (isRPKSupported()) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMessageCount(0);
            sendBodyAndHeader("direct:rpknotruststore", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    void testRawPublicKeyFailedTrust() throws Exception {
        if (isRPKSupported()) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMessageCount(0);
            sendBodyAndHeader("direct:rpkfailedtrust", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    void testRawPublicKeyClientAuth() throws Exception {
        if (isRPKSupported()) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            mock.expectedBodiesReceived("Hello Camel CoAP");
            mock.expectedHeaderReceived(CoAPConstants.CONTENT_TYPE,
                    MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
            mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
            sendBodyAndHeader("direct:rpkclientauth", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    void testPreSharedKey() throws Exception {
        if (isPSKSupported()) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            mock.expectedBodiesReceived("Hello Camel CoAP");
            mock.expectedHeaderReceived(CoAPConstants.CONTENT_TYPE,
                    MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
            mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
            sendBodyAndHeader("direct:psk", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    void testPreSharedKeyCipherSuite() throws Exception {
        if (isPSKSupported()) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            mock.expectedBodiesReceived("Hello Camel CoAP");
            mock.expectedHeaderReceived(CoAPConstants.CONTENT_TYPE,
                    MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
            mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
            sendBodyAndHeader("direct:pskciphersuite", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    @Test
    void testPreSharedKeyX509() throws Exception {
        if (isPSKSupported()) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            mock.expectedBodiesReceived("Hello Camel CoAP");
            mock.expectedHeaderReceived(CoAPConstants.CONTENT_TYPE,
                    MediaTypeRegistry.toString(MediaTypeRegistry.APPLICATION_OCTET_STREAM));
            mock.expectedHeaderReceived(CoAPConstants.COAP_RESPONSE_CODE, CoAP.ResponseCode.CONTENT.toString());
            sendBodyAndHeader("direct:pskx509", "Camel CoAP", CoAPConstants.COAP_METHOD, "POST");
            MockEndpoint.assertIsSatisfied(context);
        }
    }

    protected abstract String getProtocol();

    protected abstract boolean isPSKSupported();

    protected abstract boolean isRPKSupported();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        registerTLSConfiguration(context);

        return new RouteBuilder() {
            @Override
            public void configure() {

                fromF(getProtocol() + "://localhost:%d/TestResource?sslContextParameters=#serviceSSLContextParameters", PORT)
                        .transform(body().prepend("Hello "));

                fromF(getProtocol()
                      + "://localhost:%d/TestResource?alias=selfsigned&sslContextParameters=#selfSignedServiceSSLContextParameters",
                        PORT2)
                        .transform(body().prepend("Hello "));

                fromF(getProtocol()
                      + "://localhost:%d/TestResource?sslContextParameters=#clientAuthServiceSSLContextParameters", PORT3)
                        .transform(body().prepend("Hello "));

                fromF(getProtocol()
                      + "://localhost:%d/TestResource?sslContextParameters=#serviceSSLContextParameters&cipherSuites=TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8",
                        PORT4)
                        .transform(body().prepend("Hello "));

                from("direct:start")
                        .toF(getProtocol() + "://localhost:%d/TestResource?sslContextParameters=#clientSSLContextParameters",
                                PORT)
                        .to("mock:result");

                from("direct:notruststore").toF(getProtocol() + "://localhost:%d/TestResource", PORT).to("mock:result");

                from("direct:failedtrust")
                        .toF(getProtocol() + "://localhost:%d/TestResource?sslContextParameters=#clientSSLContextParameters2",
                                PORT)
                        .to("mock:result");

                from("direct:selfsigned")
                        .toF(getProtocol()
                             + "://localhost:%d/TestResource?sslContextParameters=#selfSignedClientSSLContextParameters", PORT2)
                        .to("mock:result");

                from("direct:clientauth")
                        .toF(getProtocol()
                             + "://localhost:%d/TestResource?sslContextParameters=#clientAuthClientSSLContextParameters", PORT3)
                        .to("mock:result");

                from("direct:failedclientauth")
                        .toF(getProtocol()
                             + "://localhost:%d/TestResource?sslContextParameters=#clientAuthClientSSLContextParameters2",
                                PORT3)
                        .to("mock:result");

                from("direct:ciphersuites")
                        .toF(getProtocol()
                             + "://localhost:%d/TestResource?sslContextParameters=#clientSSLContextParameters&cipherSuites=TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8",
                                PORT4)
                        .to("mock:result");

                if (isRPKSupported()) {
                    fromF(getProtocol() + "://localhost:%d/TestResource?privateKey=#privateKey&publicKey=#publicKey", PORT5)
                            .transform(body().prepend("Hello "));

                    fromF(getProtocol()
                          + "://localhost:%d/TestResource?privateKey=#privateKey&publicKey=#publicKey&clientAuthentication=REQUIRE&trustedRpkStore=#trustedRpkStore",
                            PORT6).transform(body().prepend("Hello "));

                    from("direct:rpk")
                            .toF(getProtocol() + "://localhost:%d/TestResource?trustedRpkStore=#trustedRpkStore", PORT5)
                            .to("mock:result");

                    from("direct:rpknotruststore").toF(getProtocol() + "://localhost:%d/TestResource", PORT5).to("mock:result");

                    from("direct:rpkfailedtrust")
                            .toF(getProtocol() + "://localhost:%d/TestResource?trustedRpkStore=#failedTrustedRpkStore", PORT5)
                            .to("mock:result");

                    from("direct:rpkclientauth")
                            .toF(getProtocol()
                                 + "://localhost:%d/TestResource?trustedRpkStore=#trustedRpkStore&privateKey=#privateKey&publicKey=#publicKey",
                                    PORT6)
                            .to("mock:result");
                }

                if (isPSKSupported()) {
                    fromF(getProtocol() + "://localhost:%d/TestResource?pskStore=#pskStore", PORT7)
                            .transform(body().prepend("Hello "));

                    fromF(getProtocol()
                          + "://localhost:%d/TestResource?sslContextParameters=#serviceSSLContextParameters&pskStore=#pskStore",
                            PORT8)
                            .transform(body().prepend("Hello "));

                    from("direct:psk").toF(getProtocol() + "://localhost:%d/TestResource?pskStore=#pskStore", PORT7)
                            .to("mock:result");

                    from("direct:pskciphersuite")
                            .toF(getProtocol()
                                 + "://localhost:%d/TestResource?pskStore=#pskStore&cipherSuites=TLS_PSK_WITH_AES_128_GCM_SHA256",
                                    PORT7)
                            .to("mock:result");

                    from("direct:pskx509")
                            .toF(getProtocol()
                                 + "://localhost:%d/TestResource?pskStore=#pskStore&sslContextParameters=#clientSSLContextParameters",
                                    PORT8)
                            .to("mock:result");
                }

            }
        };
    }

    private void registerTLSConfiguration(CamelContext context) throws GeneralSecurityException, IOException {
        KeyStoreParameters serviceKeystoreParameters = new KeyStoreParameters();
        serviceKeystoreParameters.setCamelContext(context);
        serviceKeystoreParameters.setResource("service.jks");
        serviceKeystoreParameters.setPassword("security");

        KeyStoreParameters selfSignedKeyStoreParameters = new KeyStoreParameters();
        selfSignedKeyStoreParameters.setCamelContext(context);
        selfSignedKeyStoreParameters.setResource("selfsigned.jks");
        selfSignedKeyStoreParameters.setPassword("security");

        KeyStoreParameters clientKeystoreParameters = new KeyStoreParameters();
        clientKeystoreParameters.setCamelContext(context);
        clientKeystoreParameters.setResource("client.jks");
        clientKeystoreParameters.setPassword("security");

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setCamelContext(context);
        truststoreParameters.setResource("truststore.jks");
        truststoreParameters.setPassword("storepass");

        KeyStoreParameters truststoreParameters2 = new KeyStoreParameters();
        truststoreParameters2.setCamelContext(context);
        truststoreParameters2.setResource("truststore2.jks");
        truststoreParameters2.setPassword("storepass");

        SSLContextParameters serviceSSLContextParameters = new SSLContextParameters();
        serviceSSLContextParameters.setCamelContext(context);
        KeyManagersParameters serviceSSLKeyManagers = new KeyManagersParameters();
        serviceSSLKeyManagers.setKeyPassword("security");
        serviceSSLKeyManagers.setKeyStore(serviceKeystoreParameters);
        serviceSSLContextParameters.setKeyManagers(serviceSSLKeyManagers);

        SSLContextParameters selfSignedServiceSSLContextParameters = new SSLContextParameters();
        selfSignedServiceSSLContextParameters.setCamelContext(context);
        KeyManagersParameters selfSignedServiceSSLKeyManagers = new KeyManagersParameters();
        selfSignedServiceSSLKeyManagers.setKeyPassword("security");
        selfSignedServiceSSLKeyManagers.setKeyStore(selfSignedKeyStoreParameters);
        selfSignedServiceSSLContextParameters.setKeyManagers(selfSignedServiceSSLKeyManagers);

        SSLContextParameters clientAuthServiceSSLContextParameters = new SSLContextParameters();
        clientAuthServiceSSLContextParameters.setCamelContext(context);
        KeyManagersParameters clientAuthServiceSSLKeyManagers = new KeyManagersParameters();
        clientAuthServiceSSLKeyManagers.setKeyPassword("security");
        clientAuthServiceSSLKeyManagers.setKeyStore(serviceKeystoreParameters);
        clientAuthServiceSSLContextParameters.setKeyManagers(clientAuthServiceSSLKeyManagers);
        TrustManagersParameters clientAuthServiceSSLTrustManagers = new TrustManagersParameters();
        clientAuthServiceSSLTrustManagers.setKeyStore(truststoreParameters);
        clientAuthServiceSSLContextParameters.setTrustManagers(clientAuthServiceSSLTrustManagers);
        SSLContextServerParameters clientAuthSSLContextServerParameters = new SSLContextServerParameters();
        clientAuthSSLContextServerParameters.setClientAuthentication("REQUIRE");
        clientAuthServiceSSLContextParameters.setServerParameters(clientAuthSSLContextServerParameters);

        SSLContextParameters clientSSLContextParameters = new SSLContextParameters();
        clientSSLContextParameters.setCamelContext(context);
        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientSSLContextParameters.setTrustManagers(clientSSLTrustManagers);

        SSLContextParameters clientSSLContextParameters2 = new SSLContextParameters();
        clientSSLContextParameters2.setCamelContext(context);
        TrustManagersParameters clientSSLTrustManagers2 = new TrustManagersParameters();
        clientSSLTrustManagers2.setKeyStore(truststoreParameters2);
        clientSSLContextParameters2.setTrustManagers(clientSSLTrustManagers2);

        SSLContextParameters clientAuthClientSSLContextParameters = new SSLContextParameters();
        clientAuthClientSSLContextParameters.setCamelContext(context);
        TrustManagersParameters clientAuthClientSSLTrustManagers = new TrustManagersParameters();
        clientAuthClientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientAuthClientSSLContextParameters.setTrustManagers(clientAuthClientSSLTrustManagers);
        KeyManagersParameters clientAuthClientSSLKeyManagers = new KeyManagersParameters();
        clientAuthClientSSLKeyManagers.setKeyPassword("security");
        clientAuthClientSSLKeyManagers.setKeyStore(clientKeystoreParameters);
        clientAuthClientSSLContextParameters.setKeyManagers(clientAuthClientSSLKeyManagers);

        SSLContextParameters clientAuthClientSSLContextParameters2 = new SSLContextParameters();
        clientAuthClientSSLContextParameters2.setCamelContext(context);
        TrustManagersParameters clientAuthClientSSLTrustManagers2 = new TrustManagersParameters();
        clientAuthClientSSLTrustManagers2.setKeyStore(truststoreParameters2);
        clientAuthClientSSLContextParameters2.setTrustManagers(clientAuthClientSSLTrustManagers2);
        KeyManagersParameters clientAuthClientSSLKeyManagers2 = new KeyManagersParameters();
        clientAuthClientSSLKeyManagers2.setKeyPassword("security");
        clientAuthClientSSLKeyManagers2.setKeyStore(clientKeystoreParameters);
        clientAuthClientSSLContextParameters2.setKeyManagers(clientAuthClientSSLKeyManagers2);

        SSLContextParameters selfSignedClientSSLContextParameters = new SSLContextParameters();
        selfSignedClientSSLContextParameters.setCamelContext(context);
        TrustManagersParameters selfSignedClientSSLTrustManagers = new TrustManagersParameters();
        selfSignedClientSSLTrustManagers.setKeyStore(selfSignedKeyStoreParameters);
        selfSignedClientSSLContextParameters.setTrustManagers(selfSignedClientSSLTrustManagers);

        KeyStore keyStore = serviceKeystoreParameters.createKeyStore();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey("service", "security".toCharArray());
        PublicKey publicKey = keyStore.getCertificate("service").getPublicKey();

        TrustedRpkStore trustedRpkStore = id -> {
            return true;
        };
        TrustedRpkStore failedTrustedRpkStore = id -> {
            return false;
        };
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        PskStore pskStore = new StaticPskStore("some-identity", keyGenerator.generateKey().getEncoded());

        context.getRegistry().bind("serviceSSLContextParameters", serviceSSLContextParameters);
        context.getRegistry().bind("selfSignedServiceSSLContextParameters", selfSignedServiceSSLContextParameters);
        context.getRegistry().bind("clientAuthServiceSSLContextParameters", clientAuthServiceSSLContextParameters);
        context.getRegistry().bind("clientSSLContextParameters", clientSSLContextParameters);
        context.getRegistry().bind("clientSSLContextParameters2", clientSSLContextParameters2);
        context.getRegistry().bind("clientAuthClientSSLContextParameters", clientAuthClientSSLContextParameters);
        context.getRegistry().bind("clientAuthClientSSLContextParameters2", clientAuthClientSSLContextParameters2);
        context.getRegistry().bind("selfSignedClientSSLContextParameters", selfSignedClientSSLContextParameters);

        context.getRegistry().bind("privateKey", privateKey);
        context.getRegistry().bind("publicKey", publicKey);
        context.getRegistry().bind("trustedRpkStore", trustedRpkStore);
        context.getRegistry().bind("failedTrustedRpkStore", failedTrustedRpkStore);
        context.getRegistry().bind("pskStore", pskStore);
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
