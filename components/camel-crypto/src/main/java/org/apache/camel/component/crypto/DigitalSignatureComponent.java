/**
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
package org.apache.camel.component.crypto;

import java.net.MalformedURLException;
import java.net.URI;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.KeyStoreParameters;

public class DigitalSignatureComponent extends UriEndpointComponent {

    private DigitalSignatureConfiguration configuration;

    public DigitalSignatureComponent() {
        super(DigitalSignatureEndpoint.class);
    }

    public DigitalSignatureComponent(CamelContext context) {
        super(context, DigitalSignatureEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "CamelContext");

        DigitalSignatureConfiguration config = getConfiguration().copy();

        setProperties(config, parameters);
        config.setCamelContext(getCamelContext());
        try {
            config.setCryptoOperation(new URI(remaining).getScheme());
        } catch (Exception e) {
            throw new MalformedURLException(String.format("An invalid crypto uri was provided '%s'."
                    + " Check the uri matches the format crypto:sign or crypto:verify", uri));
        }
        return new DigitalSignatureEndpoint(uri, this, config);
    }

    public DigitalSignatureConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new DigitalSignatureConfiguration();
        }
        return configuration;
    }

    /**
     * To use the shared DigitalSignatureConfiguration as configuration. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(DigitalSignatureConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getName() {
        return getConfiguration().getName();
    }

    /**
     * The logical name of this operation.
     * @param name
     */
    public void setName(String name) {
        getConfiguration().setName(name);
    }

    /**
     * Gets the JCE name of the Algorithm that should be used for the signer.
     */
    public String getAlgorithm() {
        return getConfiguration().getAlgorithm();
    }

    /**
     * Sets the JCE name of the Algorithm that should be used for the signer.
     * @param algorithm
     */
    public void setAlgorithm(String algorithm) {
        getConfiguration().setAlgorithm(algorithm);
    }

    /**
     * Gets the alias used to query the KeyStore for keys and {@link Certificate Certificates}
     * to be used in signing and verifying exchanges. This value can be provided at runtime via the message header
     * {@link DigitalSignatureConstants#KEYSTORE_ALIAS}
     */
    public String getAlias() {
        return getConfiguration().getAlias();
    }

    /**
     * Sets the alias used to query the KeyStore for keys and {@link Certificate Certificates}
     * to be used in signing and verifying exchanges. This value can be provided at runtime via the message header
     * {@link DigitalSignatureConstants#KEYSTORE_ALIAS}
     * @param alias
     */
    public void setAlias(String alias) {
        getConfiguration().setAlias(alias);
    }

    /**
     * Get the PrivateKey that should be used to sign the exchange
     */
    public PrivateKey getPrivateKey() throws Exception {
        return getConfiguration().getPrivateKey();
    }

    /**
     * Get the PrivateKey that should be used to sign the signature in the
     * exchange using the supplied alias.
     *
     * @param alias the alias used to retrieve the Certificate from the keystore.
     */
    public PrivateKey getPrivateKey(String alias) throws Exception {
        return getConfiguration().getPrivateKey(alias);
    }

    /**
     * Get the PrivateKey that should be used to sign the signature in the
     * exchange using the supplied alias.
     *
     * @param alias the alias used to retrieve the Certificate from the keystore.
     * @param password
     */
    public PrivateKey getPrivateKey(String alias, char[] password) throws Exception {
        return getConfiguration().getPrivateKey(alias, password);
    }

    /**
     * Set the PrivateKey that should be used to sign the exchange
     *
     * @param privateKey the key with with to sign the exchange.
     */
    public void setPrivateKey(PrivateKey privateKey) {
        getConfiguration().setPrivateKey(privateKey);
    }

    /**
     * Sets the reference name for a PrivateKey that can be fond in the registry.
     * @param privateKeyName
     */
    public void setPrivateKeyName(String privateKeyName) {
        getConfiguration().setPrivateKeyName(privateKeyName);
    }

    /**
     * Set the PublicKey that should be used to verify the signature in the exchange.
     * @param publicKey
     */
    public void setPublicKey(PublicKey publicKey) {
        getConfiguration().setPublicKey(publicKey);
    }

    /**
     * Sets the reference name for a publicKey that can be fond in the registry.
     * @param publicKeyName
     */
    public void setPublicKeyName(String publicKeyName) {
        getConfiguration().setPublicKeyName(publicKeyName);
    }

    /**
     * get the PublicKey that should be used to verify the signature in the exchange.
     */
    public PublicKey getPublicKey() {
        return getConfiguration().getPublicKey();
    }

    /**
     * Set the Certificate that should be used to verify the signature in the
     * exchange. If a {@link KeyStore} has been configured then this will
     * attempt to retrieve the {@link Certificate}from it using hte supplied
     * alias. If either the alias or the Keystore is invalid then the configured
     * certificate will be returned
     *
     * @param alias the alias used to retrieve the Certificate from the keystore.
     */
    public Certificate getCertificate(String alias) throws Exception {
        return getConfiguration().getCertificate(alias);
    }

    /**
     * Get the explicitly configured {@link Certificate} that should be used to
     * verify the signature in the exchange.
     */
    public Certificate getCertificate() throws Exception {
        return getConfiguration().getCertificate();
    }

    /**
     * Set the Certificate that should be used to verify the signature in the
     * exchange based on its payload.
     * @param certificate
     */
    public void setCertificate(Certificate certificate) {
        getConfiguration().setCertificate(certificate);
    }

    /**
     * Sets the reference name for a PrivateKey that can be fond in the registry.
     * @param certificateName
     */
    public void setCertificateName(String certificateName) {
        getConfiguration().setCertificateName(certificateName);
    }

    /**
     * Gets the KeyStore that can contain keys and Certficates for use in
     * signing and verifying exchanges. A {@link KeyStore} is typically used
     * with an alias, either one supplied in the Route definition or dynamically
     * via the message header "CamelSignatureKeyStoreAlias". If no alias is
     * supplied and there is only a single entry in the Keystore, then this
     * single entry will be used.
     */
    public KeyStore getKeystore() {
        return getConfiguration().getKeystore();
    }

    /**
     * Sets the KeyStore that can contain keys and Certficates for use in
     * signing and verifying exchanges. A {@link KeyStore} is typically used
     * with an alias, either one supplied in the Route definition or dynamically
     * via the message header "CamelSignatureKeyStoreAlias". If no alias is
     * supplied and there is only a single entry in the Keystore, then this
     * single entry will be used.
     * @param keystore
     */
    public void setKeystore(KeyStore keystore) {
        getConfiguration().setKeystore(keystore);
    }

    /**
     * Sets the reference name for a Keystore that can be fond in the registry.
     * @param keystoreName
     */
    public void setKeystoreName(String keystoreName) {
        getConfiguration().setKeystoreName(keystoreName);
    }

    /**
     * Gets the password used to access an aliased {@link PrivateKey} in the KeyStore.
     */
    public char[] getPassword() {
        return getConfiguration().getPassword();
    }

    /**
     * Sets the password used to access an aliased {@link PrivateKey} in the KeyStore.
     * @param password
     */
    public void setPassword(char[] password) {
        getConfiguration().setPassword(password);
    }

    public KeyStoreParameters getKeyStoreParameters() {
        return getConfiguration().getKeyStoreParameters();
    }

    /**
     * Sets the KeyStore that can contain keys and Certficates for use in
     * signing and verifying exchanges based on the given KeyStoreParameters.
     * A {@link KeyStore} is typically used
     * with an alias, either one supplied in the Route definition or dynamically
     * via the message header "CamelSignatureKeyStoreAlias". If no alias is
     * supplied and there is only a single entry in the Keystore, then this
     * single entry will be used.
     * @param keyStoreParameters
     */
    public void setKeyStoreParameters(KeyStoreParameters keyStoreParameters) throws Exception {
        getConfiguration().setKeyStoreParameters(keyStoreParameters);
    }

    /**
     * Get the SecureRandom used to initialize the Signature service
     */
    public SecureRandom getSecureRandom() {
        return getConfiguration().getSecureRandom();
    }

    /**
     * Sets the reference name for a SecureRandom that can be fond in the registry.
     * @param randomName
     */
    public void setSecureRandomName(String randomName) {
        getConfiguration().setSecureRandomName(randomName);
    }

    /**
     * Set the SecureRandom used to initialize the Signature service
     *
     * @param secureRandom the random used to init the Signature service
     */
    public void setSecureRandom(SecureRandom secureRandom) {
        getConfiguration().setSecureRandom(secureRandom);
    }

    /**
     * Get the size of the buffer used to read in the Exchange payload data.
     */
    public Integer getBufferSize() {
        return getConfiguration().getBufferSize();
    }

    /**
     * Set the size of the buffer used to read in the Exchange payload data.
     * @param bufferSize
     */
    public void setBufferSize(Integer bufferSize) {
        getConfiguration().setBufferSize(bufferSize);
    }

    /**
     * Get the id of the security provider that provides the configured
     * {@link Signature} algorithm.
     */
    public String getProvider() {
        return getConfiguration().getProvider();
    }

    /**
     * Set the id of the security provider that provides the configured
     * {@link Signature} algorithm.
     *
     * @param provider the id of the security provider
     */
    public void setProvider(String provider) {
        getConfiguration().setProvider(provider);
    }

    /**
     * Get the name of the message header that should be used to store the
     * base64 encoded signature. This defaults to 'CamelDigitalSignature'
     */
    public String getSignatureHeaderName() {
        return getConfiguration().getSignatureHeaderName();
    }

    /**
     * Set the name of the message header that should be used to store the
     * base64 encoded signature. This defaults to 'CamelDigitalSignature'
     * @param signatureHeaderName
     */
    public void setSignatureHeaderName(String signatureHeaderName) {
        getConfiguration().setSignatureHeaderName(signatureHeaderName);
    }

    /**
     * Determines if the Signature specific headers be cleared after signing and
     * verification. Defaults to true, and should only be made otherwise at your
     * extreme peril as vital private information such as Keys and passwords may
     * escape if unset.
     *
     * @return true if the Signature headers should be unset, false otherwise
     */
    public boolean isClearHeaders() {
        return getConfiguration().isClearHeaders();
    }

    /**
     * Determines if the Signature specific headers be cleared after signing and
     * verification. Defaults to true, and should only be made otherwise at your
     * extreme peril as vital private information such as Keys and passwords may
     * escape if unset.
     * @param clearHeaders
     */
    public void setClearHeaders(boolean clearHeaders) {
        getConfiguration().setClearHeaders(clearHeaders);
    }

    /**
     * Set the Crypto operation from that supplied after the crypto scheme in the
     * endpoint uri e.g. crypto:sign sets sign as the operation.
     *
     * @param operation the operation supplied after the crypto scheme
     */
    public void setCryptoOperation(String operation) {
        getConfiguration().setCryptoOperation(operation);
    }

    public void setCryptoOperation(CryptoOperation operation) {
        getConfiguration().setCryptoOperation(operation);
    }

    /**
     * Gets the Crypto operation that was supplied in the the crypto scheme in the endpoint uri
     */
    public CryptoOperation getCryptoOperation() {
        return getConfiguration().getCryptoOperation();
    }
}
