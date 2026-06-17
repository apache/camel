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
package org.apache.camel.component.leveldb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.util.HashMap;

import org.apache.camel.component.leveldb.serializer.DefaultLevelDBSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link DefaultLevelDBSerializer#deserializeKey(byte[])} applies an ObjectInputFilter: legitimate String
 * keys still round-trip, while a crafted non-String object graph is rejected (defense-in-depth for the aggregation
 * repository key path).
 */
public class DefaultLevelDBSerializerKeyFilterTest {

    private final DefaultLevelDBSerializer serializer = new DefaultLevelDBSerializer();

    @Test
    public void testStringKeyRoundTrip() throws IOException {
        String key = "my-correlation-key-123";
        byte[] bytes = serializer.serializeKey(key);
        assertEquals(key, serializer.deserializeKey(bytes), "A String key must still deserialize correctly");
    }

    @Test
    public void testNonStringKeyIsRejectedByFilter() throws IOException {
        // A malicious actor could place a non-String (object-graph) payload where a key is expected.
        byte[] payload;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            HashMap<String, String> notAKey = new HashMap<>();
            notAKey.put("k", "v");
            oos.writeObject(notAKey);
            payload = baos.toByteArray();
        }

        // The ObjectInputFilter must reject any class other than java.lang.String.
        assertThrows(InvalidClassException.class, () -> serializer.deserializeKey(payload),
                "A non-String key payload must be rejected by the deserialization filter");
    }
}
