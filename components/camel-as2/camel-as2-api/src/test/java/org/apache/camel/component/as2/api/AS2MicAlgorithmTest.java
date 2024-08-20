package org.apache.camel.component.as2.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AS2MicAlgorithmTest {

    @Test
    void test_md5() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("md5"), AS2MicAlgorithm.MD5.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("md-5"), AS2MicAlgorithm.MD5.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("MD5"), AS2MicAlgorithm.MD5.getAs2AlgorithmName());
    }

    @Test
    void test_sha1() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha1"), AS2MicAlgorithm.SHA_1.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha-1"), AS2MicAlgorithm.SHA_1.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("SHA-1"), AS2MicAlgorithm.SHA_1.getAs2AlgorithmName());
    }

    @Test
    void test_sha256() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha256"), AS2MicAlgorithm.SHA_256.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha-256"), AS2MicAlgorithm.SHA_256.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("SHA-256"), AS2MicAlgorithm.SHA_256.getAs2AlgorithmName());
    }

    @Test
    void test_sha384() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha384"), AS2MicAlgorithm.SHA_384.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha-384"), AS2MicAlgorithm.SHA_384.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("SHA-384"), AS2MicAlgorithm.SHA_384.getAs2AlgorithmName());
    }

    @Test
    void test_sha512() {
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha512"), AS2MicAlgorithm.SHA_512.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getJdkAlgorithmName("sha-512"), AS2MicAlgorithm.SHA_512.getJdkAlgorithmName());
        assertEquals(AS2MicAlgorithm.getAS2AlgorithmName("SHA-512"), AS2MicAlgorithm.SHA_512.getAs2AlgorithmName());
    }
}