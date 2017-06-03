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

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.crypto.processor.SigningProcessor;
import org.apache.camel.component.crypto.processor.VerifyingProcessor;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The crypto component is used for signing and verifying exchanges using the Signature Service of the Java Cryptographic Extension (JCE).
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "crypto", title = "Crypto (JCE)", syntax = "crypto:cryptoOperation:name", producerOnly = true, label = "security,transformation")
public class DigitalSignatureEndpoint extends DefaultEndpoint {
    @UriParam
    private DigitalSignatureConfiguration configuration;

    public DigitalSignatureEndpoint(String uri, DigitalSignatureComponent component, DigitalSignatureConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        if (CryptoOperation.sign == configuration.getCryptoOperation()) {
            return new DigitalSignatureProducer(this, new SigningProcessor(configuration));
        } else {
            return new DigitalSignatureProducer(this, new VerifyingProcessor(configuration));
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Digital Signatures endpoints are not meant to be consumed from. They are meant be used as an intermediate endpoints");
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * Sets the configuration to use
     */
    public void setConfiguration(DigitalSignatureConfiguration configuration) {
        this.configuration = configuration;
    }

    public DigitalSignatureConfiguration getConfiguration() {
        return configuration;
    }

    public PublicKey getPublicKey() throws Exception {
        return getConfiguration().getPublicKey();
    }

    public void setPublicKey(PublicKey publicKey) {
        getConfiguration().setPublicKey(publicKey);
    }

    public void setPublicKey(String publicKeyName) {
        getConfiguration().setPublicKeyName(publicKeyName);
    }

    public Certificate getCertificate() throws Exception {
        return getConfiguration().getCertificate();
    }

    public PrivateKey getPrivateKey() throws Exception {
        return getConfiguration().getPrivateKey();
    }

    public void setPrivateKey(PrivateKey privateKey) {
        getConfiguration().setPrivateKey(privateKey);
    }

    public KeyStore getKeystore() {
        return getConfiguration().getKeystore();
    }

    public void setKeystore(KeyStore keystore) {
        getConfiguration().setKeystore(keystore);
    }

    public char[] getPassword() {
        return getConfiguration().getPassword();
    }

    public void setKeyPassword(char[] keyPassword) {
        getConfiguration().setPassword(keyPassword);
    }

    public SecureRandom getSecureRandom() {
        return getConfiguration().getSecureRandom();
    }

    public void setSecureRandom(SecureRandom secureRandom) {
        getConfiguration().setSecureRandom(secureRandom);
    }

    public String getAlgorithm() {
        return getConfiguration().getAlgorithm();
    }

    public void setAlgorithm(String algorithm) {
        getConfiguration().setAlgorithm(algorithm);
    }

    public Integer getBuffersize() {
        return getConfiguration().getBufferSize();
    }

    public void setBuffersize(Integer buffersize) {
        getConfiguration().setBufferSize(buffersize);
    }

    public String getProvider() {
        return getConfiguration().getProvider();
    }

    public void setProvider(String provider) {
        getConfiguration().setProvider(provider);
    }

    public String getSignatureHeader() {
        return getConfiguration().getSignatureHeaderName();
    }

    public void setSignatureHeader(String signatureHeaderName) {
        getConfiguration().setSignatureHeaderName(signatureHeaderName);
    }

    public String getAlias() {
        return getConfiguration().getAlias();
    }

    public void setAlias(String alias) {
        getConfiguration().setAlias(alias);
    }

    public boolean isClearHeaders() {
        return getConfiguration().isClearHeaders();
    }

    public void setClearHeaders(boolean clearHeaders) {
        getConfiguration().setClearHeaders(clearHeaders);
    }
}
