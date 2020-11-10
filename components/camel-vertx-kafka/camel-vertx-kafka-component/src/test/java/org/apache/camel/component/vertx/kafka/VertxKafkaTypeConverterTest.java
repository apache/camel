package org.apache.camel.component.vertx.kafka;

import java.nio.ByteBuffer;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.ByteBufferSerializer;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxKafkaTypeConverterTest extends CamelTestSupport {

    @Test
    void testNormalTypes() {
        final Exchange exchange = new DefaultExchange(context);

        final Object convertedStringValue
                = VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, 12, StringSerializer.class.getName());

        assertEquals("12", convertedStringValue);

        final Object convertedByteArr
                = VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, "test", ByteArraySerializer.class.getName());

        assertTrue(convertedByteArr instanceof byte[]);

        final Object convertedByteBuffer
                = VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, "test", ByteBufferSerializer.class.getName());

        assertTrue(convertedByteBuffer instanceof ByteBuffer);

        final Object convertedBytes
                = VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, "test", BytesSerializer.class.getName());

        assertTrue(convertedBytes instanceof Bytes);

        final Object convertedFallback
                = VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, "test", "dummy");

        assertEquals("test", convertedFallback);

        assertNull(VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, null, "dummy"));

        assertNull(VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, null, null));

        assertEquals("test", VertxKafkaTypeConverter.tryConvertToSerializedType(exchange, "test", null));
    }
}
