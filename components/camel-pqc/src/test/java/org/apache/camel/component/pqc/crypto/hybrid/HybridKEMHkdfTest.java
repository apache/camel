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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    @MethodSource("hkdfKnownAnswerVectors")
    void matchesKnownAnswerVector(String algorithm, String expectedHex) throws Exception {
        byte[] firstSecret = sequence(37, 0x11);
        byte[] secondSecret = sequence(53, 0x37);
        String info = "DESEDE";
        int outputLength = 97;

        byte[] inputKeyMaterial = new byte[firstSecret.length + secondSecret.length];
        System.arraycopy(firstSecret, 0, inputKeyMaterial, 0, firstSecret.length);
        System.arraycopy(secondSecret, 0, inputKeyMaterial, firstSecret.length, secondSecret.length);
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);

        byte[] actual = Hkdf.derive(algorithm, inputKeyMaterial, infoBytes, outputLength);

        assertArrayEquals(HexFormat.of().parseHex(expectedHex), actual,
                "HKDF output must match the fixed RFC 5869-derived vector for " + algorithm);
    }

    /**
     * Fixed 97-byte, multi-block HKDF vectors calculated independently from the RFC 5869 construction. Keeping expected
     * values here avoids using Bouncy Castle internals as the test oracle.
     */
    private static Stream<Arguments> hkdfKnownAnswerVectors() {
        return Stream.of(
                Arguments.of("HKDF-SHA256",
                        "25ac8dad6e15df93950ba810c49348ad090d0ef64ea13b550e185401b801e2fa"
                                            + "9c69109cb373b8df12156c6531c48d61364cd596f1461f9bbf38793edf526db4"
                                            + "175311f840bde79cf61b95e8fd71eef8e2128f9124a60ac70637a65184c15e7b3c"),
                Arguments.of("HKDF-SHA384",
                        "c33e57ce3dcf3952428dbec2be00965d0da8e542a28c0199ea33177e42636179"
                                            + "b56ec2a7f9dd9c04b63af0027e190118e90e9d3187278538f5a5a894c13bdd7f"
                                            + "ac86385193305190d35a295da792e9458bdac7faf5484848c74a8ddf302c0b83cd"),
                Arguments.of("HKDF-SHA512",
                        "a318fddb6b93e963b9444cc535af1c8ff442143e17acace098bf5b3ab63acc80"
                                            + "05df1cb51980bfc48efd14a40f49f1a570243c6998486ebf4919bd3a8ed168c4"
                                            + "bc368e3d523332a42404492af05a98df99b4600564632086c3e1608624d037f0ae"));
    }

    @Test
    void rejectsUnsupportedKdfWithoutFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> Hkdf.derive("HKDF-SHA1", new byte[] { 1 }, new byte[0], 16));
    }

    private static byte[] sequence(int length, int seed) {
        byte[] result = new byte[length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (seed + 13 * i);
        }
        return result;
    }
}
