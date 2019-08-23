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
package org.apache.camel.component.crypto.cms;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.crypto.cms.crypt.DefaultEnvelopedDataDecryptorConfiguration;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataEncryptorConfiguration;
import org.apache.camel.component.crypto.cms.sig.DefaultSignedDataVerifierConfiguration;
import org.apache.camel.component.crypto.cms.sig.SignedDataCreatorConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The crypto cms component is used for encrypting data in CMS Enveloped Data
 * format, decrypting CMS Enveloped Data, signing data in CMS Signed Data
 * format, and verifying CMS Signed Data.
 */
@UriEndpoint(firstVersion = "2.20.0", scheme = "crypto-cms", title = "Crypto CMS", syntax = "crypto-cms:cryptoOperation:name", producerOnly = true, label = "security,transformation")
public class CryptoCmsEndpoint extends DefaultEndpoint {
    private final Processor processor;

    @UriPath
    @Metadata(required = true)
    private CryptoOperation cryptoOperation;
    @UriPath
    @Metadata(required = true)
    private String name;

    // to include different kind of configuration params
    @UriParam
    private SignedDataCreatorConfiguration signConfig;
    @UriParam
    private DefaultSignedDataVerifierConfiguration verifyConfig;
    @UriParam
    private EnvelopedDataEncryptorConfiguration encryptConfig;
    @UriParam
    private DefaultEnvelopedDataDecryptorConfiguration decryptConfig;

    public CryptoCmsEndpoint(String uri, CryptoCmsComponent component, Processor processor) {
        super(uri, component);
        this.processor = processor;
    }

    public String getName() {
        return name;
    }

    public SignedDataCreatorConfiguration getSignConfig() {
        return signConfig;
    }

    public DefaultSignedDataVerifierConfiguration getVerifyConfig() {
        return verifyConfig;
    }

    public EnvelopedDataEncryptorConfiguration getEncryptConfig() {
        return encryptConfig;
    }

    public DefaultEnvelopedDataDecryptorConfiguration getDecryptConfig() {
        return decryptConfig;
    }

    /**
     * Set the Crypto operation from that supplied after the crypto scheme in
     * the endpoint uri e.g. crypto-cms:sign sets sign as the operation.
     * Possible values: "sign", "verify", "encrypt", or "decrypt".
     */
    public void setCryptoOperation(String operation) {
        this.cryptoOperation = CryptoOperation.valueOf(operation);
    }

    public void setCryptoOperation(CryptoOperation operation) {
        this.cryptoOperation = operation;
    }

    /**
     * Gets the Crypto operation that was supplied in the crypto scheme in
     * the endpoint uri
     */
    public CryptoOperation getCryptoOperation() {
        return cryptoOperation;
    }

    /**
     * The name part in the URI can be chosen by the user to distinguish between
     * different signer/verifier/encryptor/decryptor endpoints within the camel
     * context.
     */
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Producer createProducer() {
        return new CryptoCmsProducer(this, processor);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Crypto CMS endpoints are not meant to be consumed from. They are meant be used as intermediate endpoints");
    }

    public Object getManagedObject(CryptoCmsEndpoint endpoint) {
        return this;
    }

}
