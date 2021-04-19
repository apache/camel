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

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.impl.KafkaHeaderImpl;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.vertx.kafka.serde.VertxKafkaHeaderSerializer;
import org.apache.camel.spi.HeaderFilterStrategy;

public final class VertxKafkaHeadersPropagation {

    private final HeaderFilterStrategy headerFilterStrategy;

    public VertxKafkaHeadersPropagation(final HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public List<KafkaHeader> getPropagatedHeaders(final Message message) {
        return message.getHeaders().entrySet().stream()
                .filter(entry -> shouldBeFiltered(entry, message.getExchange(), headerFilterStrategy))
                .map(VertxKafkaHeadersPropagation::getRecordHeader)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Map<String, Buffer> getPropagatedHeaders(
            final List<KafkaHeader> headers, final Message message) {
        return headers
                .stream()
                .filter(entry -> shouldBeFiltered(new AbstractMap.SimpleEntry<>(entry.key(), entry.value()),
                        message.getExchange(), headerFilterStrategy))
                .collect(Collectors.toMap(KafkaHeader::key, KafkaHeader::value));
    }

    private static boolean shouldBeFiltered(
            Map.Entry<String, Object> entry, Exchange exchange, HeaderFilterStrategy headerFilterStrategy) {
        return !headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange);
    }

    private static KafkaHeader getRecordHeader(final Map.Entry<String, Object> entry) {
        final Buffer headerValue = VertxKafkaHeaderSerializer.serialize(entry.getValue());

        if (headerValue == null) {
            return null;
        }

        return new KafkaHeaderImpl(entry.getKey(), headerValue);
    }
}
