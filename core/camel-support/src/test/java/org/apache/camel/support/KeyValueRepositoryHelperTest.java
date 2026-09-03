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

import java.io.Serial;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.RuntimeCamelException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyValueRepositoryHelperTest {

    // -------------------------------------------------------------------------
    // serialize / deserialize roundtrip — scalar types
    // -------------------------------------------------------------------------

    @Test
    void testSerializeDeserializeString() {
        String original = "hello camel";

        byte[] bytes = KeyValueRepositoryHelper.serialize(original);
        Object result = KeyValueRepositoryHelper.deserialize(bytes);

        assertThat(result).isInstanceOf(String.class).isEqualTo(original);
    }

    @Test
    void testSerializeDeserializeInteger() {
        Integer original = 42;

        byte[] bytes = KeyValueRepositoryHelper.serialize(original);
        Object result = KeyValueRepositoryHelper.deserialize(bytes);

        assertThat(result).isInstanceOf(Integer.class).isEqualTo(original);
    }

    @Test
    void testSerializeDeserializeLong() {
        Long original = Long.MAX_VALUE;

        byte[] bytes = KeyValueRepositoryHelper.serialize(original);
        Object result = KeyValueRepositoryHelper.deserialize(bytes);

        assertThat(result).isInstanceOf(Long.class).isEqualTo(original);
    }

    @Test
    void testSerializeDeserializeBoolean() {
        byte[] bytes = KeyValueRepositoryHelper.serialize(Boolean.TRUE);
        Object result = KeyValueRepositoryHelper.deserialize(bytes);

        assertThat(result).isEqualTo(Boolean.TRUE);
    }

    @Test
    void testSerializeProduceNonEmptyByteArray() {
        byte[] bytes = KeyValueRepositoryHelper.serialize("test");

        assertThat(bytes).isNotNull().isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // serialize / deserialize roundtrip — complex Serializable object
    // -------------------------------------------------------------------------

    @Test
    void testSerializeDeserializeComplexObject() {
        ComplexPayload original = new ComplexPayload("order-99", 3, List.of("item-a", "item-b"));

        byte[] bytes = KeyValueRepositoryHelper.serialize(original);
        Object result = KeyValueRepositoryHelper.deserialize(bytes);

        assertThat(result).isInstanceOf(ComplexPayload.class).isEqualTo(original);
    }

    @Test
    void testSerializeDeserializeNestedMap() {
        Map<String, Integer> original = Map.of("alpha", 1, "beta", 2);

        byte[] bytes = KeyValueRepositoryHelper.serialize(original);
        Object result = KeyValueRepositoryHelper.deserialize(bytes);

        assertThat(result).isEqualTo(original);
    }

    // -------------------------------------------------------------------------
    // ByteBuffer serialize / deserialize roundtrip
    // -------------------------------------------------------------------------

    @Test
    void testSerializeToByteBufferAndDeserialize() {
        String original = "byteBuffer value";

        ByteBuffer buffer = KeyValueRepositoryHelper.serializeToByteBuffer(original);
        Object result = KeyValueRepositoryHelper.deserialize(buffer);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void testSerializeToByteBufferReturnsNonNull() {
        ByteBuffer buffer = KeyValueRepositoryHelper.serializeToByteBuffer(42);

        assertThat(buffer).isNotNull();
        assertThat(buffer.remaining()).isPositive();
    }

    @Test
    void testDeserializeByteBufferConsumesRemainingBytes() {
        Integer original = 123;
        ByteBuffer buffer = KeyValueRepositoryHelper.serializeToByteBuffer(original);

        // Position is at start; after deserialize the buffer should be fully consumed
        assertThat(buffer.position()).isZero();
        Object result = KeyValueRepositoryHelper.deserialize(buffer);

        assertThat(result).isEqualTo(original);
        assertThat(buffer.remaining()).isZero();
    }

    @Test
    void testSerializeToByteBufferRoundtripComplexObject() {
        ComplexPayload original = new ComplexPayload("shipment-7", 10, List.of("sku-x"));

        ByteBuffer buffer = KeyValueRepositoryHelper.serializeToByteBuffer(original);
        Object result = KeyValueRepositoryHelper.deserialize(buffer);

        assertThat(result).isInstanceOf(ComplexPayload.class).isEqualTo(original);
    }

    // -------------------------------------------------------------------------
    // deserialize(bytes, offset, length) variant
    // -------------------------------------------------------------------------

    @Test
    void testDeserializeWithOffsetAndLength() {
        String original = "offset-test";
        byte[] serialized = KeyValueRepositoryHelper.serialize(original);

        // Embed serialized bytes into a larger array with a 4-byte header prefix
        int headerSize = 4;
        byte[] wrapped = new byte[headerSize + serialized.length];
        System.arraycopy(serialized, 0, wrapped, headerSize, serialized.length);

        Object result = KeyValueRepositoryHelper.deserialize(wrapped, headerSize, serialized.length);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void testDeserializeWithOffsetZeroFullLength() {
        Integer original = 999;
        byte[] bytes = KeyValueRepositoryHelper.serialize(original);

        Object result = KeyValueRepositoryHelper.deserialize(bytes, 0, bytes.length);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void testDeserializeWithOffsetIgnoresTrailingBytes() {
        String original = "trimmed";
        byte[] serialized = KeyValueRepositoryHelper.serialize(original);

        // Append garbage trailing bytes — they must be ignored
        byte[] withTrail = new byte[serialized.length + 10];
        System.arraycopy(serialized, 0, withTrail, 0, serialized.length);

        Object result = KeyValueRepositoryHelper.deserialize(withTrail, 0, serialized.length);

        assertThat(result).isEqualTo(original);
    }

    // -------------------------------------------------------------------------
    // Error case — non-Serializable object throws RuntimeCamelException
    // -------------------------------------------------------------------------

    @Test
    void testSerializeNonSerializableThrowsRuntimeCamelException() {
        Object notSerializable = new NonSerializable();

        assertThatThrownBy(() -> KeyValueRepositoryHelper.serialize(notSerializable))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("Failed to serialize value");
    }

    @Test
    void testSerializeToByteBufferNonSerializableThrowsRuntimeCamelException() {
        Object notSerializable = new NonSerializable();

        assertThatThrownBy(() -> KeyValueRepositoryHelper.serializeToByteBuffer(notSerializable))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("Failed to serialize value");
    }

    @Test
    void testDeserializeCorruptBytesThrowsRuntimeCamelException() {
        byte[] corrupt = new byte[] { 0x00, 0x01, 0x02, 0x03 };

        assertThatThrownBy(() -> KeyValueRepositoryHelper.deserialize(corrupt))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("Failed to deserialize value");
    }

    @Test
    void testDeserializeOffsetCorruptBytesThrowsRuntimeCamelException() {
        byte[] corrupt = new byte[] { 0x00, 0x01, 0x02, 0x03 };

        assertThatThrownBy(() -> KeyValueRepositoryHelper.deserialize(corrupt, 0, corrupt.length))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("Failed to deserialize value");
    }

    @Test
    void testDeserializeByteBufferCorruptThrowsRuntimeCamelException() {
        ByteBuffer corrupt = ByteBuffer.wrap(new byte[] { 0x00, 0x01, 0x02, 0x03 });

        assertThatThrownBy(() -> KeyValueRepositoryHelper.deserialize(corrupt))
                .isInstanceOf(RuntimeCamelException.class)
                .hasMessageContaining("Failed to deserialize value");
    }

    // -------------------------------------------------------------------------
    // Null handling edge cases
    // -------------------------------------------------------------------------

    @Test
    void testSerializeNullRoundtrip() {
        byte[] bytes = KeyValueRepositoryHelper.serialize(null);

        assertThat(bytes).isNotNull().isNotEmpty();

        Object result = KeyValueRepositoryHelper.deserialize(bytes);
        assertThat(result).isNull();
    }

    @Test
    void testSerializeToByteBufferNullRoundtrip() {
        ByteBuffer buffer = KeyValueRepositoryHelper.serializeToByteBuffer(null);
        Object result = KeyValueRepositoryHelper.deserialize(buffer);

        assertThat(result).isNull();
    }

    @Test
    void testSerializeNullOffsetRoundtrip() {
        byte[] bytes = KeyValueRepositoryHelper.serialize(null);
        Object result = KeyValueRepositoryHelper.deserialize(bytes, 0, bytes.length);

        assertThat(result).isNull();
    }

    // -------------------------------------------------------------------------
    // Helper types
    // -------------------------------------------------------------------------

    /** A non-serializable type used to provoke serialization failures. */
    private static class NonSerializable {
        // intentionally does not implement Serializable
    }

    /** A complex Serializable value object used for roundtrip assertions. */
    private static final class ComplexPayload implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String id;
        private final int quantity;
        private final List<String> items;

        ComplexPayload(String id, int quantity, List<String> items) {
            this.id = id;
            this.quantity = quantity;
            this.items = List.copyOf(items);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ComplexPayload other)) {
                return false;
            }
            return quantity == other.quantity
                    && Objects.equals(id, other.id)
                    && Objects.equals(items, other.items);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, quantity, items);
        }

        @Override
        public String toString() {
            return "ComplexPayload{id='" + id + "', quantity=" + quantity + ", items=" + items + "}";
        }
    }
}
