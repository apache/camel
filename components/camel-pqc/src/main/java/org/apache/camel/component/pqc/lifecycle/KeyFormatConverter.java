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
package org.apache.camel.component.pqc.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.apache.camel.component.pqc.PQCKeyEncapsulationAlgorithms;
import org.apache.camel.component.pqc.PQCSignatureAlgorithms;

/**
 * Utility class for converting PQC keys between different formats (PEM, DER, PKCS8, X509).
 */
public class KeyFormatConverter {

    /**
     * Export a public key to the specified format
     */
    public static byte[] exportPublicKey(PublicKey publicKey, KeyLifecycleManager.KeyFormat format) throws Exception {
        switch (format) {
            case PEM:
                return exportPublicKeyToPEM(publicKey);
            case DER:
            case X509:
                return publicKey.getEncoded(); // X.509 format (DER encoded)
            default:
                throw new IllegalArgumentException("Unsupported format for public key: " + format);
        }
    }

    /**
     * Export a private key to the specified format
     */
    public static byte[] exportPrivateKey(PrivateKey privateKey, KeyLifecycleManager.KeyFormat format) throws Exception {
        switch (format) {
            case PEM:
                return exportPrivateKeyToPEM(privateKey);
            case DER:
            case PKCS8:
                return privateKey.getEncoded(); // PKCS#8 format (DER encoded)
            default:
                throw new IllegalArgumentException("Unsupported format for private key: " + format);
        }
    }

    /**
     * Export a key pair to the specified format
     */
    public static byte[] exportKeyPair(KeyPair keyPair, KeyLifecycleManager.KeyFormat format, boolean includePrivate)
            throws Exception {
        if (includePrivate) {
            // Export both keys in a combined format
            byte[] publicKeyBytes = exportPublicKey(keyPair.getPublic(), format);
            byte[] privateKeyBytes = exportPrivateKey(keyPair.getPrivate(), format);

            // Concatenate with separator for PEM
            if (format == KeyLifecycleManager.KeyFormat.PEM) {
                return (new String(publicKeyBytes, StandardCharsets.UTF_8) + "\n"
                        + new String(privateKeyBytes, StandardCharsets.UTF_8))
                        .getBytes(StandardCharsets.UTF_8);
            } else {
                // For DER, just return private key (contains public key info)
                return privateKeyBytes;
            }
        } else {
            return exportPublicKey(keyPair.getPublic(), format);
        }
    }

    /**
     * Import a public key from bytes
     */
    public static PublicKey importPublicKey(byte[] keyData, KeyLifecycleManager.KeyFormat format, String algorithm)
            throws Exception {
        byte[] derBytes;

        if (format == KeyLifecycleManager.KeyFormat.PEM) {
            derBytes = parsePEMPublicKey(keyData);
        } else {
            derBytes = keyData;
        }

        X509EncodedKeySpec spec = new X509EncodedKeySpec(derBytes);
        return keyFactory(algorithm).generatePublic(spec);
    }

    /**
     * Import a private key from bytes
     */
    public static PrivateKey importPrivateKey(byte[] keyData, KeyLifecycleManager.KeyFormat format, String algorithm)
            throws Exception {
        byte[] derBytes;

        if (format == KeyLifecycleManager.KeyFormat.PEM) {
            derBytes = parsePEMPrivateKey(keyData);
        } else {
            derBytes = keyData;
        }

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(derBytes);
        return keyFactory(algorithm).generatePrivate(spec);
    }

    /**
     * Resolves the {@link KeyFactory} of an algorithm with the BouncyCastle provider that serves it, so an imported key
     * is of the same provider as the keys this component generates and signs with. From JDK 24 the JDK itself
     * implements ML-DSA and ML-KEM and is listed before BouncyCastle, so a plain {@code KeyFactory.getInstance} returns
     * a JDK key, which the component's BouncyCastle {@code Signature} rejects as an unknown private key. An algorithm
     * the component does not map, or whose provider is not installed, keeps the default lookup.
     */
    private static KeyFactory keyFactory(String algorithm) throws Exception {
        String provider = bouncyCastleProviderFor(algorithm);
        if (provider != null && Security.getProvider(provider) != null) {
            return KeyFactory.getInstance(algorithm, provider);
        }
        return KeyFactory.getInstance(algorithm);
    }

    /**
     * The BouncyCastle provider ({@code BC} or {@code BCPQC}) serving an algorithm given by the component's name
     * ({@code MLDSA}) or by its JCE name ({@code ML-DSA}), or {@code null} for an algorithm the component does not
     * know.
     */
    private static String bouncyCastleProviderFor(String algorithm) {
        String provider = KeyAlgorithmSupport.determineProvider(algorithm);
        if (provider != null) {
            return provider;
        }
        for (PQCSignatureAlgorithms candidate : PQCSignatureAlgorithms.values()) {
            if (candidate.getAlgorithm().equalsIgnoreCase(algorithm)) {
                return candidate.getBcProvider();
            }
        }
        for (PQCKeyEncapsulationAlgorithms candidate : PQCKeyEncapsulationAlgorithms.values()) {
            if (candidate.getAlgorithm().equalsIgnoreCase(algorithm)) {
                return candidate.getBcProvider();
            }
        }
        return null;
    }

    /**
     * Export public key to PEM format
     */
    private static byte[] exportPublicKeyToPEM(PublicKey publicKey) throws Exception {
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);

        // Format as PEM with line breaks every 64 characters
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        pem.append("-----END PUBLIC KEY-----\n");
        return pem.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Export private key to PEM format
     */
    private static byte[] exportPrivateKeyToPEM(PrivateKey privateKey) throws Exception {
        byte[] encoded = privateKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);

        // Format as PEM with line breaks every 64 characters
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        pem.append("-----END PRIVATE KEY-----\n");
        return pem.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parse PEM-encoded public key
     */
    private static byte[] parsePEMPublicKey(byte[] pemData) {
        return parsePEMBlock(pemData, "PUBLIC KEY");
    }

    /**
     * Parse PEM-encoded private key
     */
    private static byte[] parsePEMPrivateKey(byte[] pemData) {
        return parsePEMBlock(pemData, "PRIVATE KEY");
    }

    /**
     * Extracts the body of one labelled PEM block.
     * <p/>
     * The block is located by its BEGIN and END lines rather than by stripping those lines out of the whole input, so
     * that a document holding more than one block - as {@link #exportKeyPair} produces for a key pair including the
     * private key - yields the requested block instead of a Base64 string still carrying the armour of the other one.
     */
    private static byte[] parsePEMBlock(byte[] pemData, String label) {
        String pem = new String(pemData, StandardCharsets.UTF_8);
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";

        int from = pem.indexOf(begin);
        if (from < 0) {
            throw new IllegalArgumentException("PEM data holds no '" + begin + "' line");
        }
        from += begin.length();

        int to = pem.indexOf(end, from);
        if (to < 0) {
            throw new IllegalArgumentException("PEM data holds no '" + end + "' line");
        }

        return Base64.getDecoder().decode(pem.substring(from, to).replaceAll("\\s", ""));
    }
}
