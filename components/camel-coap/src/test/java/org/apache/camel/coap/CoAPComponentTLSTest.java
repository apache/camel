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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.scandium.dtls.rpkstore.TrustedRpkStore;
import org.junit.Test;

public class CoAPComponentTLSTest extends CamelTestSupport {

    protected static final int PORT = AvailablePortFinder.getNextAvailable();
    protected static final int PORT2 = AvailablePortFinder.getNextAvailable();
    protected static final int PORT3 = AvailablePortFinder.getNextAvailable();
    protected static final int PORT4 = AvailablePortFinder.getNextAvailable();
    protected static final int PORT5 = AvailablePortFinder.getNextAvailable();

    @Produce(uri = "direct:start")
    protected ProducerTemplate sender;

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

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("service.jks");
        keystoreParameters.setPassword("security");

        KeyStore keyStore = keystoreParameters.createKeyStore();
        PrivateKey privateKey =
            (PrivateKey)keyStore.getKey("service", "security".toCharArray());
        PublicKey publicKey =
            keyStore.getCertificate("service").getPublicKey();

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

        TrustedRpkStore trustedRpkStore = id -> { return true;};

        registry.bind("keyParams", keystoreParameters);
        registry.bind("keyParams2", keystoreParameters2);
        registry.bind("keyParams3", keystoreParameters3);
        registry.bind("trustParams", truststoreParameters);
        registry.bind("trustParams2", truststoreParameters2);
        registry.bind("privateKey", privateKey);
        registry.bind("publicKey", publicKey);
        registry.bind("trustedRpkStore", trustedRpkStore);

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&"
                      + "keyStoreParameters=#keyParams", PORT)
                    .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?alias=selfsigned&password=security&"
                    + "keyStoreParameters=#keyParams2", PORT2)
                  .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&"
                    + "trustStoreParameters=#trustParams&"
                    + "keyStoreParameters=#keyParams&clientAuthentication=REQUIRE", PORT3)
                  .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&"
                    + "keyStoreParameters=#keyParams&cipherSuites=TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8", PORT4)
                  .transform(body().prepend("Hello "));

                fromF("coaps://localhost:%d/TestResource?alias=service&password=security&"
                    + "privateKey=#privateKey&publicKey=#publicKey", PORT5)
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
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams&"
                         + "keyStoreParameters=#keyParams3&alias=client&password=security", PORT3)
                    .to("mock:result");

                from("direct:failedclientauth")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams&"
                         + "keyStoreParameters=#keyParams2&alias=selfsigned&password=security", PORT3)
                    .to("mock:result");

                from("direct:ciphersuites")
                    .toF("coaps://localhost:%d/TestResource?trustStoreParameters=#trustParams&"
                         + "cipherSuites=TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8", PORT4)
                    .to("mock:result");

                from("direct:rpk")
                    .toF("coaps://localhost:%d/TestResource?trustedRpkStore=#trustedRpkStore", PORT5)
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
