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
package org.apache.camel.component.as2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * That's a utility class for preparing Mendelson-specific certificate chain, private key, ssl context. It has no
 * mention of paths to Mendelson certificate, keystore and keystore password, but we can't ensure that it works with any
 * provided certificate and keystore without modifications. At least due to certificate chain for Mendelson consists of
 * the only certificate.
 */
public class MendelsonCertLoader {

    private static final Logger LOG = LoggerFactory.getLogger(MendelsonCertLoader.class);

    private final List<Certificate> chainAsList = new ArrayList<>();

    private PrivateKey privateKey;
    private SSLContext sslContext;

    public void setupSslContext(String keyStorePath, String keyStorePassword) {
        try {
            InputStream keyStoreAsStream = getClass().getClassLoader().getResourceAsStream(keyStorePath);
            KeyStore keyStore = getKeyStore(keyStoreAsStream, keyStorePassword);
            sslContext = SSLContexts.custom().setKeyStoreType("PKCS12")
                    .loadTrustMaterial(keyStore, new TrustAllStrategy())
                    .build();
        } catch (KeyStoreException | IOException | KeyManagementException | NoSuchAlgorithmException e) {
            LOG.error("Failed to configure SSLContext", e);
        }

        if (sslContext == null) {
            LOG.error("failed to configure SSL context");
        }
    }

    private KeyStore getKeyStore(InputStream inputStream, String keyStorePassword)
            throws IOException, NoSuchAlgorithmException {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(inputStream, keyStorePassword.toCharArray());
            return ks;
        } catch (KeyStoreException e) {
            LOG.error("Failed to create instance of KeyStore", e);
        } catch (CertificateException e) {
            LOG.error("Failed to load KeyStore");
        }
        throw new IllegalStateException("about to return null");
    }

    public void setupCertificateChain(String certificatePath, String keyStorePath, String keyStorePassword) {

        InputStream certificateAsStream = getClass().getClassLoader().getResourceAsStream(certificatePath);
        if (certificateAsStream == null) {
            //LOG.error("Couldn't read out client certificate as stream.");
            throw new IllegalStateException("Couldn't read out certificate as stream.");
        }

        InputStream keyStoreAsStream = getClass().getClassLoader().getResourceAsStream(keyStorePath);
        if (keyStoreAsStream == null) {
            //LOG.error("Couldn't read out private key as stream.");
            throw new IllegalStateException("Couldn't read out key storage as stream.");
        }

        try {
            Certificate certificate = getCertificateFromStream(certificateAsStream);
            chainAsList.add(certificate);

            //private key
            privateKey = getPrivateKeyFromPKCSStream(keyStoreAsStream, keyStorePassword);

        } catch (IOException e) {
            String errMsg
                    = "Error while trying to load certificate to the key store. IO error when reading a byte array.  " + e;
            LOG.error(errMsg);
        } catch (NoSuchAlgorithmException e) {
            String errMsg = "Error while trying to load certificate to the key store. Requested algorithm isn't found.  " + e;
            LOG.error(errMsg);
        } catch (CertificateException e) {
            String errMsg = "Error while trying to load certificate to the key store. There is a certificate problem.  " + e;
            LOG.error(errMsg);
        } catch (InvalidKeySpecException e) {
            String errMsg = "Can not init private key store  " + e;
            LOG.error(errMsg);
        }
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public Certificate[] getChain() {
        if (chainAsList.size() > 0) {
            Certificate[] arrayCert = new Certificate[chainAsList.size()];

            for (int i = 0; i < chainAsList.size(); i++) {
                arrayCert[i] = chainAsList.get(i);
            }
            return arrayCert;
        } else {
            return null;
        }
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    private List<Certificate> getCertificatesFromStream(InputStream inputStream) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (List<Certificate>) certificateFactory.generateCertificates(inputStream);
    }

    private Certificate getCertificateFromStream(InputStream inputStream) throws IOException, CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return certificateFactory.generateCertificate(inputStream);
    }

    //https://stackoverflow.com/questions/18644286/creating-privatekey-object-from-pkcs12
    private PrivateKey getPrivateKeyFromPKCSStream(InputStream inputStream, String keyStorePassword)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance("PKCS12");
        } catch (KeyStoreException e) {
            LOG.error("Error while getting instance of KeyStore: {}", e.getMessage(), e);
        }
        try {
            ks.load(inputStream, keyStorePassword.toCharArray());
        } catch (CertificateException e) {
            LOG.error("Error while loading the certificate: {}", e.getMessage(), e);
        }
        try {
            return (PrivateKey) ks.getKey(
                    ks.aliases().nextElement(),
                    keyStorePassword.toCharArray());
        } catch (KeyStoreException e) {
            LOG.error("Error while retrieving private key: {}", e.getMessage(), e);
        } catch (UnrecoverableKeyException e) {
            LOG.error("Error while retrieving private key: {}", e.getMessage(), e);
        }
        throw new IllegalStateException("Failed to construct a PrivateKey from provided InputStream");
    }

    private byte[] getBytesFromPem(InputStream inputStream) throws IOException {
        String privateKeyPEM
                = IOUtils.toString(inputStream, StandardCharsets.UTF_8).replaceAll("-{5}.+-{5}", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(privateKeyPEM);
    }

    private byte[] getBytesFromPKCS12(InputStream inputStream) throws IOException {
        String privateKeyPKCS12 = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        return privateKeyPKCS12.getBytes(StandardCharsets.UTF_8);
    }

}
