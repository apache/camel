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
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.eclipse.californium.core.CoapServer;

/**
 * The coap component is used for sending and receiving messages from COAP capable devices.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "coap", title = "CoAP", syntax = "coap:uri", label = "iot")
public class CoAPEndpoint extends DefaultEndpoint {
    @UriPath
    private URI uri;
    @UriParam(label = "consumer")
    private String coapMethodRestrict;
    
    @UriParam
    private KeyStoreParameters keyStoreParameters;
    
    @UriParam
    private KeyStore keystore;
    
    @UriParam
    private KeyStoreParameters trustStoreParameters;
    
    @UriParam
    private KeyStore truststore;
    
    @UriParam
    private String alias;
    
    @UriParam(label = "security", javaType = "java.lang.String", secret = true)
    private char[] password;
    
    @UriParam
    private String cipherSuites;
    
    private String[] configuredCipherSuites;
        
    private CoAPComponent component;
    
    public CoAPEndpoint(String uri, CoAPComponent component) {
        super(uri, component);
        try {
            this.uri = new URI(uri);
        } catch (java.net.URISyntaxException use) {
            this.uri = null;
        }
        this.component = component;
    }

    public void setCoapMethodRestrict(String coapMethodRestrict) {
        this.coapMethodRestrict = coapMethodRestrict;
    }

    /**
     * Comma separated list of methods that the CoAP consumer will bind to. The default is to bind to all methods (DELETE, GET, POST, PUT).
     */
    public String getCoapMethodRestrict() {
        return this.coapMethodRestrict;
    }

    public Producer createProducer() throws Exception {
        return new CoAPProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CoAPConsumer(this, processor);
    }

    public void setUri(URI u) {
        uri = u;
    }
    
    /**
     * The URI for the CoAP endpoint
     */
    public URI getUri() {
        return uri;
    }

    public CoapServer getCoapServer() {
        return component.getServer(getUri().getPort(), this);
    }
    
    /**
     * The KeyStoreParameters object to use with TLS to configure the keystore. Alternatively, a "keystore" 
     * parameter can be directly configured instead. An alias and password should also be configured on the route definition.
     */
    public KeyStoreParameters getKeyStoreParameters() {
        return keyStoreParameters;
    }

    public void setKeyStoreParameters(KeyStoreParameters keyStoreParameters) throws GeneralSecurityException, IOException {
        this.keyStoreParameters = keyStoreParameters;
        if (keyStoreParameters != null) {
            this.keystore = keyStoreParameters.createKeyStore();
        }
    }
    
    /**
     * The KeyStoreParameters object to use with TLS to configure the truststore. Alternatively, a "truststore" 
     * object can be directly configured instead. All certificates in the truststore are used to establish trust.
     */
    public KeyStoreParameters getTrustStoreParameters() {
        return trustStoreParameters;
    }

    public void setTrustStoreParameters(KeyStoreParameters trustStoreParameters) throws GeneralSecurityException, IOException {
        this.trustStoreParameters = trustStoreParameters;
        if (trustStoreParameters != null) {
            this.truststore = trustStoreParameters.createKeyStore();
        }
    }
    
    /**
     * Gets the TLS key store. Alternatively, a KeyStoreParameters object can be configured instead.
     * An alias and password should also be configured on the route definition.
     */
    public KeyStore getKeystore() {
        return keystore;
    }

    /**
     * Sets the TLS key store. Alternatively, a KeyStoreParameters object can be configured instead.
     * An alias and password should also be configured on the route definition.
     */
    public void setKeystore(KeyStore keystore) {
        this.keystore = keystore;
    }
    
    /**
     * Gets the TLS trust store. Alternatively, a "trustStoreParameters" object can be configured instead.
     * All certificates in the truststore are used to establish trust.
     */
    public KeyStore getTruststore() {
        return truststore;
    }

    /**
     * Sets the TLS trust store. Alternatively, a "trustStoreParameters" object can be configured instead.
     * All certificates in the truststore are used to establish trust.
     */
    public void setTruststore(KeyStore truststore) {
        this.truststore = truststore;
    }
    
    /**
     * Gets the alias used to query the KeyStore for the private key and certificate.
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Sets the alias used to query the KeyStore for the private key and certificate.
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }
    
    /**
     * Gets the password used to access an aliased {@link PrivateKey} in the KeyStore.
     */
    public char[] getPassword() {
        return password;
    }

    /**
     * Sets the password used to access an aliased {@link PrivateKey} in the KeyStore.
     */
    public void setPassword(char[] password) {
        this.password = password;
    }
    
    /**
     * Gets the cipherSuites String. This is a comma separated String of ciphersuites to configure.
     */
    public String getCipherSuites() {
        return cipherSuites;
    }

    /**
     * Sets the cipherSuites String. This is a comma separated String of ciphersuites to configure.
     */
    public void setCipherSuites(String cipherSuites) {
        this.cipherSuites = cipherSuites;
        if (cipherSuites != null) {
            configuredCipherSuites = cipherSuites.split(",");
        }
    }
    
    public String[] getConfiguredCipherSuites() {
        return configuredCipherSuites;
    }
    
    public Certificate[] getTrustedCerts() throws KeyStoreException {
        Enumeration<String> aliases = truststore.aliases();
        List<Certificate> trustCerts = new ArrayList<>();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            X509Certificate cert = (X509Certificate) truststore.getCertificate(alias);
            if (cert != null) {
                trustCerts.add(cert);
            }
        }
        
        return trustCerts.toArray(new Certificate[0]);
    }
}
