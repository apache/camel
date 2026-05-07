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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelHeadersExtractAdapterTest {

    private Map<String, Object> map;

    @BeforeEach
    public void before() {
        map = new HashMap<>();
    }

    @Test
    public void noProperties() {
        SpanContextPropagationExtractor adapter = new CamelHeadersSpanContextPropagationExtractor(map);
        Iterator<Map.Entry<String, Object>> iterator = adapter.iterator();
        assertFalse(iterator.hasNext());
    }

    @Test
    public void oneProperty() {
        map.put("key", "value");
        SpanContextPropagationExtractor adapter = new CamelHeadersSpanContextPropagationExtractor(map);
        Iterator<Map.Entry<String, Object>> iterator = adapter.iterator();
        Map.Entry<String, Object> entry = iterator.next();
        assertEquals("key", entry.getKey());
        assertEquals("value", entry.getValue());
    }

    @Test
    public void propertyWithDash() {
        map.put("-key-1-", "value1");
        SpanContextPropagationExtractor adapter = new CamelHeadersSpanContextPropagationExtractor(map);
        Iterator<Map.Entry<String, Object>> iterator = adapter.iterator();
        Map.Entry<String, Object> entry = iterator.next();
        assertEquals("-key-1-", entry.getKey());
        assertEquals("value1", entry.getValue());
    }

    @Test
    public void keyWithDifferentCase() {
        map.put("key", "value");
        SpanContextPropagationExtractor adapter = new CamelHeadersSpanContextPropagationExtractor(map);
        assertEquals("value", adapter.get("KeY"));
    }

    @Test
    public void byteArrayProperty() {
        map.put("traceparent", "00-abc123-def456-01".getBytes(StandardCharsets.UTF_8));
        SpanContextPropagationExtractor adapter = new CamelHeadersSpanContextPropagationExtractor(map);
        assertEquals("00-abc123-def456-01", adapter.get("traceparent"));
    }

    @Test
    public void mixedStringAndByteArrayProperties() {
        map.put("traceparent", "00-abc123-def456-01".getBytes(StandardCharsets.UTF_8));
        map.put("custom-header", "custom-value");
        SpanContextPropagationExtractor adapter = new CamelHeadersSpanContextPropagationExtractor(map);
        assertEquals("00-abc123-def456-01", adapter.get("traceparent"));
        assertEquals("custom-value", adapter.get("custom-header"));
    }

    @Test
    public void nonStringNonByteArrayPropertyIsFiltered() {
        map.put("integer-header", 42);
        map.put("key", "value");
        SpanContextPropagationExtractor adapter = new CamelHeadersSpanContextPropagationExtractor(map);
        assertNull(adapter.get("integer-header"));
        assertEquals("value", adapter.get("key"));
        assertTrue(adapter.keys().contains("key"));
        assertFalse(adapter.keys().contains("integer-header"));
    }

    @Test
    public void byteArrayKeyWithDifferentCase() {
        map.put("traceparent", "00-abc123-def456-01".getBytes(StandardCharsets.UTF_8));
        SpanContextPropagationExtractor adapter = new CamelHeadersSpanContextPropagationExtractor(map);
        assertEquals("00-abc123-def456-01", adapter.get("TraceParent"));
    }
}
