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
package org.apache.camel.component.vertx.kafka;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaHeader;
import org.apache.camel.Message;
import org.apache.camel.component.vertx.kafka.serde.VertxKafkaHeaderSerializer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxKafkaHeadersPropagationTest extends CamelTestSupport {

    @Test
    void testGetPropagatedHeadersFromCamelMessage() {
        String propagatedStringHeaderKey = "PROPAGATED_STRING_HEADER";
        String propagatedStringHeaderValue = "propagated string header value";

        String propagatedIntegerHeaderKey = "PROPAGATED_INTEGER_HEADER";
        Integer propagatedIntegerHeaderValue = 54545;

        String propagatedLongHeaderKey = "PROPAGATED_LONG_HEADER";
        Long propagatedLongHeaderValue = 5454545454545L;

        String propagatedDoubleHeaderKey = "PROPAGATED_DOUBLE_HEADER";
        Double propagatedDoubleHeaderValue = 43434.545D;

        String propagatedBytesHeaderKey = "PROPAGATED_BYTES_HEADER";
        byte[] propagatedBytesHeaderValue = new byte[] { 121, 34, 34, 54, 5, 3, 54, -34 };

        String propagatedBooleanHeaderKey = "PROPAGATED_BOOLEAN_HEADER";

        Map<String, Object> camelHeaders = new HashMap<>();

        camelHeaders.put(propagatedStringHeaderKey, propagatedStringHeaderValue);
        camelHeaders.put(propagatedIntegerHeaderKey, propagatedIntegerHeaderValue);
        camelHeaders.put(propagatedLongHeaderKey, propagatedLongHeaderValue);
        camelHeaders.put(propagatedDoubleHeaderKey, propagatedDoubleHeaderValue);
        camelHeaders.put(propagatedBytesHeaderKey, propagatedBytesHeaderValue);
        camelHeaders.put(propagatedBooleanHeaderKey, true);

        camelHeaders.put("CustomObjectHeader", new Object());
        camelHeaders.put("CustomNullObjectHeader", null);
        camelHeaders.put("CamelFilteredHeader", "CamelFilteredHeader value");

        final Message message = new DefaultExchange(context).getMessage();

        message.setBody("test body");
        message.setHeaders(camelHeaders);

        final List<KafkaHeader> kafkaHeaders
                = new VertxKafkaHeadersPropagation(new VertxKafkaHeaderFilterStrategy()).getPropagatedHeaders(message);

        assertNotNull(kafkaHeaders, "Kafka Headers should not be null.");
        // we have 6 headers
        assertEquals(6, kafkaHeaders.size(), "6 propagated header is expected.");

        final Map<String, KafkaHeader> headers = convertToMap(kafkaHeaders);

        assertEquals(propagatedStringHeaderValue, new String(getHeaderValue(propagatedStringHeaderKey, headers)),
                "Propagated string value received");
        assertEquals(propagatedIntegerHeaderValue,
                Integer.valueOf(ByteBuffer.wrap(getHeaderValue(propagatedIntegerHeaderKey, headers)).getInt()),
                "Propagated integer value received");
        assertEquals(propagatedLongHeaderValue,
                Long.valueOf(ByteBuffer.wrap(getHeaderValue(propagatedLongHeaderKey, headers)).getLong()),
                "Propagated long value received");
        assertEquals(propagatedDoubleHeaderValue,
                Double.valueOf(ByteBuffer.wrap(getHeaderValue(propagatedDoubleHeaderKey, headers)).getDouble()),
                "Propagated double value received");
        assertArrayEquals(propagatedBytesHeaderValue, getHeaderValue(propagatedBytesHeaderKey, headers),
                "Propagated byte array value received");
        assertEquals(true,
                Boolean.valueOf(new String(getHeaderValue(propagatedBooleanHeaderKey, headers))),
                "Propagated boolean value received");
    }

    @Test
    void testGetPropagatedHeadersFromKafkaHeaders() {
        String propagatedHeaderKey = VertxKafkaConstants.TOPIC;
        byte[] propagatedHeaderValue = "propagated incorrect topic".getBytes();

        String propagatedStringHeaderKey = "PROPAGATED_STRING_HEADER";
        String propagatedStringHeaderValue = "propagated string header value";

        String propagatedIntegerHeaderKey = "PROPAGATED_INTEGER_HEADER";
        Integer propagatedIntegerHeaderValue = 54545;

        String propagatedLongHeaderKey = "PROPAGATED_LONG_HEADER";
        Long propagatedLongHeaderValue = 5454545454545L;

        final List<KafkaHeader> kafkaHeaders = new LinkedList<>();
        kafkaHeaders.add(convertToKafkaHeader(propagatedHeaderKey, propagatedHeaderValue));
        kafkaHeaders.add(convertToKafkaHeader(propagatedStringHeaderKey, propagatedStringHeaderValue));
        kafkaHeaders.add(convertToKafkaHeader(propagatedIntegerHeaderKey, propagatedIntegerHeaderValue));
        kafkaHeaders.add(convertToKafkaHeader(propagatedLongHeaderKey, propagatedLongHeaderValue));

        final Map<String, Buffer> propagatedHeaders
                = new VertxKafkaHeadersPropagation(new VertxKafkaHeaderFilterStrategy()).getPropagatedHeaders(kafkaHeaders,
                        new DefaultExchange(context).getMessage());

        // 3 since one is skipped due to the camel prefix
        assertEquals(3, propagatedHeaders.size());
        assertEquals("propagated string header value", propagatedHeaders.get("PROPAGATED_STRING_HEADER").toString());
        assertEquals(54545, propagatedHeaders.get("PROPAGATED_INTEGER_HEADER").getInt(0));
        assertEquals(5454545454545L, propagatedHeaders.get("PROPAGATED_LONG_HEADER").getLong(0));
    }

    private Map<String, KafkaHeader> convertToMap(final List<KafkaHeader> headersAsList) {
        return headersAsList
                .stream()
                .collect(Collectors.toMap(KafkaHeader::key, header -> header));
    }

    @Test
    void testGetPropagatedHeadersFromKafkaHeadersWithCustomStrategy() {
        String propagatedHeaderKey = VertxKafkaConstants.TOPIC;
        byte[] propagatedHeaderValue = "propagated incorrect topic".getBytes();

        String propagatedStringHeaderKey = "TEST_PROPAGATED_STRING_HEADER";
        String propagatedStringHeaderValue = "propagated string header value";

        String propagatedIntegerHeaderKey = "TEST_PROPAGATED_INTEGER_HEADER";
        Integer propagatedIntegerHeaderValue = 54545;

        String propagatedLongHeaderKey = "PROPAGATED_LONG_HEADER";
        Long propagatedLongHeaderValue = 5454545454545L;

        final List<KafkaHeader> kafkaHeaders = new LinkedList<>();
        kafkaHeaders.add(convertToKafkaHeader(propagatedHeaderKey, propagatedHeaderValue));
        kafkaHeaders.add(convertToKafkaHeader(propagatedStringHeaderKey, propagatedStringHeaderValue));
        kafkaHeaders.add(convertToKafkaHeader(propagatedIntegerHeaderKey, propagatedIntegerHeaderValue));
        kafkaHeaders.add(convertToKafkaHeader(propagatedLongHeaderKey, propagatedLongHeaderValue));

        final Map<String, Buffer> propagatedHeaders
                = new VertxKafkaHeadersPropagation(new VertxKafkaTestHeaderFilterStrategy()).getPropagatedHeaders(kafkaHeaders,
                        new DefaultExchange(context).getMessage());

        assertEquals(2, propagatedHeaders.size());
        assertTrue(propagatedHeaders.containsKey(VertxKafkaConstants.TOPIC));
        assertTrue(propagatedHeaders.containsKey("PROPAGATED_LONG_HEADER"));
    }

    private byte[] getHeaderValue(String headerKey, Map<String, KafkaHeader> headers) {
        return headers.get(headerKey).value().getBytes();
    }

    public KafkaHeader convertToKafkaHeader(final String key, final Object value) {
        return KafkaHeader.header(key, VertxKafkaHeaderSerializer.serialize(value));
    }
}
