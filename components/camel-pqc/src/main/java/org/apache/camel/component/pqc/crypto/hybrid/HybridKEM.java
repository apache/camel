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
package org.apache.camel.component.pqc.crypto.hybrid;

import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.jcajce.SecretKeyWithEncapsulation;
import org.bouncycastle.jcajce.spec.KEMExtractSpec;
import org.bouncycastle.jcajce.spec.KEMGenerateSpec;

/**
 * Utility class for hybrid Key Encapsulation Mechanism (KEM) operations combining classical key agreement (e.g., ECDH,
 * X25519) with post-quantum KEM algorithms (e.g., ML-KEM).
 * <p>
 * Hybrid KEM provides defense-in-depth by combining:
 * <ul>
 * <li>Classical ECDH/X25519 key agreement for quantum-vulnerable but well-understood security</li>
 * <li>Post-quantum KEM for quantum-resistant security</li>
 * </ul>
 * The shared secrets from both algorithms are combined using HKDF to produce the final shared secret.
 * <p>
 * Wire format: [4 bytes: classical encap length][classical encap][pqc encap]
 */
public final class HybridKEM {

    private HybridKEM() {
        // Utility class
    }

    /**
     * Generates a hybrid encapsulation using both classical ECDH/XDH and PQC KEM.
     *
     * @param  classicalPublicKey the recipient's classical public key (EC or XDH)
     * @param  pqcPublicKey       the recipient's PQC public key
     * @param  classicalKA        the classical key agreement instance
     * @param  pqcKemGenerator    the PQC KEM key generator
     * @param  symmetricAlgorithm the symmetric algorithm for the shared secret (e.g., "AES")
     * @param  keyLength          the symmetric key length in bits
     * @param  kdfAlgorithm       the KDF algorithm (e.g., "HKDF-SHA256")
     * @return                    the hybrid encapsulation result containing the combined secret
     * @throws Exception          if encapsulation fails
     */
    public static HybridEncapsulationResult encapsulate(
            PublicKey classicalPublicKey,
            PublicKey pqcPublicKey,
            KeyAgreement classicalKA,
            KeyGenerator pqcKemGenerator,
            String symmetricAlgorithm,
            int keyLength,
            String kdfAlgorithm)
            throws Exception {

        if (classicalPublicKey == null || pqcPublicKey == null || classicalKA == null
                || pqcKemGenerator == null || symmetricAlgorithm == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }

        // Generate ephemeral key pair for classical key agreement
        KeyPair ephemeralKeyPair = generateEphemeralKeyPair(classicalPublicKey.getAlgorithm());

        // Perform classical key agreement
        classicalKA.init(ephemeralKeyPair.getPrivate());
        classicalKA.doPhase(classicalPublicKey, true);
        byte[] classicalSharedSecret = classicalKA.generateSecret();

        // The classical "encapsulation" is the ephemeral public key
        byte[] classicalEncapsulation = ephemeralKeyPair.getPublic().getEncoded();

        // Perform PQC KEM encapsulation
        pqcKemGenerator.init(
                new KEMGenerateSpec(pqcPublicKey, symmetricAlgorithm, keyLength),
                new SecureRandom());
        SecretKeyWithEncapsulation pqcResult = (SecretKeyWithEncapsulation) pqcKemGenerator.generateKey();
        byte[] pqcSharedSecret = pqcResult.getEncoded();
        byte[] pqcEncapsulation = pqcResult.getEncapsulation();

        // Combine shared secrets using HKDF
        byte[] combinedSecret = combineSecrets(
                classicalSharedSecret,
                pqcSharedSecret,
                kdfAlgorithm,
                keyLength / 8,
                symmetricAlgorithm);

        SecretKey hybridSecretKey = new SecretKeySpec(combinedSecret, symmetricAlgorithm);

        // Combine encapsulations
        byte[] hybridEncapsulation = combineEncapsulations(classicalEncapsulation, pqcEncapsulation);

        return new HybridEncapsulationResult(
                hybridEncapsulation,
                hybridSecretKey,
                classicalSharedSecret,
                pqcSharedSecret);
    }

    /**
     * Extracts the shared secret from a hybrid encapsulation.
     *
     * @param  hybridEncapsulation the hybrid encapsulation to extract from
     * @param  classicalPrivateKey the recipient's classical private key
     * @param  pqcPrivateKey       the recipient's PQC private key
     * @param  classicalKA         the classical key agreement instance
     * @param  pqcKemGenerator     the PQC KEM key generator
     * @param  symmetricAlgorithm  the symmetric algorithm for the shared secret
     * @param  keyLength           the symmetric key length in bits
     * @param  kdfAlgorithm        the KDF algorithm
     * @return                     the extracted shared secret key
     * @throws Exception           if extraction fails
     */
    public static SecretKey extract(
            byte[] hybridEncapsulation,
            PrivateKey classicalPrivateKey,
            PrivateKey pqcPrivateKey,
            KeyAgreement classicalKA,
            KeyGenerator pqcKemGenerator,
            String symmetricAlgorithm,
            int keyLength,
            String kdfAlgorithm)
            throws Exception {

        if (hybridEncapsulation == null || classicalPrivateKey == null || pqcPrivateKey == null
                || classicalKA == null || pqcKemGenerator == null || symmetricAlgorithm == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }

        // Parse the hybrid encapsulation
        HybridEncapsulationComponents components = parse(hybridEncapsulation);

        // Reconstruct the ephemeral public key from classical encapsulation
        PublicKey ephemeralPublicKey = reconstructPublicKey(
                components.classicalEncapsulation(),
                classicalPrivateKey.getAlgorithm());

        // Perform classical key agreement
        classicalKA.init(classicalPrivateKey);
        classicalKA.doPhase(ephemeralPublicKey, true);
        byte[] classicalSharedSecret = classicalKA.generateSecret();

        // Perform PQC KEM extraction
        pqcKemGenerator.init(
                new KEMExtractSpec(pqcPrivateKey, components.pqcEncapsulation(), symmetricAlgorithm, keyLength),
                new SecureRandom());
        SecretKeyWithEncapsulation pqcResult = (SecretKeyWithEncapsulation) pqcKemGenerator.generateKey();
        byte[] pqcSharedSecret = pqcResult.getEncoded();

        // Combine shared secrets using HKDF
        byte[] combinedSecret = combineSecrets(
                classicalSharedSecret,
                pqcSharedSecret,
                kdfAlgorithm,
                keyLength / 8,
                symmetricAlgorithm);

        return new SecretKeySpec(combinedSecret, symmetricAlgorithm);
    }

    /**
     * Parses a hybrid encapsulation into its component parts.
     *
     * @param  hybridEncapsulation      the hybrid encapsulation to parse
     * @return                          the parsed components
     * @throws IllegalArgumentException if the format is invalid
     */
    public static HybridEncapsulationComponents parse(byte[] hybridEncapsulation) {
        if (hybridEncapsulation == null || hybridEncapsulation.length < 5) {
            throw new IllegalArgumentException("Invalid hybrid encapsulation: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(hybridEncapsulation);

        // Read classical encapsulation length
        int classicalLength = buffer.getInt();
        if (classicalLength <= 0 || classicalLength > hybridEncapsulation.length - 4) {
            throw new IllegalArgumentException("Invalid hybrid encapsulation: invalid classical length");
        }

        // Read classical encapsulation
        byte[] classicalEncapsulation = new byte[classicalLength];
        buffer.get(classicalEncapsulation);

        // Read PQC encapsulation (remaining bytes)
        int pqcLength = buffer.remaining();
        if (pqcLength <= 0) {
            throw new IllegalArgumentException("Invalid hybrid encapsulation: missing PQC encapsulation");
        }
        byte[] pqcEncapsulation = new byte[pqcLength];
        buffer.get(pqcEncapsulation);

        return new HybridEncapsulationComponents(classicalEncapsulation, pqcEncapsulation);
    }

    /**
     * Combines classical and PQC encapsulations into a hybrid encapsulation.
     */
    public static byte[] combineEncapsulations(byte[] classicalEncapsulation, byte[] pqcEncapsulation) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + classicalEncapsulation.length + pqcEncapsulation.length);
        buffer.putInt(classicalEncapsulation.length);
        buffer.put(classicalEncapsulation);
        buffer.put(pqcEncapsulation);
        return buffer.array();
    }

    /**
     * Combines two shared secrets using HKDF.
     */
    private static byte[] combineSecrets(
            byte[] secret1,
            byte[] secret2,
            String kdfAlgorithm,
            int outputLength,
            String info)
            throws Exception {

        // Concatenate the secrets as input key material
        byte[] ikm = new byte[secret1.length + secret2.length];
        System.arraycopy(secret1, 0, ikm, 0, secret1.length);
        System.arraycopy(secret2, 0, ikm, secret1.length, secret2.length);

        // Use HKDF to derive the final key
        HKDFBytesGenerator hkdf = createHKDF(kdfAlgorithm);
        hkdf.init(new HKDFParameters(ikm, null, info.getBytes()));

        byte[] output = new byte[outputLength];
        hkdf.generateBytes(output, 0, outputLength);

        return output;
    }

    /**
     * Creates an HKDF instance based on the algorithm name.
     */
    private static HKDFBytesGenerator createHKDF(String algorithm) {
        return switch (algorithm) {
            case "HKDF-SHA256" -> new HKDFBytesGenerator(new SHA256Digest());
            case "HKDF-SHA384" -> new HKDFBytesGenerator(new SHA384Digest());
            case "HKDF-SHA512" -> new HKDFBytesGenerator(new SHA512Digest());
            default -> new HKDFBytesGenerator(new SHA256Digest());
        };
    }

    /**
     * Generates an ephemeral key pair for the given algorithm.
     */
    private static KeyPair generateEphemeralKeyPair(String algorithm) throws Exception {
        KeyPairGenerator kpg;

        if ("EC".equals(algorithm)) {
            kpg = KeyPairGenerator.getInstance("EC");
            // Default to P-256
            kpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        } else if ("X25519".equals(algorithm) || "XDH".equals(algorithm)) {
            kpg = KeyPairGenerator.getInstance("X25519");
        } else if ("X448".equals(algorithm)) {
            kpg = KeyPairGenerator.getInstance("X448");
        } else {
            // For other algorithms, try to use the same algorithm
            kpg = KeyPairGenerator.getInstance(algorithm);
        }

        return kpg.generateKeyPair();
    }

    /**
     * Reconstructs a public key from encoded bytes.
     */
    private static PublicKey reconstructPublicKey(byte[] encoded, String algorithm) throws Exception {
        java.security.KeyFactory kf;

        if ("EC".equals(algorithm) || "ECDH".equals(algorithm)) {
            kf = java.security.KeyFactory.getInstance("EC");
        } else if ("X25519".equals(algorithm) || "XDH".equals(algorithm)) {
            kf = java.security.KeyFactory.getInstance("X25519");
        } else if ("X448".equals(algorithm)) {
            kf = java.security.KeyFactory.getInstance("X448");
        } else {
            kf = java.security.KeyFactory.getInstance(algorithm);
        }

        java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(encoded);
        return kf.generatePublic(keySpec);
    }

    /**
     * Record holding the result of a hybrid encapsulation operation.
     */
    public record HybridEncapsulationResult(
            byte[] encapsulation,
            SecretKey sharedSecret,
            byte[] classicalSharedSecret,
            byte[] pqcSharedSecret) {
    }

    /**
     * Record holding the parsed components of a hybrid encapsulation.
     */
    public record HybridEncapsulationComponents(byte[] classicalEncapsulation, byte[] pqcEncapsulation) {
    }
}
