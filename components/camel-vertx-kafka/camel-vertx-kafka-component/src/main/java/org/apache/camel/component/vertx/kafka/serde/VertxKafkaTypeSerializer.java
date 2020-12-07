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
package org.apache.camel.component.vertx.kafka.serde;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.common.utils.Bytes;

public final class VertxKafkaTypeSerializer {

    private static final Map<String, Class<?>> TYPE_TO_CLASS = new HashMap<>();

    static {
        bind("org.apache.kafka.common.serialization.StringSerializer", String.class);
        bind("org.apache.kafka.common.serialization.ByteArraySerializer", byte[].class);
        bind("org.apache.kafka.common.serialization.ByteBufferSerializer", ByteBuffer.class);
    }

    private VertxKafkaTypeSerializer() {
    }

    public static Object tryConvertToSerializedType(
            final Message message, final Object inputValue, final String valueSerializer) {
        ObjectHelper.notNull(message, "message");

        if (inputValue == null) {
            return null;
        }

        // Special case for BytesSerializer
        if ("org.apache.kafka.common.serialization.BytesSerializer".equals(valueSerializer)) {
            final byte[] valueAsByteArr = convertValue(message.getExchange(), inputValue, byte[].class);
            if (valueAsByteArr != null) {
                return new Bytes(valueAsByteArr);
            }
        }

        final Object convertedValue = convertValue(message.getExchange(), inputValue, TYPE_TO_CLASS.get(valueSerializer));

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
        TYPE_TO_CLASS.put(type, typeClass);
    }
}
