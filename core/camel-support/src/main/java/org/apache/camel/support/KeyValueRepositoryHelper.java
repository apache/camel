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
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.time.Duration;

import org.apache.camel.RuntimeCamelException;

/**
 * Shared serialization and expiry utilities for {@link org.apache.camel.spi.KeyValueRepository} implementations.
 * <p>
 * All persistent {@code KeyValueRepository} implementations need to serialize arbitrary Java objects to bytes (for BLOB
 * columns, Kafka messages, etc.) and deserialize them back. This helper centralises that logic to avoid the same
 * try/catch boilerplate in every implementation.
 * <p>
 * <b>Security note:</b> These methods use plain Java serialization
 * ({@link ObjectOutputStream}/{@link ObjectInputStream}). The byte store backing a repository is shared infrastructure
 * (a Redis instance, a database table, a Kafka topic, a data grid) and is not necessarily writable only by the
 * application that owns it. Every {@code deserialize} method therefore installs a JEP-290 {@link ObjectInputFilter}
 * resolved by {@link DeserializationFilterHelper}, in the same way the aggregation repositories do. The default filter
 * only allows standard Java and Apache Camel types, so applications that store their own classes must widen it through
 * the {@code deserializationFilter} option of the repository they use.
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
     * Deserializes a byte array back into an object using Java object serialization, applying the default
     * deserialization filter.
     *
     * @param  bytes                 the bytes to deserialize
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails or the filter rejects the stream
     */
    public static Object deserialize(byte[] bytes) {
        return deserialize(bytes, null);
    }

    /**
     * Deserializes a byte array back into an object using Java object serialization.
     *
     * @param  bytes                 the bytes to deserialize
     * @param  deserializationFilter the filter pattern to apply, or null to use the JVM-wide filter or the Camel
     *                               default
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails or the filter rejects the stream
     */
    public static Object deserialize(byte[] bytes, String deserializationFilter) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            return doDeserialize(bis, deserializationFilter);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeCamelException("Failed to deserialize value", e);
        }
    }

    /**
     * Deserializes an object from a portion of a byte array using Java object serialization, applying the default
     * deserialization filter. Useful when the serialized data starts at an offset (e.g. after a protocol header).
     *
     * @param  bytes                 the byte array containing the serialized data
     * @param  offset                the start offset within the array
     * @param  length                the number of bytes to read
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails or the filter rejects the stream
     */
    public static Object deserialize(byte[] bytes, int offset, int length) {
        return deserialize(bytes, offset, length, null);
    }

    /**
     * Deserializes an object from a portion of a byte array using Java object serialization. Useful when the serialized
     * data starts at an offset (e.g. after a protocol header).
     *
     * @param  bytes                 the byte array containing the serialized data
     * @param  offset                the start offset within the array
     * @param  length                the number of bytes to read
     * @param  deserializationFilter the filter pattern to apply, or null to use the JVM-wide filter or the Camel
     *                               default
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails or the filter rejects the stream
     */
    public static Object deserialize(byte[] bytes, int offset, int length, String deserializationFilter) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes, offset, length)) {
            return doDeserialize(bis, deserializationFilter);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeCamelException("Failed to deserialize value", e);
        }
    }

    /**
     * Deserializes an object from a {@link ByteBuffer} using Java object serialization, applying the default
     * deserialization filter. The buffer's remaining bytes are consumed.
     *
     * @param  buffer                the ByteBuffer containing the serialized bytes
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails or the filter rejects the stream
     */
    public static Object deserialize(ByteBuffer buffer) {
        return deserialize(buffer, null);
    }

    /**
     * Deserializes an object from a {@link ByteBuffer} using Java object serialization. The buffer's remaining bytes
     * are consumed.
     *
     * @param  buffer                the ByteBuffer containing the serialized bytes
     * @param  deserializationFilter the filter pattern to apply, or null to use the JVM-wide filter or the Camel
     *                               default
     * @return                       the deserialized object
     * @throws RuntimeCamelException if deserialization fails or the filter rejects the stream
     */
    public static Object deserialize(ByteBuffer buffer, String deserializationFilter) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return deserialize(bytes, deserializationFilter);
    }

    /**
     * Returns the time, in milliseconds since the epoch, at which an entry stored now with the given time-to-live
     * expires. When that time is beyond {@link Long#MAX_VALUE} (a time-to-live of about {@link Long#MAX_VALUE}
     * milliseconds or more), {@link Long#MAX_VALUE} is returned, so the entry does not expire.
     * <p>
     * The caller handles a {@code null}, zero or negative time-to-live (no expiration) itself.
     *
     * @param  ttl a positive time-to-live
     * @return     the expiration time in milliseconds since the epoch, at most {@link Long#MAX_VALUE}
     * @since      4.24
     */
    public static long expiresAt(Duration ttl) {
        try {
            return Math.addExact(System.currentTimeMillis(), ttl.toMillis());
        } catch (ArithmeticException e) {
            // toMillis() or the sum overflows: the entry would expire after the end of the long time range
            return Long.MAX_VALUE;
        }
    }

    private static Object doDeserialize(ByteArrayInputStream bis, String deserializationFilter)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(bis)) {
            ois.setObjectInputFilter(DeserializationFilterHelper.resolveDeserializationFilter(deserializationFilter));
            return ois.readObject();
        }
    }
}
