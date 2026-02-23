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

/**
 * Utility class for hybrid signature operations combining classical and post-quantum cryptography algorithms.
 * <p>
 * Hybrid signatures provide defense-in-depth by combining a classical signature (e.g., ECDSA, RSA) with a post-quantum
 * signature (e.g., ML-DSA, Dilithium). Both signatures must verify for the hybrid signature to be valid, ensuring
 * security even if one algorithm is compromised.
 * <p>
 * The wire format is: [4 bytes: classical sig length][classical sig][pqc sig]
 */
public final class HybridSignature {

    private HybridSignature() {
        // Utility class
    }

    /**
     * Creates a hybrid signature by signing data with both classical and PQC algorithms.
     *
     * @param  data                     the data to sign
     * @param  classicalPrivateKey      the classical private key (e.g., ECDSA, RSA)
     * @param  classicalSigner          the classical signature instance
     * @param  pqcPrivateKey            the PQC private key (e.g., ML-DSA, Dilithium)
     * @param  pqcSigner                the PQC signature instance
     * @return                          the hybrid signature containing both signatures
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

        // Combine: [4 bytes length][classical sig][pqc sig]
        return combineSignatures(classicalSignature, pqcSignature);
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
     * Parses a hybrid signature into its component parts.
     *
     * @param  hybridSignature          the hybrid signature to parse
     * @return                          the parsed components
     * @throws IllegalArgumentException if the signature format is invalid
     */
    public static HybridSignatureComponents parse(byte[] hybridSignature) {
        if (hybridSignature == null || hybridSignature.length < 5) {
            throw new IllegalArgumentException("Invalid hybrid signature: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(hybridSignature);

        // Read classical signature length
        int classicalLength = buffer.getInt();
        if (classicalLength <= 0 || classicalLength > hybridSignature.length - 4) {
            throw new IllegalArgumentException("Invalid hybrid signature: invalid classical signature length");
        }

        // Read classical signature
        byte[] classicalSignature = new byte[classicalLength];
        buffer.get(classicalSignature);

        // Read PQC signature (remaining bytes)
        int pqcLength = buffer.remaining();
        if (pqcLength <= 0) {
            throw new IllegalArgumentException("Invalid hybrid signature: missing PQC signature");
        }
        byte[] pqcSignature = new byte[pqcLength];
        buffer.get(pqcSignature);

        return new HybridSignatureComponents(classicalSignature, pqcSignature);
    }

    /**
     * Combines classical and PQC signatures into a hybrid signature.
     *
     * @param  classicalSignature the classical signature bytes
     * @param  pqcSignature       the PQC signature bytes
     * @return                    the combined hybrid signature
     */
    public static byte[] combineSignatures(byte[] classicalSignature, byte[] pqcSignature) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + classicalSignature.length + pqcSignature.length);
        buffer.putInt(classicalSignature.length);
        buffer.put(classicalSignature);
        buffer.put(pqcSignature);
        return buffer.array();
    }

    /**
     * Record holding the parsed components of a hybrid signature.
     */
    public record HybridSignatureComponents(byte[] classicalSignature, byte[] pqcSignature) {
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
