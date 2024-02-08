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
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.ClientAuthentication;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.config.CertificateAuthenticationMode;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.tcp.netty.TcpClientConnector;
import org.eclipse.californium.elements.tcp.netty.TlsClientConnector;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConfig;
import org.eclipse.californium.scandium.config.DtlsConfig.DtlsRole;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.dtls.x509.NewAdvancedCertificateVerifier;
import org.eclipse.californium.scandium.dtls.x509.SingleCertificateProvider;
import org.eclipse.californium.scandium.dtls.x509.StaticNewAdvancedCertificateVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_CERTIFICATE_TYPES;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_CIPHER_SUITES;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_CLIENT_AUTHENTICATION_MODE;
import static org.eclipse.californium.scandium.config.DtlsConfig.DTLS_RECOMMENDED_CIPHER_SUITES_ONLY;

/**
 * Send and receive messages to/from COAP capable devices.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "coap,coaps,coap+tcp,coaps+tcp", title = "CoAP", syntax = "coap:uri",
             category = { Category.IOT }, headersClass = CoAPConstants.class)
public class CoAPEndpoint extends DefaultEndpoint {
    final static Logger LOGGER = LoggerFactory.getLogger(CoAPEndpoint.class);
    @UriPath
    private URI uri;
    @UriParam(label = "consumer", enums = "DELETE,GET,POST,PUT")
    private String coapMethodRestrict;
    @UriParam(label = "security", secret = true)
    private PrivateKey privateKey;
    @UriParam(label = "security")
    private PublicKey publicKey;
    @UriParam(label = "security")
    private NewAdvancedCertificateVerifier advancedCertificateVerifier;
    @UriParam(label = "security")
    private AdvancedPskStore advancedPskStore;
    @UriParam(label = "security")
    private String cipherSuites;
    private transient String[] configuredCipherSuites;
    @UriParam(label = "security")
    private CertificateAuthenticationMode clientAuthentication;
    @UriParam(label = "security", enums = "NONE,WANT,REQUIRE")
    private String alias;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "security", defaultValue = "true")
    private boolean recommendedCipherSuitesOnly = true;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean observe;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean observable;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean notify;

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
     * Comma separated list of methods that the CoAP consumer will bind to. The default is to bind to all methods
     * (DELETE, GET, POST, PUT).
     */
    public String getCoapMethodRestrict() {
        return this.coapMethodRestrict;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (isNotify()) {
            return new CoAPNotifier(this);
        } else {
            return new CoAPProducer(this);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final Consumer consumer;
        if (isObserve()) {
            consumer = new CoAPObserver(this, processor);
        } else {
            consumer = new CoAPConsumer(this, processor);
        }
        configureConsumer(consumer);
        return consumer;
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

    public CamelCoapResource getCamelCoapResource(String path) throws IOException, GeneralSecurityException {
        Iterator<String> pathSegments = CoAPHelper.getPathSegmentsFromPath(path).iterator();
        if (!pathSegments.hasNext()) {
            return null;
        }

        Resource current = getCoapServer().getRoot();
        while (pathSegments.hasNext() && current != null) {
            current = current.getChild(pathSegments.next());
        }
        return (CamelCoapResource) current;
    }

    public List<String> getPathSegmentsFromURI() {
        return CoAPHelper.getPathSegmentsFromPath(getUri().getPath());
    }

    public CoapServer getCoapServer() throws IOException, GeneralSecurityException {
        return component.getServer(getUri().getPort(), this);
    }

    /**
     * Gets the alias used to query the KeyStore for the private key and certificate. This parameter is used when we are
     * enabling TLS with certificates on the service side, and similarly on the client side when TLS is used with
     * certificates and client authentication. If the parameter is not specified then the default behavior is to use the
     * first alias in the keystore that contains a key entry. This configuration parameter does not apply to configuring
     * TLS via a Raw Public Key or a Pre-Shared Key.
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Sets the alias used to query the KeyStore for the private key and certificate. This parameter is used when we are
     * enabling TLS with certificates on the service side, and similarly on the client side when TLS is used with
     * certificates and client authentication. If the parameter is not specified then the default behavior is to use the
     * first alias in the keystore that contains a key entry. This configuration parameter does not apply to configuring
     * TLS via a Raw Public Key or a Pre-Shared Key.
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    public boolean isObserve() {
        return observe;
    }

    /**
     * Send an observe request from a source endpoint, based on RFC 7641.
     */
    public void setObserve(boolean observe) {
        this.observe = observe;
    }

    public boolean isObservable() {
        return observable;
    }

    /**
     * Make CoAP resource observable for source endpoint, based on RFC 7641.
     */
    public void setObservable(boolean observable) {
        this.observable = observable;
    }

    public boolean isNotify() {
        return notify;
    }

    /**
     * Notify observers that the resource of this URI has changed, based on RFC 7641. Use this flag on a destination
     * endpoint, with a URI that matches an existing source endpoint URI.
     */
    public void setNotify(boolean notify) {
        this.notify = notify;
    }

    /**
     * Get the SSLContextParameters object for setting up TLS. This is required for coaps+tcp, and for coaps when we are
     * using certificates for TLS (as opposed to RPK or PKS).
     */
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * Set the SSLContextParameters object for setting up TLS. This is required for coaps+tcp, and for coaps when we are
     * using certificates for TLS (as opposed to RPK or PKS).
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    /**
     * Get the AdvancedCertificateVerifier to use to determine trust in raw public keys.
     */
    public NewAdvancedCertificateVerifier getAdvancedCertificateVerifier() {
        return advancedCertificateVerifier;
    }

    /**
     * Set the AdvancedCertificateVerifier to use to determine trust in raw public keys.
     */
    public void setAdvancedCertificateVerifier(NewAdvancedCertificateVerifier advancedCertificateVerifier) {
        this.advancedCertificateVerifier = advancedCertificateVerifier;
    }

    /**
     * Get the AdvancedPskStore to use for pre-shared key.
     */
    public AdvancedPskStore getAdvancedPskStore() {
        return advancedPskStore;
    }

    /**
     * Set the AdvancedPskStore to use for pre-shared key.
     */
    public void setAdvancedPskStore(AdvancedPskStore advancedPskStore) {
        this.advancedPskStore = advancedPskStore;
    }

    /**
     * Get the configured private key for use with Raw Public Key.
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * Set the configured private key for use with Raw Public Key.
     */
    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * Get the configured public key for use with Raw Public Key.
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Set the configured public key for use with Raw Public Key.
     */
    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Gets the cipherSuites String. This is a comma separated String of ciphersuites to configure. If it is not
     * specified, then it falls back to getting the ciphersuites from the sslContextParameters object.
     */
    public String getCipherSuites() {
        return cipherSuites;
    }

    /**
     * Sets the cipherSuites String. This is a comma separated String of ciphersuites to configure. If it is not
     * specified, then it falls back to getting the ciphersuites from the sslContextParameters object.
     */
    public void setCipherSuites(String cipherSuites) {
        this.cipherSuites = cipherSuites;
        if (cipherSuites != null) {
            configuredCipherSuites = cipherSuites.split(",");
        }
    }

    private String[] getConfiguredCipherSuites() {
        if (configuredCipherSuites != null) {
            return configuredCipherSuites;
        } else if (sslContextParameters != null && sslContextParameters.getCipherSuites() != null) {
            return sslContextParameters.getCipherSuites().getCipherSuite().toArray(new String[0]);
        }
        return null;
    }

    /**
     * Gets the configuration options for server-side client-authentication requirements. The value is either null or
     * one of NONE, WANT, REQUIRE. If this value is not specified, then it falls back to checking the
     * sslContextParameters.getServerParameters().getClientAuthentication() value.
     */
    public CertificateAuthenticationMode getClientAuthentication() {
        return clientAuthentication;
    }

    /**
     * Sets the configuration options for server-side client-authentication requirements. The value must be one of NONE,
     * WANT, REQUIRE. If this value is not specified, then it falls back to checking the
     * sslContextParameters.getServerParameters().getClientAuthentication() value.
     */
    public void setClientAuthentication(CertificateAuthenticationMode clientAuthentication) {
        this.clientAuthentication = clientAuthentication;
    }

    public boolean isRecommendedCipherSuitesOnly() {
        return recommendedCipherSuitesOnly;
    }

    /**
     * The CBC cipher suites are not recommended. If you want to use them, you first need to set the
     * recommendedCipherSuitesOnly option to false.
     */
    public void setRecommendedCipherSuitesOnly(boolean recommendedCipherSuitesOnly) {
        this.recommendedCipherSuitesOnly = recommendedCipherSuitesOnly;
    }

    public boolean isClientAuthenticationRequired() {
        CertificateAuthenticationMode clientAuth = clientAuthentication;
        if (clientAuth == null && sslContextParameters != null && sslContextParameters.getServerParameters() != null) {
            clientAuth = CertificateAuthenticationMode
                    .valueOf(sslContextParameters.getServerParameters().getClientAuthentication());
        }

        return clientAuth == CertificateAuthenticationMode.NEEDED;
    }

    public boolean isClientAuthenticationWanted() {
        CertificateAuthenticationMode clientAuth = clientAuthentication;
        if (clientAuth == null && sslContextParameters != null && sslContextParameters.getServerParameters() != null) {
            clientAuth = CertificateAuthenticationMode
                    .valueOf(sslContextParameters.getServerParameters().getClientAuthentication());
        }

        return clientAuth != null && ClientAuthentication.valueOf(String.valueOf(clientAuth)) == ClientAuthentication.WANT;
    }

    /**
     * Get all the certificates contained in the sslContextParameters truststore
     */
    private X509Certificate[] getTrustedCerts() throws GeneralSecurityException, IOException {
        if (sslContextParameters != null && sslContextParameters.getTrustManagers() != null) {
            KeyStore trustStore = sslContextParameters.getTrustManagers().getKeyStore().createKeyStore();
            Enumeration<String> aliases = trustStore.aliases();
            List<Certificate> trustCerts = new ArrayList<>();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                X509Certificate cert = (X509Certificate) trustStore.getCertificate(alias);
                if (cert != null) {
                    trustCerts.add(cert);
                }
            }

            return trustCerts.toArray(new X509Certificate[0]);
        }

        return new X509Certificate[0];
    }

    public static boolean enableDTLS(URI uri) {
        return "coaps".equals(uri.getScheme());
    }

    public static boolean enableTCP(URI uri) {
        return uri.getScheme().endsWith("+tcp");
    }

    public DTLSConnector createDTLSConnector(InetSocketAddress address, boolean client) throws IOException {
        Configuration cfg;
        try {
            cfg = Configuration.getStandard();
        } catch (Exception e) {
            // in case error loading standard file
            cfg = new Configuration();
        }
        DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder(cfg);
        if (client) {
            if (advancedCertificateVerifier == null && sslContextParameters == null && advancedPskStore == null) {
                throw new IllegalStateException(
                        "Either an advancedCertificateVerifier, sslContextParameters or advancedPskStore object "
                                                + "must be configured for a TLS client");
            }
            builder.set(DTLS_RECOMMENDED_CIPHER_SUITES_ONLY, isRecommendedCipherSuitesOnly());
            builder.set(DtlsConfig.DTLS_ROLE, DtlsRole.CLIENT_ONLY);
        } else {
            if (privateKey == null && sslContextParameters == null && advancedPskStore == null) {
                throw new IllegalStateException(
                        "Either a privateKey, sslContextParameters or advancedPskStore object "
                                                + "must be configured for a TLS service");
            }
            if (privateKey != null && publicKey == null) {
                throw new IllegalStateException("A public key must be configured to use a Raw Public Key with TLS");
            }
            if ((isClientAuthenticationRequired() || isClientAuthenticationWanted())
                    && (sslContextParameters == null || sslContextParameters.getTrustManagers() == null)
                    && publicKey == null) {
                throw new IllegalStateException("A truststore must be configured to support TLS client authentication");
            }

            builder.setAddress(address);
            if (isClientAuthenticationRequired()) {
                builder.set(DTLS_CLIENT_AUTHENTICATION_MODE, CertificateAuthenticationMode.NEEDED);
            } else if (isClientAuthenticationWanted()) {
                builder.set(DTLS_CLIENT_AUTHENTICATION_MODE, CertificateAuthenticationMode.WANTED);
            } else {
                // Is it right to set to NONE here?
                builder.set(DTLS_CLIENT_AUTHENTICATION_MODE, CertificateAuthenticationMode.NONE);
            }
            builder.set(DTLS_RECOMMENDED_CIPHER_SUITES_ONLY, isRecommendedCipherSuitesOnly());
        }

        try {
            // Configure the identity if the sslContextParameters or privateKey
            // parameter is specified
            if (sslContextParameters != null && sslContextParameters.getKeyManagers() != null) {
                KeyManagersParameters keyManagers = sslContextParameters.getKeyManagers();
                KeyStore keyStore = keyManagers.getKeyStore().createKeyStore();

                // Use the configured alias or fall back to the first alias in
                // the keystore that contains a key
                String alias = getAlias();
                if (alias == null) {
                    Enumeration<String> aliases = keyStore.aliases();
                    while (aliases.hasMoreElements()) {
                        String ksAlias = aliases.nextElement();
                        if (keyStore.isKeyEntry(ksAlias)) {
                            alias = ksAlias;
                            break;
                        }
                    }
                }
                if (alias == null) {
                    throw new IllegalStateException("The sslContextParameters keystore must contain a key entry");
                }

                PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyManagers.getKeyPassword().toCharArray());
                builder.setCertificateIdentityProvider(
                        new SingleCertificateProvider(privateKey, keyStore.getCertificateChain(alias)));

            } else if (privateKey != null) {
                builder.setCertificateIdentityProvider(new SingleCertificateProvider(privateKey, publicKey));
            }

            if (advancedPskStore != null) {
                builder.setAdvancedPskStore(advancedPskStore);
            }

            // Add all certificates from the truststore
            X509Certificate[] certs = getTrustedCerts();
            if (certs.length > 0) {
                NewAdvancedCertificateVerifier trust = StaticNewAdvancedCertificateVerifier
                        .builder()
                        .setTrustedCertificates(certs)
                        .build();
                builder.setAdvancedCertificateVerifier(trust);
            }
            if (advancedCertificateVerifier != null) {
                builder.set(DTLS_CERTIFICATE_TYPES, Arrays.asList(CertificateType.RAW_PUBLIC_KEY));
                builder.setAdvancedCertificateVerifier(advancedCertificateVerifier);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error in configuring TLS", e);
        }

        if (getConfiguredCipherSuites() != null) {
            LOGGER.debug("There are configured cipher suites: " + getConfiguredCipherSuites());
            builder.set(DTLS_CIPHER_SUITES, CipherSuite.getTypesByNames(getConfiguredCipherSuites()));
        }

        return new DTLSConnector(builder.build());
    }

    public CoapClient createCoapClient(URI uri) throws IOException, GeneralSecurityException {
        CoapClient client = new CoapClient(uri);

        // Configure TLS and / or TCP
        if (CoAPEndpoint.enableDTLS(uri)) {
            DTLSConnector connector = createDTLSConnector(null, true);
            CoapEndpoint.Builder coapBuilder = new CoapEndpoint.Builder();
            coapBuilder.setConnector(connector);

            client.setEndpoint(coapBuilder.build());
        } else if (CoAPEndpoint.enableTCP(getUri())) {
            TcpClientConnector tcpConnector;

            // TLS + TCP
            if (getUri().getScheme().startsWith("coaps")) {
                SSLContextParameters params = getSslContextParameters();
                if (params == null) {
                    params = new SSLContextParameters();
                }
                SSLContext sslContext = params.createSSLContext(getCamelContext());
                tcpConnector = new TlsClientConnector(sslContext, Configuration.createStandardWithoutFile());
            } else {
                tcpConnector = new TcpClientConnector(Configuration.createStandardWithoutFile());
            }

            CoapEndpoint.Builder tcpBuilder = new CoapEndpoint.Builder();
            tcpBuilder.setConnector(tcpConnector);

            client.setEndpoint(tcpBuilder.build());
        }
        return client;
    }
}
