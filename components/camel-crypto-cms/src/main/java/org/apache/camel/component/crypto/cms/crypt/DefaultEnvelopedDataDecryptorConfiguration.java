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
package org.apache.camel.component.crypto.cms.crypt;

import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.crypto.cms.common.DefaultCryptoCmsUnMarshallerConfiguration;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.jsse.KeyStoreParameters;

/**
 * The defualt implementation fetches the private key and certificate from a keystore.
 */
@UriParams
public class DefaultEnvelopedDataDecryptorConfiguration extends DefaultCryptoCmsUnMarshallerConfiguration implements EnvelopedDataDecryptorConfiguration, Cloneable {

    @UriParam(label = "decrypt")
    private char[] password;

    public DefaultEnvelopedDataDecryptorConfiguration() {
    }

    /**
     * Sets the password of the private keys. It is assumed that all private
     * keys in the keystore have the same password. If not set then it is
     * assumed that the password of the private keys is given by the keystore
     * password given in the {@link KeyStoreParameters}.
     */
    public void setPassword(char[] password) {
        this.password = password;
    }

    public char[] getPassword() {
        if (password == null) {
            if (getKeyStoreParameters() != null) {
                String passwordS = getKeyStoreParameters().getPassword();
                if (passwordS == null) {
                    throw new RuntimeException("Password for private keys not configured");
                } else {
                    return passwordS.toCharArray();
                }
            } else {
                throw new RuntimeException("Password for private keys not configured");
            }
        } else {
            return password;
        }
    }

    @Override
    public Collection<PrivateKeyWithCertificate> getPrivateKeyCertificateCollection(Exchange exchange) throws CryptoCmsException {
        KeyStore keystore = getKeyStore();
        try {
            List<PrivateKeyWithCertificate> privateKeys = new ArrayList<>(keystore.size());
            for (Enumeration<String> aliases = keystore.aliases(); aliases.hasMoreElements();) {
                String alias = aliases.nextElement();
                if (!keystore.isKeyEntry(alias)) {
                    // only key entries are relevant!
                    continue;
                }
                Key privateKey = keystore.getKey(alias, getPassword());
                if (privateKey instanceof PrivateKey) { // we currently only support assymmetric keys
                    Certificate cert = keystore.getCertificate(alias);
                    if (cert instanceof X509Certificate) {
                        privateKeys.add(new PrivateKeyWithCertificate((PrivateKey)privateKey, (X509Certificate)cert));
                    }
                }
            }
            if (privateKeys.isEmpty()) {
                throw new CryptoCmsException("No private keys in keystore found. Check your configuration.");
            }
            return privateKeys;
        } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
            throw new CryptoCmsException("Problem during reading the private keys from the keystore", e);
        }
    }

    @Override
    public DefaultEnvelopedDataDecryptorConfiguration copy() {
        try {
            return (DefaultEnvelopedDataDecryptorConfiguration)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e); // should never happen
        }
    }

}
