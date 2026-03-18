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
package org.apache.camel.component.pqc;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for input validation of algorithm combinations and key sizes in PQCProducer. Validates that invalid symmetric
 * key lengths are rejected at startup and that non-KEM operations are not affected by key length validation.
 */
public class PQCInputValidationTest extends CamelTestSupport {

    private final List<PQCEndpoint> createdEndpoints = new ArrayList<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @AfterEach
    void cleanupEndpoints() {
        for (PQCEndpoint ep : createdEndpoints) {
            try {
                ep.stop();
            } catch (Exception ignored) {
            }
        }
        createdEndpoints.clear();
    }

    // -- Invalid symmetric key length tests (should throw at startup) --

    @Test
    void testInvalidAESKeyLength64ThrowsAtStartup() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=AES&symmetricKeyLength=64"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid symmetric key length 64 for algorithm AES");
    }

    @Test
    void testInvalidAESKeyLength512ThrowsAtStartup() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=AES&symmetricKeyLength=512"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid symmetric key length 512 for algorithm AES");
    }

    @Test
    void testInvalidChaChaKeyLength128ThrowsAtStartup() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=CHACHA7539&symmetricKeyLength=128"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid symmetric key length 128 for algorithm CHACHA7539");
    }

    @Test
    void testInvalidGOSTKeyLength128ThrowsAtStartup() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=GOST28147&symmetricKeyLength=128"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid symmetric key length 128 for algorithm GOST28147");
    }

    @Test
    void testInvalidHC256KeyLength128ThrowsAtStartup() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=extractSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=HC256&symmetricKeyLength=128"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid symmetric key length 128 for algorithm HC256");
    }

    @Test
    void testInvalidDESedeKeyLength64ThrowsAtStartup() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=DESEDE&symmetricKeyLength=64"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid symmetric key length 64 for algorithm DESEDE");
    }

    @Test
    void testInvalidKeyLengthForHybridKEMThrowsAtStartup() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=hybridGenerateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=AES&symmetricKeyLength=64"
                                              + "&keyEncapsulationAlgorithm=MLKEM"
                                              + "&classicalKEMAlgorithm=X25519");

        assertThatThrownBy(producer::start)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid symmetric key length 64 for algorithm AES");
    }

    // -- Valid symmetric key length tests (should start without error) --

    @Test
    void testValidAESKeyLength128Accepted() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=AES&symmetricKeyLength=128"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        try {
            assertThatCode(producer::start).doesNotThrowAnyException();
        } finally {
            producer.stop();
        }
    }

    @Test
    void testValidAESKeyLength256Accepted() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=AES&symmetricKeyLength=256"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        try {
            assertThatCode(producer::start).doesNotThrowAnyException();
        } finally {
            producer.stop();
        }
    }

    @Test
    void testValidChaChaKeyLength256Accepted() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=CHACHA7539&symmetricKeyLength=256"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        try {
            assertThatCode(producer::start).doesNotThrowAnyException();
        } finally {
            producer.stop();
        }
    }

    @Test
    void testValidDESedeKeyLength192Accepted() throws Exception {
        PQCProducer producer = createProducer(
                "pqc:test?operation=generateSecretKeyEncapsulation"
                                              + "&symmetricKeyAlgorithm=DESEDE&symmetricKeyLength=192"
                                              + "&keyEncapsulationAlgorithm=MLKEM");

        try {
            assertThatCode(producer::start).doesNotThrowAnyException();
        } finally {
            producer.stop();
        }
    }

    // -- Key length not validated for non-KEM operations --

    @Test
    void testKeyLengthNotValidatedForSignatureOperations() throws Exception {
        // symmetricKeyLength=64 is invalid for AES but should not matter for sign operations
        PQCProducer producer = createProducer(
                "pqc:test?operation=sign&signatureAlgorithm=MLDSA"
                                              + "&symmetricKeyAlgorithm=AES&symmetricKeyLength=64");

        try {
            assertThatCode(producer::start).doesNotThrowAnyException();
        } finally {
            producer.stop();
        }
    }

    // -- Helper --

    private PQCProducer createProducer(String uri) throws Exception {
        PQCComponent component = context.getComponent("pqc", PQCComponent.class);
        PQCEndpoint endpoint = (PQCEndpoint) component.createEndpoint(uri);
        createdEndpoints.add(endpoint);
        endpoint.start();
        return new PQCProducer(endpoint);
    }
}
