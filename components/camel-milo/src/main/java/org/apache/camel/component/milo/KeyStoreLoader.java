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
package org.apache.camel.component.milo;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;

import org.apache.camel.RuntimeCamelException;

public class KeyStoreLoader {
    public static final String DEFAULT_KEY_STORE_TYPE = "PKCS12";

    private String type = DEFAULT_KEY_STORE_TYPE;
    private URL url;
    private String keyStorePassword;
    private String keyPassword;
    private String keyAlias;

    public static class Result {

        private final X509Certificate certificate;
        private final X509Certificate[] certificateChain;
        private final KeyPair keyPair;

        public Result(final X509Certificate certificate, final X509Certificate[] certificateChain, final KeyPair keyPair) {
            this.certificate = certificate;
            this.certificateChain = certificateChain;
            this.keyPair = keyPair;
        }

        public X509Certificate getCertificate() {
            return this.certificate;
        }

        public X509Certificate[] getCertificateChain() {
            return this.certificateChain;
        }

        public KeyPair getKeyPair() {
            return this.keyPair;
        }
    }

    public KeyStoreLoader() {
    }

    public void setType(final String type) {
        this.type = type != null ? type : DEFAULT_KEY_STORE_TYPE;
    }

    public String getType() {
        return this.type;
    }

    public void setUrl(final URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return this.url;
    }

    public void setUrl(final String url) throws MalformedURLException {
        this.url = new URL(url);
    }

    public void setKeyStorePassword(final String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStorePassword() {
        return this.keyStorePassword;
    }

    public void setKeyPassword(final String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getKeyPassword() {
        return this.keyPassword;
    }

    public void setKeyAlias(final String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getKeyAlias() {
        return this.keyAlias;
    }

    public Result load() throws GeneralSecurityException, IOException {

        final KeyStore keyStore = KeyStore.getInstance(this.type);

        try (InputStream stream = this.url.openStream()) {
            keyStore.load(stream, this.keyStorePassword != null ? this.keyStorePassword.toCharArray() : null);
        }

        String effectiveKeyAlias = this.keyAlias;

        if (effectiveKeyAlias == null) {
            if (keyStore.size() != 1) {
                throw new IllegalArgumentException(
                        "Key store contains more than one key. The use of the 'keyAlias' parameter is required.");
            }
            try {
                effectiveKeyAlias = keyStore.aliases().nextElement();
            } catch (final NoSuchElementException e) {
                throw new RuntimeCamelException("Failed to enumerate key alias", e);
            }
        }

        final Key privateKey
                = keyStore.getKey(effectiveKeyAlias, this.keyPassword != null ? this.keyPassword.toCharArray() : null);

        if (privateKey instanceof PrivateKey pk) {
            // Load the full certificate chain
            final java.security.cert.Certificate[] chain = keyStore.getCertificateChain(effectiveKeyAlias);
            if (chain == null || chain.length == 0) {
                return null;
            }

            // Convert to X509Certificate array
            final X509Certificate[] certificateChain = new X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                certificateChain[i] = (X509Certificate) chain[i];
            }

            // The first certificate in the chain is the end-entity certificate
            final X509Certificate certificate = certificateChain[0];
            final PublicKey publicKey = certificate.getPublicKey();
            final KeyPair keyPair = new KeyPair(publicKey, pk);
            return new Result(certificate, certificateChain, keyPair);
        }

        return null;
    }
}
