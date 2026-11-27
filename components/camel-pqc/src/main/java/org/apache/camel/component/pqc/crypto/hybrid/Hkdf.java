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

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal HKDF implementation using the JCA {@link Mac} API.
 * <p>
 * The hybrid KEM uses HKDF with the RFC 5869 default salt (HashLen zero bytes), so this helper intentionally exposes no
 * salt parameter. This preserves the previous Bouncy Castle HKDFParameters(inputKeyMaterial, null, info) behavior.
 */
final class Hkdf {

    private Hkdf() {
    }

    static byte[] derive(String algorithm, byte[] inputKeyMaterial, byte[] info, int outputLength)
            throws GeneralSecurityException {
        String macAlgorithm = switch (algorithm) {
            case "HKDF-SHA256" -> "HmacSHA256";
            case "HKDF-SHA384" -> "HmacSHA384";
            case "HKDF-SHA512" -> "HmacSHA512";
            // Do not silently fall back: both sides must agree on the KDF.
            default -> throw new IllegalArgumentException(
                    "Unsupported hybrid KDF algorithm: " + algorithm
                                                          + ". Supported values are HKDF-SHA256, HKDF-SHA384 and HKDF-SHA512");
        };
        int hashLength = switch (algorithm) {
            case "HKDF-SHA256" -> 32;
            case "HKDF-SHA384" -> 48;
            case "HKDF-SHA512" -> 64;
            default -> throw new IllegalArgumentException("Unsupported hybrid KDF algorithm: " + algorithm);
        };
        if (inputKeyMaterial == null || info == null) {
            throw new IllegalArgumentException("HKDF input key material and info must be non-null");
        }
        if (outputLength < 0 || outputLength > 255 * hashLength) {
            throw new IllegalArgumentException(
                    "HKDF output length must be between 0 and " + (255 * hashLength)
                                               + " bytes for " + algorithm);
        }

        Mac mac = Mac.getInstance(macAlgorithm);

        // RFC 5869: an omitted salt is HashLen zero bytes.
        byte[] salt = new byte[hashLength];
        mac.init(new SecretKeySpec(salt, macAlgorithm));
        byte[] pseudorandomKey = mac.doFinal(inputKeyMaterial);
        Arrays.fill(salt, (byte) 0);

        byte[] output = new byte[outputLength];
        byte[] previousBlock = new byte[0];
        try {
            mac.init(new SecretKeySpec(pseudorandomKey, macAlgorithm));
            int outputOffset = 0;
            for (int blockNumber = 1; outputOffset < outputLength; blockNumber++) {
                mac.update(previousBlock);
                mac.update(info);
                mac.update((byte) blockNumber);
                byte[] nextBlock = mac.doFinal();

                int bytesToCopy = Math.min(nextBlock.length, outputLength - outputOffset);
                System.arraycopy(nextBlock, 0, output, outputOffset, bytesToCopy);
                outputOffset += bytesToCopy;

                Arrays.fill(previousBlock, (byte) 0);
                previousBlock = nextBlock;
            }
            return output;
        } finally {
            Arrays.fill(pseudorandomKey, (byte) 0);
            Arrays.fill(previousBlock, (byte) 0);
        }
    }
}
