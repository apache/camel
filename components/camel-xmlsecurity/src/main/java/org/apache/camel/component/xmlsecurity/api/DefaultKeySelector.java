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
package org.apache.camel.component.xmlsecurity.api;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.jsse.KeyStoreParameters;

/**
 * Default implementation for the key selector. The key is read from a key-store
 * for a given alias. Depending on the purpose a private or public key is
 * returned.
 */
public class DefaultKeySelector extends KeySelector implements CamelContextAware {

    private final KeyStoreAndAlias keyStoreAndAlias = new KeyStoreAndAlias();

    private KeySelectorResult nullKeyResult;
    
    private CamelContext context;

    public void setKeyStore(KeyStore keyStore) {
        keyStoreAndAlias.setKeyStore(keyStore);
    }

    public void setAlias(String alias) {
        keyStoreAndAlias.setAlias(alias);
    }

    public void setPassword(String password) {
        if (password == null) {
            keyStoreAndAlias.setPassword(null);
        } else {
            setPassword(password.toCharArray());
        }
    }

    public void setPassword(char[] password) {
        keyStoreAndAlias.setPassword(password);
    }
    
    public void setKeyStoreParameters(KeyStoreParameters parameters) 
        throws GeneralSecurityException, IOException {
        if (parameters != null) {
            keyStoreAndAlias.setKeyStore(parameters.createKeyStore());
        }
    }

    @Override
    public KeySelectorResult select(KeyInfo keyInfo, KeySelector.Purpose purpose, AlgorithmMethod method, XMLCryptoContext context)
        throws KeySelectorException {
        
        if (keyStoreAndAlias.getKeyStore() == null) {
            return getNullKeyResult();
        }
        if (keyStoreAndAlias.getAlias() == null) {
            return getNullKeyResult();
        }
        if (KeySelector.Purpose.VERIFY.equals(purpose)) {
            Certificate cert;
            try {
                cert = keyStoreAndAlias.getKeyStore().getCertificate(keyStoreAndAlias.getAlias());
            } catch (KeyStoreException e) {
                throw new KeySelectorException(e);
            }
            if (cert == null) {
                return getNullKeyResult();
            }
            final Key key = cert.getPublicKey();
            return getKeySelectorResult(key);
        } else if (KeySelector.Purpose.SIGN.equals(purpose)) {
            if (keyStoreAndAlias.getPassword() == null) {
                return getNullKeyResult();
            }
            Key key;
            try {
                if (this.getCamelContext() != null && keyStoreAndAlias.getPassword() != null) {
                    try {
                        String passwordProperty = 
                            this.getCamelContext().resolvePropertyPlaceholders(
                                new String(keyStoreAndAlias.getPassword()));
                        key = keyStoreAndAlias.getKeyStore().getKey(keyStoreAndAlias.getAlias(), passwordProperty.toCharArray());
                    } catch (Exception e) {
                        throw new RuntimeCamelException("Error parsing property value: " 
                            + new String(keyStoreAndAlias.getPassword()), e);
                    }
                } else {
                    key = keyStoreAndAlias.getKeyStore().getKey(keyStoreAndAlias.getAlias(), keyStoreAndAlias.getPassword());
                }
            } catch (UnrecoverableKeyException e) {
                throw new KeySelectorException(e);
            } catch (KeyStoreException e) {
                throw new KeySelectorException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new KeySelectorException(e);
            }
            return getKeySelectorResult(key);
        } else {
            throw new IllegalStateException("Purpose " + purpose + " not supported");
        }
    }

    KeyStore getKeyStore() {
        return keyStoreAndAlias.getKeyStore();
    }

    String getAlias() {
        return keyStoreAndAlias.getAlias();
    }

    private KeySelectorResult getKeySelectorResult(final Key key) {
        return new KeySelectorResult() {
            public Key getKey() {
                return key;
            }
        };
    }

    private KeySelectorResult getNullKeyResult() {
        if (nullKeyResult == null) {
            nullKeyResult = new KeySelectorResult() {
                public Key getKey() {
                    return null;
                }
            };
        }
        return nullKeyResult;
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.context = context;
    }

}
