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
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.camel.component.pqc.BouncyCastleKemSupport;
import org.apache.camel.component.pqc.PQCAlgorithmId;
import org.apache.camel.component.pqc.PQCSymmetricAlgorithms;
import org.apache.camel.util.SecureRandomHelper;

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
 * The {@link KeyAgreement} and {@link KeyGenerator} passed in may be shared by concurrent callers. Both keep state from
 * their {@code init()} call to the call that returns the result and are not thread-safe. Classical key-agreement
 * sequences are synchronized on the supplied instance; PQC KEM sequences delegate synchronization to
 * {@link BouncyCastleKemSupport}.
 * <p>
 * <strong>Wire format v2</strong> (default since Camel 4.24):
 *
 * <pre>
 * [2 bytes: magic 0x50 0x51 ("PQ")]
 * [1 byte:  version 0x02]
 * [1 byte:  format type 0x02 = hybrid KEM]
 * [2 bytes: classical KEM algorithm ID]
 * [2 bytes: PQC KEM algorithm ID]
 * [4 bytes: classical encapsulation length]
 * [N bytes: classical encapsulation]
 * [M bytes: PQC encapsulation]
 * </pre>
 * <p>
 * <strong>Wire format v1</strong> (legacy, still accepted on input):
 *
 * <pre>
 * [4 bytes: classical encapsulation length]
 * [N bytes: classical encapsulation]
 * [M bytes: PQC encapsulation]
 * </pre>
 *
 * The two formats are distinguished on input by checking the first two bytes for the magic sequence {@code 0x50 0x51}.
 */
public final class HybridKEM {

    /** Wire format magic bytes: ASCII "PQ" (0x50 0x51). */
    static final byte MAGIC_BYTE_1 = 0x50;
    /** Wire format magic bytes: ASCII "PQ" (0x50 0x51). */
    static final byte MAGIC_BYTE_2 = 0x51;
    /** Current wire format version. */
    static final byte VERSION_V2 = 0x02;
    /** Format type identifier for hybrid KEM encapsulations. */
    static final byte FORMAT_HYBRID_KEM = 0x02;
    /** Total byte count of the v2 header (magic + version + type + 2 algorithm IDs + payload length). */
    static final int V2_HEADER_SIZE = 12;

    private HybridKEM() {
        // Utility class
    }

    /**
     * Generates a hybrid encapsulation using both classical ECDH/XDH and PQC KEM. The encapsulation uses wire format
     * v2, which embeds the algorithm identifiers in the header.
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
        KeyPair ephemeralKeyPair = generateEphemeralKeyPair(classicalPublicKey);

        // Perform classical key agreement
        byte[] classicalSharedSecret;
        synchronized (classicalKA) {
            classicalKA.init(ephemeralKeyPair.getPrivate());
            classicalKA.doPhase(classicalPublicKey, true);
            classicalSharedSecret = classicalKA.generateSecret();
        }

        // The classical "encapsulation" is the ephemeral public key
        byte[] classicalEncapsulation = ephemeralKeyPair.getPublic().getEncoded();

        // Perform PQC KEM encapsulation
        BouncyCastleKemSupport.EncapsulationResult pqcResult;
        pqcResult = BouncyCastleKemSupport.encapsulate(
                pqcKemGenerator, pqcPublicKey, symmetricAlgorithm, keyLength, SecureRandomHelper.getSecureRandom());
        byte[] pqcSharedSecret = pqcResult.secretKey().getEncoded();
        byte[] pqcEncapsulation = pqcResult.encapsulation();

        // Combine shared secrets using HKDF
        byte[] combinedSecret = combineSecrets(
                classicalSharedSecret,
                pqcSharedSecret,
                kdfAlgorithm,
                keyLength / 8,
                symmetricAlgorithm);

        // The label only: the value handed to combineSecrets above is the HKDF info and must stay exactly as the
        // caller gave it, or every encapsulation produced by an earlier version stops decrypting.
        SecretKey hybridSecretKey = new SecretKeySpec(combinedSecret, PQCSymmetricAlgorithms.toJceName(symmetricAlgorithm));

        // Resolve algorithm IDs for the v2 header
        PQCAlgorithmId classicalAlgId = PQCAlgorithmId.fromJcaName(classicalKA.getAlgorithm());
        PQCAlgorithmId pqcAlgId = PQCAlgorithmId.fromJcaName(pqcKemGenerator.getAlgorithm());

        // Combine encapsulations in v2 wire format
        byte[] hybridEncapsulation = combineEncapsulations(classicalEncapsulation, pqcEncapsulation, classicalAlgId, pqcAlgId);

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

        // Validate algorithm IDs if present (v2 format)
        validateAlgorithmIds(components, classicalKA, pqcKemGenerator);

        // Reconstruct the ephemeral public key from classical encapsulation
        PublicKey ephemeralPublicKey = reconstructPublicKey(
                components.classicalEncapsulation(),
                classicalPrivateKey.getAlgorithm());

        // Perform classical key agreement
        byte[] classicalSharedSecret;
        synchronized (classicalKA) {
            classicalKA.init(classicalPrivateKey);
            classicalKA.doPhase(ephemeralPublicKey, true);
            classicalSharedSecret = classicalKA.generateSecret();
        }

        // Perform PQC KEM extraction
        SecretKey pqcResult;
        pqcResult = BouncyCastleKemSupport.decapsulate(
                pqcKemGenerator, pqcPrivateKey, components.pqcEncapsulation(), symmetricAlgorithm, keyLength,
                SecureRandomHelper.getSecureRandom());
        byte[] pqcSharedSecret = pqcResult.getEncoded();

        // Combine shared secrets using HKDF
        byte[] combinedSecret = combineSecrets(
                classicalSharedSecret,
                pqcSharedSecret,
                kdfAlgorithm,
                keyLength / 8,
                symmetricAlgorithm);

        // As in encapsulate(): the label is mapped, the HKDF info is not
        return new SecretKeySpec(combinedSecret, PQCSymmetricAlgorithms.toJceName(symmetricAlgorithm));
    }

    /**
     * Parses a hybrid encapsulation into its component parts. Auto-detects the wire format version by checking the
     * first two bytes for the magic sequence {@code 0x50 0x51}: v2 when found, v1 (legacy) otherwise.
     *
     * @param  hybridEncapsulation      the hybrid encapsulation to parse
     * @return                          the parsed components
     * @throws IllegalArgumentException if the format is invalid
     */
    public static HybridEncapsulationComponents parse(byte[] hybridEncapsulation) {
        if (hybridEncapsulation == null || hybridEncapsulation.length < 5) {
            throw new IllegalArgumentException("Invalid hybrid encapsulation: too short");
        }

        // Auto-detect v2 by checking the magic bytes
        if (hybridEncapsulation[0] == MAGIC_BYTE_1 && hybridEncapsulation[1] == MAGIC_BYTE_2) {
            return parseV2(hybridEncapsulation);
        }

        return parseV1(hybridEncapsulation);
    }

    /**
     * Parses a v1 (legacy) hybrid encapsulation.
     * <p>
     * Layout: {@code [4 bytes: classical encap length][classical encap][pqc encap]}
     */
    private static HybridEncapsulationComponents parseV1(byte[] hybridEncapsulation) {
        ByteBuffer buffer = ByteBuffer.wrap(hybridEncapsulation);

        int classicalLength = buffer.getInt();
        if (classicalLength <= 0 || classicalLength > hybridEncapsulation.length - 4) {
            throw new IllegalArgumentException("Invalid hybrid encapsulation: invalid classical length");
        }

        byte[] classicalEncapsulation = new byte[classicalLength];
        buffer.get(classicalEncapsulation);

        int pqcLength = buffer.remaining();
        if (pqcLength <= 0) {
            throw new IllegalArgumentException("Invalid hybrid encapsulation: missing PQC encapsulation");
        }
        byte[] pqcEncapsulation = new byte[pqcLength];
        buffer.get(pqcEncapsulation);

        return new HybridEncapsulationComponents(classicalEncapsulation, pqcEncapsulation, null, null, 1);
    }

    /**
     * Parses a v2 hybrid encapsulation.
     * <p>
     * Layout:
     *
     * <pre>
     * [2 bytes: magic][1 byte: version][1 byte: format type]
     * [2 bytes: classical KEM algorithm ID][2 bytes: PQC KEM algorithm ID]
     * [4 bytes: classical encap length][N bytes: classical encap][M bytes: PQC encap]
     * </pre>
     */
    private static HybridEncapsulationComponents parseV2(byte[] hybridEncapsulation) {
        if (hybridEncapsulation.length < V2_HEADER_SIZE + 2) {
            throw new IllegalArgumentException("Invalid v2 hybrid encapsulation: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(hybridEncapsulation);

        // Skip magic (2 bytes)
        buffer.get(); // 0x50
        buffer.get(); // 0x51

        byte version = buffer.get();
        if (version != VERSION_V2) {
            throw new IllegalArgumentException("Invalid v2 hybrid encapsulation: unsupported version " + version);
        }

        byte formatType = buffer.get();
        if (formatType != FORMAT_HYBRID_KEM) {
            throw new IllegalArgumentException("Invalid v2 hybrid encapsulation: unexpected format type " + formatType);
        }

        // Read algorithm IDs (16-bit each)
        int classicalAlgRaw = buffer.getShort() & 0xFFFF;
        int pqcAlgRaw = buffer.getShort() & 0xFFFF;
        PQCAlgorithmId classicalAlgId = PQCAlgorithmId.fromId(classicalAlgRaw);
        PQCAlgorithmId pqcAlgId = PQCAlgorithmId.fromId(pqcAlgRaw);

        // Read classical encapsulation
        int classicalLength = buffer.getInt();
        int remaining = buffer.remaining();
        if (classicalLength <= 0 || classicalLength > remaining) {
            throw new IllegalArgumentException("Invalid v2 hybrid encapsulation: invalid classical length");
        }
        byte[] classicalEncapsulation = new byte[classicalLength];
        buffer.get(classicalEncapsulation);

        // Read PQC encapsulation (remaining bytes)
        int pqcLength = buffer.remaining();
        if (pqcLength <= 0) {
            throw new IllegalArgumentException("Invalid v2 hybrid encapsulation: missing PQC encapsulation");
        }
        byte[] pqcEncapsulation = new byte[pqcLength];
        buffer.get(pqcEncapsulation);

        return new HybridEncapsulationComponents(classicalEncapsulation, pqcEncapsulation, classicalAlgId, pqcAlgId, 2);
    }

    /**
     * Combines classical and PQC encapsulations into a v2 wire format hybrid encapsulation embedding the given
     * algorithm identifiers.
     *
     * @param  classicalEncapsulation the classical encapsulation bytes
     * @param  pqcEncapsulation       the PQC encapsulation bytes
     * @param  classicalAlgId         the classical KEM algorithm identifier
     * @param  pqcAlgId               the PQC KEM algorithm identifier
     * @return                        the combined v2 hybrid encapsulation
     */
    public static byte[] combineEncapsulations(
            byte[] classicalEncapsulation, byte[] pqcEncapsulation,
            PQCAlgorithmId classicalAlgId, PQCAlgorithmId pqcAlgId) {
        int totalSize = V2_HEADER_SIZE + classicalEncapsulation.length + pqcEncapsulation.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.put(MAGIC_BYTE_1);
        buffer.put(MAGIC_BYTE_2);
        buffer.put(VERSION_V2);
        buffer.put(FORMAT_HYBRID_KEM);
        buffer.putShort((short) classicalAlgId.getId());
        buffer.putShort((short) pqcAlgId.getId());
        buffer.putInt(classicalEncapsulation.length);
        buffer.put(classicalEncapsulation);
        buffer.put(pqcEncapsulation);
        return buffer.array();
    }

    /**
     * Combines classical and PQC encapsulations into a v2 wire format hybrid encapsulation without algorithm
     * identifiers. Equivalent to calling {@link #combineEncapsulations(byte[], byte[], PQCAlgorithmId, PQCAlgorithmId)}
     * with {@link PQCAlgorithmId#UNKNOWN} for both algorithm IDs.
     *
     * @param  classicalEncapsulation the classical encapsulation bytes
     * @param  pqcEncapsulation       the PQC encapsulation bytes
     * @return                        the combined v2 hybrid encapsulation
     */
    public static byte[] combineEncapsulations(byte[] classicalEncapsulation, byte[] pqcEncapsulation) {
        return combineEncapsulations(classicalEncapsulation, pqcEncapsulation, PQCAlgorithmId.UNKNOWN, PQCAlgorithmId.UNKNOWN);
    }

    /**
     * Validates that the algorithm IDs embedded in a v2 wire format match the algorithms configured in the KeyAgreement
     * and KeyGenerator instances. Skips validation for v1 payloads (null IDs) and for UNKNOWN algorithm IDs (the sender
     * may not have written a known ID).
     */
    private static void validateAlgorithmIds(
            HybridEncapsulationComponents components, KeyAgreement classicalKA, KeyGenerator pqcKemGenerator) {
        if (components.classicalAlgorithmId() != null && components.classicalAlgorithmId() != PQCAlgorithmId.UNKNOWN) {
            PQCAlgorithmId expected = PQCAlgorithmId.fromJcaName(classicalKA.getAlgorithm());
            if (expected != PQCAlgorithmId.UNKNOWN && expected != components.classicalAlgorithmId()) {
                throw new IllegalArgumentException(
                        "Header algorithm ID mismatch: expected " + expected + " but found "
                                                   + components.classicalAlgorithmId());
            }
        }
        if (components.pqcAlgorithmId() != null && components.pqcAlgorithmId() != PQCAlgorithmId.UNKNOWN) {
            PQCAlgorithmId expected = PQCAlgorithmId.fromJcaName(pqcKemGenerator.getAlgorithm());
            if (expected != PQCAlgorithmId.UNKNOWN && expected != components.pqcAlgorithmId()) {
                throw new IllegalArgumentException(
                        "Header algorithm ID mismatch: expected " + expected + " but found " + components.pqcAlgorithmId());
            }
        }
    }

    /**
     * Combines two shared secrets using HKDF.
     *
     * <p>
     * The combiner uses simple concatenation of the two secrets as the HKDF input key material. Binding the combined
     * secret to ciphertext or public-key material (as proposed in draft-irtf-cfrg-hybrid-kem) is not yet implemented
     * and is deferred to a future version.
     */
    private static byte[] combineSecrets(
            byte[] secret1,
            byte[] secret2,
            String kdfAlgorithm,
            int outputLength,
            String info)
            throws Exception {

        // Concatenate the secrets as input key material, preserving the existing hybrid KEM combiner.
        byte[] inputKeyMaterial = new byte[secret1.length + secret2.length];
        System.arraycopy(secret1, 0, inputKeyMaterial, 0, secret1.length);
        System.arraycopy(secret2, 0, inputKeyMaterial, secret1.length, secret2.length);

        try {
            // The info bytes are deliberately unchanged: they are part of the established wire compatibility.
            return Hkdf.derive(kdfAlgorithm, inputKeyMaterial, info.getBytes(StandardCharsets.UTF_8), outputLength);
        } finally {
            Arrays.fill(inputKeyMaterial, (byte) 0);
        }
    }

    /**
     * Generates the ephemeral key pair used for the classical half of the key agreement.
     * <p/>
     * The ephemeral key must live in the same group as the recipient's key, so the group is taken from that key rather
     * than from its algorithm name: {@code getAlgorithm()} returns a bare {@code EC} for every curve, so deriving the
     * curve from it would pin every agreement to one curve regardless of the key actually in use.
     */
    private static KeyPair generateEphemeralKeyPair(PublicKey recipientPublicKey) throws Exception {
        KeyPairGenerator kpg;

        if (recipientPublicKey instanceof ECPublicKey ecPublicKey) {
            kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(ecPublicKey.getParams(), SecureRandomHelper.getSecureRandom());
        } else if (recipientPublicKey instanceof XECPublicKey xecPublicKey
                && xecPublicKey.getParams() instanceof NamedParameterSpec namedSpec) {
            kpg = KeyPairGenerator.getInstance(namedSpec.getName());
            kpg.initialize(namedSpec, SecureRandomHelper.getSecureRandom());
        } else {
            // BouncyCastle XDH keys report the concrete name (X25519 / X448), which fully determines the group
            String algorithm = recipientPublicKey.getAlgorithm();
            if ("XDH".equals(algorithm)) {
                throw new InvalidKeyException(
                        "Cannot determine the X25519/X448 group of the classical public key of type "
                                              + recipientPublicKey.getClass().getName()
                                              + ": it exposes neither XECPublicKey parameters nor a concrete algorithm name");
            }
            kpg = KeyPairGenerator.getInstance(algorithm);
        }

        return kpg.generateKeyPair();
    }

    /**
     * Reconstructs a public key from encoded bytes. The X.509 encoding carries its own domain parameters, so a key
     * factory for the family is enough - naming a concrete curve here would reject the other curves of that family.
     */
    private static PublicKey reconstructPublicKey(byte[] encoded, String algorithm) throws Exception {
        KeyFactory kf;

        if ("EC".equals(algorithm) || "ECDH".equals(algorithm)) {
            kf = KeyFactory.getInstance("EC");
        } else if ("XDH".equals(algorithm)) {
            kf = KeyFactory.getInstance("XDH");
        } else {
            kf = KeyFactory.getInstance(algorithm);
        }

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
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
     * <p>
     * <strong>Warning:</strong> The algorithm IDs in the header are informational metadata written by the sender and
     * are <em>not</em> authenticated by any signature or MAC. Applications must not use these IDs to select which key
     * or algorithm to use for cryptographic operations; always use the algorithm configured by the operator.
     *
     * @param classicalEncapsulation the raw classical encapsulation bytes
     * @param pqcEncapsulation       the raw PQC encapsulation bytes
     * @param classicalAlgorithmId   the classical KEM algorithm identifier from the wire format header, or {@code null}
     *                               for v1 payloads
     * @param pqcAlgorithmId         the PQC KEM algorithm identifier from the wire format header, or {@code null} for
     *                               v1 payloads
     * @param version                the wire format version: {@code 1} for legacy v1, {@code 2} for v2
     */
    public record HybridEncapsulationComponents(
            byte[] classicalEncapsulation,
            byte[] pqcEncapsulation,
            PQCAlgorithmId classicalAlgorithmId,
            PQCAlgorithmId pqcAlgorithmId,
            int version) {
    }
}
