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
package org.apache.camel.component.as2.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AS2MicAlgorithmTest {

    @Test
    void test_md5() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("md5"), AS2MicAlgorithm.MD5.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("MD5"), AS2MicAlgorithm.MD5.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("md-5"), AS2MicAlgorithm.MD5.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("MD-5"), AS2MicAlgorithm.MD5.getJdkAlgorithmName());

        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("MD5"), AS2MicAlgorithm.MD5.getAs2AlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("md5"), AS2MicAlgorithm.MD5.getAs2AlgorithmName());
    }

    @Test
    void test_sha1() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha1"), AS2MicAlgorithm.SHA_1.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha-1"), AS2MicAlgorithm.SHA_1.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("SHA1"), AS2MicAlgorithm.SHA_1.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("SHA-1"), AS2MicAlgorithm.SHA_1.getJdkAlgorithmName());

        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("SHA-1"), AS2MicAlgorithm.SHA_1.getAs2AlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("sha-1"), AS2MicAlgorithm.SHA_1.getAs2AlgorithmName());
    }

    @Test
    void test_sha256() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha256"), AS2MicAlgorithm.SHA_256.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha-256"), AS2MicAlgorithm.SHA_256.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("SHA-256"), AS2MicAlgorithm.SHA_256.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("SHA256"), AS2MicAlgorithm.SHA_256.getJdkAlgorithmName());

        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("SHA-256"), AS2MicAlgorithm.SHA_256.getAs2AlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("sha-256"), AS2MicAlgorithm.SHA_256.getAs2AlgorithmName());
    }

    @Test
    void test_sha384() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha384"), AS2MicAlgorithm.SHA_384.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha-384"), AS2MicAlgorithm.SHA_384.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("SHA384"), AS2MicAlgorithm.SHA_384.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("SHA-384"), AS2MicAlgorithm.SHA_384.getJdkAlgorithmName());

        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("SHA-384"), AS2MicAlgorithm.SHA_384.getAs2AlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("sha-384"), AS2MicAlgorithm.SHA_384.getAs2AlgorithmName());
    }

    @Test
    void test_sha512() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha512"), AS2MicAlgorithm.SHA_512.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha-512"), AS2MicAlgorithm.SHA_512.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("SHA512"), AS2MicAlgorithm.SHA_512.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("SHA-512"), AS2MicAlgorithm.SHA_512.getJdkAlgorithmName());

        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("SHA-512"), AS2MicAlgorithm.SHA_512.getAs2AlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("sha-512"), AS2MicAlgorithm.SHA_512.getAs2AlgorithmName());
    }
}
