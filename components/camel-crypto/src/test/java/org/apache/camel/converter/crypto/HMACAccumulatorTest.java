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
package org.apache.camel.converter.crypto;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;

import org.apache.camel.converter.crypto.HMACAccumulator.CircularBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HMACAccumulatorTest {
    private byte[] payload = {
            0x00, 0x00, 0x11, 0x11, 0x22, 0x22, 0x33, 0x33, 0x44, 0x44, 0x55, 0x55, 0x66, 0x66, 0x77, 0x77, (byte) 0x88,
            (byte) 0x88, (byte) 0x99, (byte) 0x99 };
    private byte[] expected;
    private Key key;

    @BeforeEach
    public void setupKeyAndExpectedMac() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("DES");
        key = generator.generateKey();
        createExpectedMac();
    }

    private void createExpectedMac() throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(key);
        expected = mac.doFinal(payload);
    }

    @Test
    void testEncryptionPhaseCalculation() throws Exception {
        int buffersize = 256;
        byte[] buffer = new byte[buffersize];
        System.arraycopy(payload, 0, buffer, 0, payload.length);

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        builder.encryptUpdate(buffer, 20);
        assertMacs(expected, builder.getCalculatedMac());
    }

    @Test
    void testDecryptionWhereBufferSizeIsGreaterThanDataSize() throws Exception {
        int buffersize = 256;
        byte[] buffer = initializeBuffer(buffersize);

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        builder.decryptUpdate(buffer, 40);
        validate(builder);
    }

    @Test
    void testValidateFailsWhenAppendedMacDoesNotMatch() throws Exception {
        int buffersize = 256;
        byte[] buffer = initializeBuffer(buffersize);
        // Corrupt the first byte of the appended MAC so it no longer matches the calculated MAC
        buffer[payload.length] ^= 0x01;

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        builder.decryptUpdate(buffer, 40);
        assertThrows(IllegalStateException.class, builder::validate);
    }

    @Test
    void testValidateAfterCipherFailureStillComputesMacAndFails() throws Exception {
        int buffersize = 256;
        byte[] buffer = initializeBuffer(buffersize);

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        builder.decryptUpdate(buffer, 40);
        // fails although the appended MAC matches, with the same message as a MAC mismatch
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> builder.validate(true));
        assertEquals(HMACAccumulator.AUTHENTICATION_FAILED, e.getMessage());
        // the MAC was still finalized and compared, as on the MAC-mismatch path
        assertMacs(expected, builder.getCalculatedMac());
        assertMacs(expected, builder.getAppendedMac());
    }

    @Test
    void testDecryptionWhereMacOverlaps() throws Exception {
        int buffersize = 32;
        byte[] buffer = new byte[buffersize];
        int overlap = buffersize - payload.length;
        System.arraycopy(payload, 0, buffer, 0, payload.length);
        System.arraycopy(expected, 0, buffer, payload.length, overlap);

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        builder.decryptUpdate(buffer, buffersize);
        System.arraycopy(expected, overlap, buffer, 0, 20 - overlap);
        builder.decryptUpdate(buffer, 20 - overlap);
        validate(builder);
    }

    @Test
    void testDecryptionWhereDataIsMultipleOfBufferLength() throws Exception {
        int buffersize = 20;
        byte[] buffer = new byte[buffersize];
        System.arraycopy(payload, 0, buffer, 0, payload.length);

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        builder.decryptUpdate(buffer, buffersize);
        System.arraycopy(expected, 0, buffer, 0, expected.length);
        builder.decryptUpdate(buffer, 20);
        validate(builder);
    }

    @Test
    void testDecryptionWhereThereIsNoPayloadData() throws Exception {
        int buffersize = 20;
        byte[] buffer = new byte[buffersize];
        payload = new byte[0];
        createExpectedMac();

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        System.arraycopy(expected, 0, buffer, 0, expected.length);
        builder.decryptUpdate(buffer, 20);
        validate(builder);
    }

    @Test
    void testDecryptionMultipleReadsSmallerThanBufferSize() throws Exception {
        int buffersize = 256;
        byte[] buffer = new byte[buffersize];

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        int read = payload.length / 2;
        System.arraycopy(payload, 0, buffer, 0, read);
        builder.decryptUpdate(buffer, read);
        System.arraycopy(payload, read, buffer, 0, read);
        builder.decryptUpdate(buffer, read);
        System.arraycopy(expected, 0, buffer, 0, expected.length);
        builder.decryptUpdate(buffer, 20);
        validate(builder);
    }

    @Test
    void testDecryptionWhereDataWrapsAroundBuffer() throws Exception {
        int buffersize = 64;
        // CipherInputStream hands out the decrypted data in chunks, so the buffer has to wrap around for any input
        // larger than the buffer
        byte[] data = new byte[1000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31 + 7);
        }
        payload = data;
        createExpectedMac();
        byte[] input = Arrays.copyOf(payload, payload.length + expected.length);
        System.arraycopy(expected, 0, input, payload.length, expected.length);

        HMACAccumulator builder = new HMACAccumulator(key, "HmacSHA1", null, buffersize);
        ByteArrayOutputStream plaintext = new ByteArrayOutputStream();
        builder.attachStream(plaintext);
        byte[] buffer = new byte[buffersize];
        for (int pos = 0; pos < input.length; pos += 24) {
            int read = Math.min(24, input.length - pos);
            System.arraycopy(input, pos, buffer, 0, read);
            builder.decryptUpdate(buffer, read);
        }
        validate(builder);
        assertArrayEquals(payload, plaintext.toByteArray());
    }

    private void validate(HMACAccumulator builder) {
        assertMacs(builder.getCalculatedMac(), builder.getCalculatedMac());
        assertMacs(builder.getAppendedMac(), builder.getAppendedMac());
        assertMacs(expected, builder.getCalculatedMac());
        assertMacs(expected, builder.getAppendedMac());
        builder.validate();
    }

    private void assertMacs(byte[] expected, byte[] actual) {
        assertEquals(HexUtils.byteArrayToHexString(expected), HexUtils.byteArrayToHexString(actual));
    }

    @Test
    void testBufferAdd() {
        CircularBuffer buffer = new CircularBuffer(payload.length * 2);
        buffer.write(payload, 0, payload.length);
        assertEquals(payload.length, buffer.availableForWrite());
        buffer.write(payload, 0, payload.length);
        assertEquals(0, buffer.availableForWrite());
        // a write that does not fit is not silently dropped
        assertThrows(IllegalStateException.class, () -> buffer.write(payload, 0, payload.length));
        assertEquals(0, buffer.availableForWrite());
    }

    @Test
    void testBufferDrain() {
        CircularBuffer buffer = new CircularBuffer(payload.length * 2);
        buffer.write(payload, 0, payload.length);

        byte[] data = new byte[payload.length >> 1];
        assertEquals(data.length, buffer.read(data, 0, data.length));
        assertEquals(data.length, buffer.read(data, 0, data.length));
        assertEquals(0, buffer.read(data, 0, data.length));
    }

    @Test
    void testBufferWriteWrapsAround() {
        CircularBuffer buffer = new CircularBuffer(5);
        byte[] data = new byte[3];
        buffer.write(new byte[] { 1, 2, 3 }, 0, 3);
        assertEquals(3, buffer.read(data, 0, 3));

        // 3 bytes from position 3 of a 5 byte buffer: 2 go to the end of the array, 1 to its start
        buffer.write(new byte[] { 0, 4, 5, 6 }, 1, 3);
        assertEquals(2, buffer.availableForWrite());
        assertEquals(3, buffer.read(data, 0, 3));
        assertArrayEquals(new byte[] { 4, 5, 6 }, data);
    }

    @Test
    void testBufferReadBehindWritePosition() {
        CircularBuffer buffer = new CircularBuffer(5);
        byte[] data = new byte[2];
        buffer.write(new byte[] { 1, 2, 3 }, 0, 3);
        assertEquals(2, buffer.read(data, 0, 2));
        // fills the array up to its end, so the write position is back at 0 and the read position is behind it
        buffer.write(new byte[] { 4, 5 }, 0, 2);

        assertEquals(1, buffer.read(data, 0, 1));
        assertEquals(3, data[0]);
        assertEquals(2, buffer.read(data, 0, 2));
        assertArrayEquals(new byte[] { 4, 5 }, data);
    }

    private byte[] initializeBuffer(int buffersize) {
        byte[] buffer = new byte[buffersize];
        System.arraycopy(payload, 0, buffer, 0, payload.length);
        System.arraycopy(expected, 0, buffer, payload.length, expected.length);
        return buffer;
    }
}
