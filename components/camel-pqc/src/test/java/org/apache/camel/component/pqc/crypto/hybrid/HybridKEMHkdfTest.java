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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests that the JCA HKDF implementation is byte-for-byte compatible with the previous BC implementation. */
class HybridKEMHkdfTest {

    @Test
    void matchesRfc5869Sha256TestCaseThree() throws Exception {
        byte[] inputKeyMaterial = new byte[22];
        Arrays.fill(inputKeyMaterial, (byte) 0x0b);

        byte[] actual = Hkdf.derive("HKDF-SHA256", inputKeyMaterial, new byte[0], 42);

        assertArrayEquals(
                HexFormat.of().parseHex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d"
                                        + "9d201395faa4b61a96c8"),
                actual);
    }

    @ParameterizedTest
    @CsvSource({
            "HKDF-SHA256",
            "HKDF-SHA384",
            "HKDF-SHA512"
    })
    void preservesPreviousBouncyCastleOutput(String algorithm) throws Exception {
        byte[] firstSecret = sequence(37, 0x11);
        byte[] secondSecret = sequence(53, 0x37);
        String info = "DESEDE";
        int outputLength = 97;

        byte[] inputKeyMaterial = new byte[firstSecret.length + secondSecret.length];
        System.arraycopy(firstSecret, 0, inputKeyMaterial, 0, firstSecret.length);
        System.arraycopy(secondSecret, 0, inputKeyMaterial, firstSecret.length, secondSecret.length);
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);

        byte[] expected = previousBouncyCastleDerive(inputKeyMaterial, algorithm, outputLength, infoBytes);
        byte[] actual = Hkdf.derive(algorithm, inputKeyMaterial, infoBytes, outputLength);

        assertArrayEquals(expected, actual,
                "JCA HKDF must preserve the previous BC output for " + algorithm + " over multiple blocks");
    }

    @Test
    void rejectsUnsupportedKdfWithoutFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> Hkdf.derive("HKDF-SHA1", new byte[] { 1 }, new byte[0], 16));
    }

    private static byte[] previousBouncyCastleDerive(
            byte[] inputKeyMaterial, String algorithm, int outputLength, byte[] info) {
        Digest digest = switch (algorithm) {
            case "HKDF-SHA256" -> new SHA256Digest();
            case "HKDF-SHA384" -> new SHA384Digest();
            case "HKDF-SHA512" -> new SHA512Digest();
            default -> throw new IllegalArgumentException("Unsupported test KDF: " + algorithm);
        };
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(digest);
        hkdf.init(new HKDFParameters(inputKeyMaterial, null, info));
        byte[] output = new byte[outputLength];
        hkdf.generateBytes(output, 0, output.length);
        return output;
    }

    private static byte[] sequence(int length, int seed) {
        byte[] result = new byte[length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (seed + 13 * i);
        }
        return result;
    }
}
