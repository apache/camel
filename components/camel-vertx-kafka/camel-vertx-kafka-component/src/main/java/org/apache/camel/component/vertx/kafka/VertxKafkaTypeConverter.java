package org.apache.camel.component.vertx.kafka;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.common.utils.Bytes;

public final class VertxKafkaTypeConverter {

    private static final Map<String, Class<?>> typeToClass = new HashMap<>();

    static {
        bind("org.apache.kafka.common.serialization.StringSerializer", String.class);
        bind("org.apache.kafka.common.serialization.ByteArraySerializer", byte[].class);
        bind("org.apache.kafka.common.serialization.ByteBufferSerializer", ByteBuffer.class);
    }

    private VertxKafkaTypeConverter() {
    }

    public static Object tryConvertToSerializedType(
            final Exchange exchange, final Object inputValue, final String valueSerializer) {
        ObjectHelper.notNull(exchange, "exchange");

        if (inputValue == null) {
            return null;
        }

        // Special case for BytesSerializer
        if ("org.apache.kafka.common.serialization.BytesSerializer".equals(valueSerializer)) {
            final byte[] valueAsByteArr = convertValue(exchange, inputValue, byte[].class);
            if (valueAsByteArr != null) {
                return new Bytes(valueAsByteArr);
            }
        }

        final Object convertedValue = convertValue(exchange, inputValue, typeToClass.get(valueSerializer));

        if (ObjectHelper.isNotEmpty(convertedValue)) {
            return convertedValue;
        }

        // we couldn't convert the value, hence return the original value
        return inputValue;
    }

    private static <T> T convertValue(final Exchange exchange, final Object inputValue, final Class<T> type) {
        if (type == null) {
            return null;
        }
        return exchange.getContext().getTypeConverter().tryConvertTo(type, exchange, inputValue);
    }

    private static void bind(final String type, final Class<?> typeClass) {
        typeToClass.put(type, typeClass);
    }
}
