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

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import javax.xml.crypto.KeySelector;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;

import org.w3c.dom.Node;

import org.apache.camel.Message;

/**
 * Accesses the public key from a key-store and returns a KeyInfo which
 * contains the X.509 certificate chain corresponding to the public key.
 */
public class DefaultKeyAccessor extends DefaultKeySelector implements KeyAccessor {

    private String provider;

    public DefaultKeyAccessor() {

    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @Override
    public KeySelector getKeySelector(Message message) throws Exception {
        return this;
    }

    @Override
    public KeyInfo getKeyInfo(Message message, Node messageBody, KeyInfoFactory factory) throws Exception {
        return createKeyInfo(factory);
    }

    private KeyInfo createKeyInfo(KeyInfoFactory kif) throws Exception {

        X509Certificate[] chain = getCertificateChain();
        if (chain == null) {
            return null;
        }
        X509Data x509D = kif.newX509Data(Arrays.asList(chain));
        return kif.newKeyInfo(Collections.singletonList(x509D), "_" + UUID.randomUUID().toString());
    }

    private X509Certificate[] getCertificateChain() throws Exception {
        KeyStore keystore = getKeyStore();
        if (keystore == null) {
            return null;
        }
        String alias = getAlias();
        if (alias == null) {
            return null;
        }
        Certificate[] certs = keystore.getCertificateChain(alias);
        if (certs == null) {
            return null;
        }
        ArrayList<X509Certificate> certList = new ArrayList<>(certs.length);
        for (Certificate cert : certs) {
            if (cert instanceof X509Certificate) {
                certList.add((X509Certificate) cert);
            }
        }
        return certList.toArray(new X509Certificate[certList.size()]);
    }

}
