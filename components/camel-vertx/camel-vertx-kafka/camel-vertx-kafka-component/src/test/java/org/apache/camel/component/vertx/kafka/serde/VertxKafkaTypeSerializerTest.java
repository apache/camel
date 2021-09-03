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

class VertxKafkaTypeSerializerTest extends CamelTestSupport {

    @Test
    void testNormalTypes() {
        final Exchange exchange = new DefaultExchange(context);
        final Message message = exchange.getIn();

        final Object convertedStringValue
                = VertxKafkaTypeSerializer.tryConvertToSerializedType(message, 12, StringSerializer.class.getName());

        assertEquals("12", convertedStringValue);

        final Object convertedByteArr
                = VertxKafkaTypeSerializer.tryConvertToSerializedType(message, "test", ByteArraySerializer.class.getName());

        assertTrue(convertedByteArr instanceof byte[]);

        final Object convertedByteBuffer
                = VertxKafkaTypeSerializer.tryConvertToSerializedType(message, "test", ByteBufferSerializer.class.getName());

        assertTrue(convertedByteBuffer instanceof ByteBuffer);

        final Object convertedBytes
                = VertxKafkaTypeSerializer.tryConvertToSerializedType(message, "test", BytesSerializer.class.getName());

        assertTrue(convertedBytes instanceof Bytes);

        final Object convertedFallback
                = VertxKafkaTypeSerializer.tryConvertToSerializedType(message, "test", "dummy");

        assertEquals("test", convertedFallback);

        assertNull(VertxKafkaTypeSerializer.tryConvertToSerializedType(message, null, "dummy"));

        assertNull(VertxKafkaTypeSerializer.tryConvertToSerializedType(message, null, null));

        assertEquals("test", VertxKafkaTypeSerializer.tryConvertToSerializedType(message, "test", null));
    }
}
