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
package org.apache.camel.component.crypto.cms.common;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.jsse.KeyStoreParameters;

@UriParams
public abstract class DefaultCryptoCmsConfiguration {

    @UriParam(label = "common")
    private KeyStoreParameters keyStoreParameters;

    @UriParam(label = "common")
    private KeyStore keyStore;

    /**
     * Keystore containing signer private keys, verifier public keys, encryptor
     * public keys, decryptor private keys depending on the operation. Use
     * either this parameter or the parameter 'keystore'.
     */
    public void setKeyStoreParameters(KeyStoreParameters keyStoreParameters) throws CryptoCmsException {
        this.keyStoreParameters = keyStoreParameters;
        if (keyStoreParameters != null) {
            try {
                this.keyStore = keyStoreParameters.createKeyStore();
            } catch (GeneralSecurityException | IOException e) {
                throw new CryptoCmsException("Problem during generating the keystore", e);
            }
        }
    }

    /**
     * Keystore which contains signer private keys, verifier public keys,
     * encryptor public keys, decryptor private keys depending on the operation.
     * Use either this parameter or the parameter 'keyStoreParameters'.
     */
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    protected KeyStore getKeyStore() throws CryptoCmsException {
        if (keyStore == null) {
            throw new CryptoCmsException("Keystore not configured");
        }
        return keyStore;
    }

    protected KeyStoreParameters getKeyStoreParameters() {
        return keyStoreParameters;
    }
}
