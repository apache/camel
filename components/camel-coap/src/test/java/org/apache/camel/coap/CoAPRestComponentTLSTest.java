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
import java.security.cert.Certificate;

import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;

/**
 * Test the CoAP Rest Component with UDP + TLS
 */
public class CoAPRestComponentTLSTest extends CoAPRestComponentTestBase {

    @Override
    protected String getProtocol() {
        return "coaps";
    }

    @Override
    protected void decorateClient(CoapClient client) throws GeneralSecurityException, IOException {

        DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder();
        builder.setClientOnly();

        KeyStoreParameters truststoreParameters = new KeyStoreParameters();
        truststoreParameters.setResource("truststore.jks");
        truststoreParameters.setPassword("storepass");

        KeyStore trustStore = truststoreParameters.createKeyStore();
        Certificate[] certs = new Certificate[] {trustStore.getCertificate(trustStore.aliases().nextElement())};
        builder.setTrustStore(certs);

        CoapEndpoint.Builder coapBuilder = new CoapEndpoint.Builder();
        coapBuilder.setConnector(new DTLSConnector(builder.build()));

        client.setEndpoint(coapBuilder.build());
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
