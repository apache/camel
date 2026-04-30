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
package org.apache.camel.telemetry.propagation;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.util.CaseInsensitiveMap;

public final class CamelHeadersSpanContextPropagationExtractor implements SpanContextPropagationExtractor {
    private final Map<String, Object> map = new CaseInsensitiveMap();

    public CamelHeadersSpanContextPropagationExtractor(final Map<String, Object> map) {
        // Extract string and byte[] valued map entries.
        // Messaging transports (Kafka, AMQP, etc.) may deliver headers as byte arrays,
        // so we convert them to String for the W3C propagator to extract trace context.
        map.entrySet().stream().filter(e -> e.getValue() instanceof String || e.getValue() instanceof byte[]).forEach(e -> {
            if (e.getValue() instanceof byte[] bytes) {
                this.map.put(e.getKey(), new String(bytes, StandardCharsets.UTF_8));
            } else {
                this.map.put(e.getKey(), e.getValue());
            }
        });
    }

    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return map.entrySet().iterator();
    }

    @Override
    public Object get(String key) {
        return this.map.get(key);
    }

    @Override
    public Set<String> keys() {
        return map.keySet();
    }
}
