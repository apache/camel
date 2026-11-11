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
package org.apache.camel.component.hashicorp.vault;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.util.ObjectHelper;
import org.springframework.vault.support.PemObject;

/**
 * The TLS context of a HashiCorp Vault client: the certificates it trusts, and the client certificate it presents.
 * <p/>
 * A store is a PKCS12 or JKS key store, or PEM: certificates to trust, or a certificate and its private key (PKCS#8, or
 * an RSA key in PKCS#1).
 */
final class HashicorpVaultTls {

    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN ([A-Z0-9 ]+)-----(.+?)-----END \\1-----", Pattern.DOTALL);
    // the algorithms of a PKCS#8 private key that is tried in turn, as Java 17 cannot read the algorithm of the key
    private static final List<String> KEY_ALGORITHMS = List.of("RSA", "EC", "Ed25519", "Ed448");

    private HashicorpVaultTls() {
    }

    /**
     * The TLS context of the trust store and key store of the settings, or null when neither is set, so that the client
     * trusts the server as any other TLS connection of the JVM.
     *
     * @throws IllegalArgumentException when a store cannot be read
     */
    static SSLContext sslContext(HashicorpVaultClientSettings settings) {
        if (ObjectHelper.isEmpty(settings.getTrustStore()) && ObjectHelper.isEmpty(settings.getKeyStore())) {
            return null;
        }
        TrustManager[] trustManagers = null;
        if (ObjectHelper.isNotEmpty(settings.getTrustStore())) {
            trustManagers = trustManagers(settings.getTrustStore(), settings.getTrustStorePassword());
        }
        KeyManager[] keyManagers = null;
        if (ObjectHelper.isNotEmpty(settings.getKeyStore())) {
            keyManagers = keyManagers(settings.getKeyStore(), settings.getKeyStorePassword());
        }
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            // no trust managers: the trust managers of the JVM
            context.init(keyManagers, trustManagers, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Cannot create the TLS context of HashiCorp Vault: " + e.getMessage(), e);
        }
    }

    private static TrustManager[] trustManagers(String file, String password) {
        try {
            KeyStore store;
            String pem = pem(file);
            if (pem != null) {
                store = KeyStore.getInstance(KeyStore.getDefaultType());
                store.load(null, null);
                int index = 0;
                for (Certificate certificate : certificates(pem)) {
                    store.setCertificateEntry("certificate-" + index++, certificate);
                }
                if (index == 0) {
                    throw new GeneralSecurityException("no certificate in the PEM file");
                }
            } else {
                store = KeyStore.getInstance(new File(file), toChars(password));
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            return factory.getTrustManagers();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Cannot read the trust store " + file + ": " + e.getMessage(), e);
        }
    }

    private static KeyManager[] keyManagers(String file, String password) {
        try {
            KeyStore store;
            char[] keyPassword;
            String pem = pem(file);
            if (pem != null) {
                List<Certificate> chain = certificates(pem);
                if (chain.isEmpty()) {
                    throw new GeneralSecurityException("no certificate in the PEM file");
                }
                keyPassword = new char[0];
                store = KeyStore.getInstance(KeyStore.getDefaultType());
                store.load(null, null);
                store.setKeyEntry("client", privateKey(pem), keyPassword, chain.toArray(new Certificate[0]));
            } else {
                keyPassword = toChars(password);
                store = KeyStore.getInstance(new File(file), keyPassword);
            }
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(store, keyPassword);
            return factory.getKeyManagers();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Cannot read the key store " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * The content of the file when it is PEM, else null
     */
    private static String pem(String file) throws IOException {
        // ISO 8859-1 reads any byte, so a binary key store can be searched as well
        String content = Files.readString(Path.of(file), StandardCharsets.ISO_8859_1);
        return content.contains("-----BEGIN ") ? content : null;
    }

    private static List<Certificate> certificates(String pem) throws GeneralSecurityException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<Certificate> certificates = new ArrayList<>();
        Matcher block = PEM_BLOCK.matcher(pem);
        while (block.find()) {
            if ("CERTIFICATE".equals(block.group(1))) {
                certificates.add(factory.generateCertificate(new ByteArrayInputStream(decode(block.group(2)))));
            }
        }
        return certificates;
    }

    private static PrivateKey privateKey(String pem) throws GeneralSecurityException {
        Matcher block = PEM_BLOCK.matcher(pem);
        while (block.find()) {
            String type = block.group(1);
            if ("PRIVATE KEY".equals(type)) {
                return pkcs8PrivateKey(decode(block.group(2)));
            }
            if ("RSA PRIVATE KEY".equals(type)) {
                return KeyFactory.getInstance("RSA").generatePrivate(PemObject.fromKey(block.group()).getRSAPrivateKeySpec());
            }
            if ("ENCRYPTED PRIVATE KEY".equals(type)) {
                throw new GeneralSecurityException(
                        "an encrypted PEM private key is not supported: use a PKCS12 key store with its password");
            }
        }
        throw new GeneralSecurityException(
                "no private key (-----BEGIN PRIVATE KEY----- or -----BEGIN RSA PRIVATE KEY-----) in the PEM file");
    }

    private static PrivateKey pkcs8PrivateKey(byte[] der) throws GeneralSecurityException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        for (String algorithm : KEY_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec);
            } catch (InvalidKeySpecException e) {
                // a key of another algorithm
            }
        }
        throw new InvalidKeySpecException("the private key is not an RSA, EC or EdDSA key in PKCS#8");
    }

    private static byte[] decode(String base64) {
        return Base64.getMimeDecoder().decode(base64.strip());
    }

    private static char[] toChars(String password) {
        return password != null ? password.toCharArray() : null;
    }
}
