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

import javax.net.ssl.SSLContext;

import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.tcp.netty.TcpClientConnector;
import org.eclipse.californium.elements.tcp.netty.TlsClientConnector;

/**
 * Test the CoAP Rest Component with TCP + TLS
 */
public class CoAPRestComponentTCPTLSTest extends CoAPRestComponentTestBase {

    @Override
    protected String getProtocol() {
        return "coaps+tcp";
    }

    @Override
    protected void decorateClient(CoapClient client) throws GeneralSecurityException, IOException {

        Configuration config = Configuration.createStandardWithoutFile();

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("truststore.jks");
        truststoreParameters.setPassword("storepass");

        SSLContextParameters clientSSLContextParameters = new SSLContextParameters();
        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientSSLContextParameters.setTrustManagers(clientSSLTrustManagers);

        SSLContext sslContext = clientSSLContextParameters.createSSLContext(context);
        TcpClientConnector tcpConnector = new TlsClientConnector(sslContext, config);

        CoapEndpoint.Builder tcpBuilder = new CoapEndpoint.Builder();
        tcpBuilder.setConnector(tcpConnector);

        client.setEndpoint(tcpBuilder.build());
    }

    @Override
    protected void decorateRestConfiguration(RestConfigurationDefinition restConfig) {
        KeyStoreParameters keystoreParameters = new KeyStoreParameters();
        keystoreParameters.setResource("service.jks");
        keystoreParameters.setPassword("security");

        SSLContextParameters serviceSSLContextParameters = new SSLContextParameters();
        KeyManagersParameters serviceSSLKeyManagers = new KeyManagersParameters();
        serviceSSLKeyManagers.setKeyPassword("security");
        serviceSSLKeyManagers.setKeyStore(keystoreParameters);
        serviceSSLContextParameters.setKeyManagers(serviceSSLKeyManagers);

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("truststore.jks");
        truststoreParameters.setPassword("storepass");

        SSLContextParameters clientSSLContextParameters = new SSLContextParameters();
        TrustManagersParameters clientSSLTrustManagers = new TrustManagersParameters();
        clientSSLTrustManagers.setKeyStore(truststoreParameters);
        clientSSLContextParameters.setTrustManagers(clientSSLTrustManagers);

        context.getRegistry().bind("serviceSSLContextParameters", serviceSSLContextParameters);
        context.getRegistry().bind("clientSSLContextParameters", clientSSLContextParameters);

        restConfig.endpointProperty("sslContextParameters", "#serviceSSLContextParameters");
    }

    @Override
    protected String getClientURI() {
        return super.getClientURI() + "?sslContextParameters=#clientSSLContextParameters";
    }

}
