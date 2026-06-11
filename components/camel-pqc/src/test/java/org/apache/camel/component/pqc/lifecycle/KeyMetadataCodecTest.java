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
package org.apache.camel.component.pqc.lifecycle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyMetadataCodecTest {

    @Test
    void jsonRoundTripPreservesAllFields() throws Exception {
        KeyMetadata original = new KeyMetadata("key-1", "DILITHIUM", Instant.parse("2026-01-01T00:00:00Z"));
        original.setLastUsedAt(Instant.parse("2026-02-01T10:15:30Z"));
        original.setExpiresAt(Instant.parse("2027-01-01T00:00:00Z"));
        original.setNextRotationAt(Instant.parse("2026-06-01T00:00:00Z"));
        original.setUsageCount(42);
        original.setStatus(KeyMetadata.KeyStatus.DEPRECATED);
        original.setDescription("rotated");

        String json = KeyMetadataCodec.toJson(original);
        assertTrue(KeyMetadataCodec.isJson(json));

        KeyMetadata restored = KeyMetadataCodec.fromJson(json);
        assertEquals(original.getKeyId(), restored.getKeyId());
        assertEquals(original.getAlgorithm(), restored.getAlgorithm());
        assertEquals(original.getCreatedAt(), restored.getCreatedAt());
        assertEquals(original.getLastUsedAt(), restored.getLastUsedAt());
        assertEquals(original.getExpiresAt(), restored.getExpiresAt());
        assertEquals(original.getNextRotationAt(), restored.getNextRotationAt());
        assertEquals(original.getUsageCount(), restored.getUsageCount());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getDescription(), restored.getDescription());
    }

    @Test
    void jsonRoundTripWithMinimalMetadata() throws Exception {
        KeyMetadata original = new KeyMetadata("key-2", "FALCON");

        KeyMetadata restored = KeyMetadataCodec.fromJson(KeyMetadataCodec.toJson(original));
        assertEquals("key-2", restored.getKeyId());
        assertEquals("FALCON", restored.getAlgorithm());
        assertEquals(KeyMetadata.KeyStatus.ACTIVE, restored.getStatus());
        assertEquals(0, restored.getUsageCount());
    }

    @Test
    void isJsonDistinguishesJsonFromLegacyBase64() {
        assertTrue(KeyMetadataCodec.isJson("{\"keyId\":\"x\"}"));
        assertTrue(KeyMetadataCodec.isJson("   \n {\"keyId\":\"x\"}"));
        // Base64 of a Java-serialized object never starts with '{'
        assertFalse(KeyMetadataCodec.isJson("rO0ABXNyAB1vcmcuYXBhY2hl"));
        assertFalse(KeyMetadataCodec.isJson(null));
        assertFalse(KeyMetadataCodec.isJson(""));
    }

    @Test
    void metadataFilterAllowsLegacySerializedKeyMetadata() throws Exception {
        KeyMetadata original = new KeyMetadata("legacy", "DILITHIUM", Instant.parse("2026-03-03T03:03:03Z"));
        original.setExpiresAt(Instant.parse("2027-03-03T03:03:03Z"));
        original.setStatus(KeyMetadata.KeyStatus.REVOKED);
        original.setUsageCount(7);

        byte[] serialized = javaSerialize(original);

        KeyMetadata restored;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            ois.setObjectInputFilter(KeyMetadataCodec.METADATA_FILTER);
            restored = (KeyMetadata) ois.readObject();
        }

        assertEquals("legacy", restored.getKeyId());
        assertEquals(KeyMetadata.KeyStatus.REVOKED, restored.getStatus());
        assertEquals(original.getExpiresAt(), restored.getExpiresAt());
        assertEquals(7, restored.getUsageCount());
    }

    @Test
    void metadataFilterRejectsUnexpectedType() throws Exception {
        ArrayList<String> notMetadata = new ArrayList<>();
        notMetadata.add("payload");
        byte[] serialized = javaSerialize(notMetadata);

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            ois.setObjectInputFilter(KeyMetadataCodec.METADATA_FILTER);
            assertThrows(InvalidClassException.class, ois::readObject);
        }
    }

    private static byte[] javaSerialize(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(o);
        }
        return baos.toByteArray();
    }
}
