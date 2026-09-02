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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

import org.apache.camel.RuntimeCamelException;

/**
 * Shared serialization utilities for {@link org.apache.camel.spi.KeyValueRepository} implementations.
 * <p/>
 * All persistent {@code KeyValueRepository} implementations need to serialize arbitrary Java objects to bytes (for BLOB
 * columns, Kafka messages, etc.) and deserialize them back. This helper centralises that logic to avoid the same
 * try/catch boilerplate in every implementation.
 * <p/>
 * <b>Security note:</b> These methods use plain Java serialization
 * ({@link ObjectOutputStream}/{@link ObjectInputStream}). The stored data is trusted — it was written by the same
 * application instance or cluster. Do not expose a repository's raw byte store to untrusted input.
 *
 * @since 4.23
 */
public final class KeyValueRepositoryHelper {

    private KeyValueRepositoryHelper() {
        // utility class
    }

    /**
     * Serializes an object to a byte array using Java object serialization.
     *
     * @param  value                 the object to serialize (must be {@link java.io.Serializable})
     * @return                       the serialized bytes
     * @throws RuntimeCamelException if serialization fails
     */
    public static byte[] serialize(Object value) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to serialize value", e);
        }
    }

    /**
     * Serializes an object to a {@link ByteBuffer} using Java object serialization. Useful for drivers that work with
     * {@code ByteBuffer} (e.g. Cassandra).
     *
     * @param  value                 the object to serialize (must be {@link java.io.Serializable})
     * @return                       a ByteBuffer wrapping the serialized bytes
     * @throws RuntimeCamelException if serialization fails
     */
    public static ByteBuffer serializeToByteBuffer(Object value) {
        return ByteBuffer.wrap(serialize(value));
    }

    /**
     * Deserializes a byte array back into an object using Java object serialization.
     *
     * @param  bytes                 the bytes to deserialize
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails
     */
    public static Object deserialize(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeCamelException("Failed to deserialize value", e);
        }
    }

    /**
     * Deserializes an object from a portion of a byte array using Java object serialization. Useful when the serialized
     * data starts at an offset (e.g. after a protocol header).
     *
     * @param  bytes                 the byte array containing the serialized data
     * @param  offset                the start offset within the array
     * @param  length                the number of bytes to read
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails
     */
    public static Object deserialize(byte[] bytes, int offset, int length) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes, offset, length);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeCamelException("Failed to deserialize value", e);
        }
    }

    /**
     * Deserializes an object from a {@link ByteBuffer} using Java object serialization. The buffer's remaining bytes
     * are consumed.
     *
     * @param  buffer                the ByteBuffer containing the serialized bytes
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails
     */
    public static Object deserialize(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return deserialize(bytes);
    }
}
