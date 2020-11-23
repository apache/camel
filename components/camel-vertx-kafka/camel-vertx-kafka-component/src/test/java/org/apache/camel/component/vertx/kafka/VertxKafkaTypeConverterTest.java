package org.apache.camel.component.vertx.kafka;

import java.nio.ByteBuffer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
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
        final Message message = exchange.getIn();

        final Object convertedStringValue
                = VertxKafkaTypeConverter.tryConvertToSerializedType(message, 12, StringSerializer.class.getName());

        assertEquals("12", convertedStringValue);

        final Object convertedByteArr
                = VertxKafkaTypeConverter.tryConvertToSerializedType(message, "test", ByteArraySerializer.class.getName());

        assertTrue(convertedByteArr instanceof byte[]);

        final Object convertedByteBuffer
                = VertxKafkaTypeConverter.tryConvertToSerializedType(message, "test", ByteBufferSerializer.class.getName());

        assertTrue(convertedByteBuffer instanceof ByteBuffer);

        final Object convertedBytes
                = VertxKafkaTypeConverter.tryConvertToSerializedType(message, "test", BytesSerializer.class.getName());

        assertTrue(convertedBytes instanceof Bytes);

        final Object convertedFallback
                = VertxKafkaTypeConverter.tryConvertToSerializedType(message, "test", "dummy");

        assertEquals("test", convertedFallback);

        assertNull(VertxKafkaTypeConverter.tryConvertToSerializedType(message, null, "dummy"));

        assertNull(VertxKafkaTypeConverter.tryConvertToSerializedType(message, null, null));

        assertEquals("test", VertxKafkaTypeConverter.tryConvertToSerializedType(message, "test", null));
    }
}
