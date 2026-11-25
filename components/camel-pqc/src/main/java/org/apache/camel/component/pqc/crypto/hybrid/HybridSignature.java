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

import org.apache.camel.component.pqc.PQCAlgorithmId;

/**
 * Utility class for hybrid signature operations combining classical and post-quantum cryptography algorithms.
 * <p>
 * Hybrid signatures provide defense-in-depth by combining a classical signature (e.g., ECDSA, RSA) with a post-quantum
 * signature (e.g., ML-DSA). Both signatures must verify for the hybrid signature to be valid, ensuring security even if
 * one algorithm is compromised.
 * <p>
 * <strong>Wire format v2</strong> (default since Camel 4.24):
 *
 * <pre>
 * [2 bytes: magic 0x50 0x51 ("PQ")]
 * [1 byte:  version 0x02]
 * [1 byte:  format type 0x01 = hybrid signature]
 * [2 bytes: classical algorithm ID]
 * [2 bytes: PQC algorithm ID]
 * [4 bytes: classical signature length]
 * [N bytes: classical signature]
 * [M bytes: PQC signature]
 * </pre>
 * <p>
 * <strong>Wire format v1</strong> (legacy, still accepted on input):
 *
 * <pre>
 * [4 bytes: classical signature length]
 * [N bytes: classical signature]
 * [M bytes: PQC signature]
 * </pre>
 *
 * The two formats are distinguished on input by checking the first two bytes for the magic sequence {@code 0x50 0x51}.
 * No valid v1 payload has a classical-signature length above 1.3 billion, so false positives are impossible in
 * practice.
 */
public final class HybridSignature {

    /** Wire format magic bytes: ASCII "PQ" (0x50 0x51). */
    static final byte MAGIC_BYTE_1 = 0x50;
    /** Wire format magic bytes: ASCII "PQ" (0x50 0x51). */
    static final byte MAGIC_BYTE_2 = 0x51;
    /** Current wire format version. */
    static final byte VERSION_V2 = 0x02;
    /** Format type identifier for hybrid signatures. */
    static final byte FORMAT_HYBRID_SIGNATURE = 0x01;
    /** Total byte count of the v2 header (magic + version + type + 2 algorithm IDs + payload length). */
    static final int V2_HEADER_SIZE = 12;

    private HybridSignature() {
        // Utility class
    }

    /**
     * Creates a hybrid signature by signing data with both classical and PQC algorithms. The output uses wire format
     * v2, which embeds the algorithm identifiers in the header.
     *
     * @param  data                     the data to sign
     * @param  classicalPrivateKey      the classical private key (e.g., ECDSA, RSA)
     * @param  classicalSigner          the classical signature instance
     * @param  pqcPrivateKey            the PQC private key (e.g., ML-DSA)
     * @param  pqcSigner                the PQC signature instance
     * @return                          the hybrid signature in wire format v2
     * @throws InvalidKeyException      if a key is invalid
     * @throws SignatureException       if signing fails
     * @throws IllegalArgumentException if any parameter is null
     */
    public static byte[] sign(
            byte[] data,
            PrivateKey classicalPrivateKey,
            Signature classicalSigner,
            PrivateKey pqcPrivateKey,
            Signature pqcSigner)
            throws InvalidKeyException, SignatureException {

        if (data == null || classicalPrivateKey == null || classicalSigner == null
                || pqcPrivateKey == null || pqcSigner == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }

        // Create classical signature
        classicalSigner.initSign(classicalPrivateKey);
        classicalSigner.update(data);
        byte[] classicalSignature = classicalSigner.sign();

        // Create PQC signature
        pqcSigner.initSign(pqcPrivateKey);
        pqcSigner.update(data);
        byte[] pqcSignature = pqcSigner.sign();

        // Resolve algorithm IDs from the JCA algorithm names reported by the Signature instances
        PQCAlgorithmId classicalAlgId = PQCAlgorithmId.fromJcaName(classicalSigner.getAlgorithm());
        PQCAlgorithmId pqcAlgId = PQCAlgorithmId.fromJcaName(pqcSigner.getAlgorithm());

        // Produce v2 wire format with algorithm identifiers in the header
        return combineSignatures(classicalSignature, pqcSignature, classicalAlgId, pqcAlgId);
    }

    /**
     * Verifies a hybrid signature - BOTH signatures must be valid for verification to succeed.
     *
     * @param  data                the original data that was signed
     * @param  hybridSignature     the hybrid signature to verify
     * @param  classicalPublicKey  the classical public key
     * @param  classicalSigner     the classical signature instance
     * @param  pqcPublicKey        the PQC public key
     * @param  pqcSigner           the PQC signature instance
     * @return                     true if both signatures are valid, false otherwise
     * @throws InvalidKeyException if a key is invalid
     * @throws SignatureException  if verification fails unexpectedly
     */
    public static boolean verify(
            byte[] data,
            byte[] hybridSignature,
            PublicKey classicalPublicKey,
            Signature classicalSigner,
            PublicKey pqcPublicKey,
            Signature pqcSigner)
            throws InvalidKeyException, SignatureException {

        if (data == null || hybridSignature == null || classicalPublicKey == null
                || classicalSigner == null || pqcPublicKey == null || pqcSigner == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }

        // Parse the hybrid signature
        HybridSignatureComponents components = parse(hybridSignature);

        // Validate algorithm IDs if present (v2 format)
        validateAlgorithmIds(components, classicalSigner, pqcSigner);

        // Verify classical signature
        classicalSigner.initVerify(classicalPublicKey);
        classicalSigner.update(data);
        boolean classicalValid = classicalSigner.verify(components.classicalSignature());

        // Verify PQC signature
        pqcSigner.initVerify(pqcPublicKey);
        pqcSigner.update(data);
        boolean pqcValid = pqcSigner.verify(components.pqcSignature());

        // Both must be valid
        return classicalValid && pqcValid;
    }

    /**
     * Verifies a hybrid signature and returns detailed results for each component.
     *
     * @param  data                the original data that was signed
     * @param  hybridSignature     the hybrid signature to verify
     * @param  classicalPublicKey  the classical public key
     * @param  classicalSigner     the classical signature instance
     * @param  pqcPublicKey        the PQC public key
     * @param  pqcSigner           the PQC signature instance
     * @return                     verification result with details
     * @throws InvalidKeyException if a key is invalid
     * @throws SignatureException  if verification fails unexpectedly
     */
    public static HybridVerificationResult verifyDetailed(
            byte[] data,
            byte[] hybridSignature,
            PublicKey classicalPublicKey,
            Signature classicalSigner,
            PublicKey pqcPublicKey,
            Signature pqcSigner)
            throws InvalidKeyException, SignatureException {

        if (data == null || hybridSignature == null || classicalPublicKey == null
                || classicalSigner == null || pqcPublicKey == null || pqcSigner == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }

        HybridSignatureComponents components = parse(hybridSignature);

        // Validate algorithm IDs if present (v2 format)
        validateAlgorithmIds(components, classicalSigner, pqcSigner);

        // Verify classical signature
        classicalSigner.initVerify(classicalPublicKey);
        classicalSigner.update(data);
        boolean classicalValid = classicalSigner.verify(components.classicalSignature());

        // Verify PQC signature
        pqcSigner.initVerify(pqcPublicKey);
        pqcSigner.update(data);
        boolean pqcValid = pqcSigner.verify(components.pqcSignature());

        return new HybridVerificationResult(classicalValid, pqcValid);
    }

    /**
     * Validates that the algorithm IDs embedded in a v2 wire format match the algorithms configured in the Signature
     * instances. Skips validation for v1 payloads (null IDs) and for UNKNOWN algorithm IDs (the sender may not have
     * written a known ID).
     */
    private static void validateAlgorithmIds(
            HybridSignatureComponents components, Signature classicalSigner, Signature pqcSigner) {
        if (components.classicalAlgorithmId() != null && components.classicalAlgorithmId() != PQCAlgorithmId.UNKNOWN) {
            PQCAlgorithmId expected = PQCAlgorithmId.fromJcaName(classicalSigner.getAlgorithm());
            if (expected != PQCAlgorithmId.UNKNOWN && expected != components.classicalAlgorithmId()) {
                throw new IllegalArgumentException(
                        "Header algorithm ID mismatch: expected " + expected + " but found "
                                                   + components.classicalAlgorithmId());
            }
        }
        if (components.pqcAlgorithmId() != null && components.pqcAlgorithmId() != PQCAlgorithmId.UNKNOWN) {
            PQCAlgorithmId expected = PQCAlgorithmId.fromJcaName(pqcSigner.getAlgorithm());
            if (expected != PQCAlgorithmId.UNKNOWN && expected != components.pqcAlgorithmId()) {
                throw new IllegalArgumentException(
                        "Header algorithm ID mismatch: expected " + expected + " but found " + components.pqcAlgorithmId());
            }
        }
    }

    /**
     * Parses a hybrid signature into its component parts. Auto-detects the wire format version by checking the first
     * two bytes for the magic sequence {@code 0x50 0x51}: v2 when found, v1 (legacy) otherwise.
     *
     * @param  hybridSignature          the hybrid signature to parse
     * @return                          the parsed components
     * @throws IllegalArgumentException if the signature format is invalid
     */
    public static HybridSignatureComponents parse(byte[] hybridSignature) {
        if (hybridSignature == null || hybridSignature.length < 5) {
            throw new IllegalArgumentException("Invalid hybrid signature: too short");
        }

        // Auto-detect v2 by checking the magic bytes
        if (hybridSignature[0] == MAGIC_BYTE_1 && hybridSignature[1] == MAGIC_BYTE_2) {
            return parseV2(hybridSignature);
        }

        return parseV1(hybridSignature);
    }

    /**
     * Parses a v1 (legacy) hybrid signature.
     * <p>
     * Layout: {@code [4 bytes: classical sig length][classical sig][pqc sig]}
     */
    private static HybridSignatureComponents parseV1(byte[] hybridSignature) {
        ByteBuffer buffer = ByteBuffer.wrap(hybridSignature);

        int classicalLength = buffer.getInt();
        if (classicalLength <= 0 || classicalLength > hybridSignature.length - 4) {
            throw new IllegalArgumentException("Invalid hybrid signature: invalid classical signature length");
        }

        byte[] classicalSignature = new byte[classicalLength];
        buffer.get(classicalSignature);

        int pqcLength = buffer.remaining();
        if (pqcLength <= 0) {
            throw new IllegalArgumentException("Invalid hybrid signature: missing PQC signature");
        }
        byte[] pqcSignature = new byte[pqcLength];
        buffer.get(pqcSignature);

        return new HybridSignatureComponents(classicalSignature, pqcSignature, null, null, 1);
    }

    /**
     * Parses a v2 hybrid signature.
     * <p>
     * Layout:
     *
     * <pre>
     * [2 bytes: magic][1 byte: version][1 byte: format type]
     * [2 bytes: classical algorithm ID][2 bytes: PQC algorithm ID]
     * [4 bytes: classical sig length][N bytes: classical sig][M bytes: PQC sig]
     * </pre>
     */
    private static HybridSignatureComponents parseV2(byte[] hybridSignature) {
        if (hybridSignature.length < V2_HEADER_SIZE + 2) {
            throw new IllegalArgumentException("Invalid v2 hybrid signature: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(hybridSignature);

        // Skip magic (2 bytes)
        buffer.get(); // 0x50
        buffer.get(); // 0x51

        byte version = buffer.get();
        if (version != VERSION_V2) {
            throw new IllegalArgumentException("Invalid v2 hybrid signature: unsupported version " + version);
        }

        byte formatType = buffer.get();
        if (formatType != FORMAT_HYBRID_SIGNATURE) {
            throw new IllegalArgumentException("Invalid v2 hybrid signature: unexpected format type " + formatType);
        }

        // Read algorithm IDs (16-bit each)
        int classicalAlgRaw = buffer.getShort() & 0xFFFF;
        int pqcAlgRaw = buffer.getShort() & 0xFFFF;
        PQCAlgorithmId classicalAlgId = PQCAlgorithmId.fromId(classicalAlgRaw);
        PQCAlgorithmId pqcAlgId = PQCAlgorithmId.fromId(pqcAlgRaw);

        // Read classical signature
        int classicalLength = buffer.getInt();
        int remaining = buffer.remaining();
        if (classicalLength <= 0 || classicalLength > remaining) {
            throw new IllegalArgumentException("Invalid v2 hybrid signature: invalid classical signature length");
        }
        byte[] classicalSignature = new byte[classicalLength];
        buffer.get(classicalSignature);

        // Read PQC signature (remaining bytes)
        int pqcLength = buffer.remaining();
        if (pqcLength <= 0) {
            throw new IllegalArgumentException("Invalid v2 hybrid signature: missing PQC signature");
        }
        byte[] pqcSignature = new byte[pqcLength];
        buffer.get(pqcSignature);

        return new HybridSignatureComponents(classicalSignature, pqcSignature, classicalAlgId, pqcAlgId, 2);
    }

    /**
     * Combines classical and PQC signatures into a v2 wire format hybrid signature embedding the given algorithm
     * identifiers.
     *
     * @param  classicalSignature the classical signature bytes
     * @param  pqcSignature       the PQC signature bytes
     * @param  classicalAlgId     the classical algorithm identifier (use {@link PQCAlgorithmId#UNKNOWN} if not known)
     * @param  pqcAlgId           the PQC algorithm identifier (use {@link PQCAlgorithmId#UNKNOWN} if not known)
     * @return                    the combined v2 hybrid signature
     */
    public static byte[] combineSignatures(
            byte[] classicalSignature, byte[] pqcSignature,
            PQCAlgorithmId classicalAlgId, PQCAlgorithmId pqcAlgId) {
        int totalSize = V2_HEADER_SIZE + classicalSignature.length + pqcSignature.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.put(MAGIC_BYTE_1);
        buffer.put(MAGIC_BYTE_2);
        buffer.put(VERSION_V2);
        buffer.put(FORMAT_HYBRID_SIGNATURE);
        buffer.putShort((short) classicalAlgId.getId());
        buffer.putShort((short) pqcAlgId.getId());
        buffer.putInt(classicalSignature.length);
        buffer.put(classicalSignature);
        buffer.put(pqcSignature);
        return buffer.array();
    }

    /**
     * Combines classical and PQC signatures into a v2 wire format hybrid signature without algorithm identifiers.
     * Equivalent to calling {@link #combineSignatures(byte[], byte[], PQCAlgorithmId, PQCAlgorithmId)} with
     * {@link PQCAlgorithmId#UNKNOWN} for both algorithm IDs.
     *
     * @param  classicalSignature the classical signature bytes
     * @param  pqcSignature       the PQC signature bytes
     * @return                    the combined v2 hybrid signature
     */
    public static byte[] combineSignatures(byte[] classicalSignature, byte[] pqcSignature) {
        return combineSignatures(classicalSignature, pqcSignature, PQCAlgorithmId.UNKNOWN, PQCAlgorithmId.UNKNOWN);
    }

    /**
     * Record holding the parsed components of a hybrid signature.
     * <p>
     * <strong>Warning:</strong> The algorithm IDs in the header are informational metadata written by the sender and
     * are <em>not</em> authenticated by any signature or MAC. Applications must not use these IDs to select which key
     * or algorithm to use for cryptographic operations; always use the algorithm configured by the operator.
     *
     * @param classicalSignature   the raw classical signature bytes
     * @param pqcSignature         the raw PQC signature bytes
     * @param classicalAlgorithmId the classical algorithm identifier from the wire format header, or {@code null} for
     *                             v1 payloads
     * @param pqcAlgorithmId       the PQC algorithm identifier from the wire format header, or {@code null} for v1
     *                             payloads
     * @param version              the wire format version: {@code 1} for legacy v1, {@code 2} for v2
     */
    public record HybridSignatureComponents(
            byte[] classicalSignature,
            byte[] pqcSignature,
            PQCAlgorithmId classicalAlgorithmId,
            PQCAlgorithmId pqcAlgorithmId,
            int version) {
    }

    /**
     * Record holding the verification results for a hybrid signature.
     */
    public record HybridVerificationResult(boolean classicalValid, boolean pqcValid) {

        /**
         * Returns true if both signatures are valid.
         */
        public boolean isValid() {
            return classicalValid && pqcValid;
        }
    }
}
