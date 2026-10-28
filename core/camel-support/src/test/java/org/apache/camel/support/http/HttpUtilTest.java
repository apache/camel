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
package org.apache.camel.support.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.TypeConverterSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("deprecation")
class HttpUtilTest {

    private static final String FILTERED = "filtered";

    private static final TypeConverter CONVERTER = new TypeConverterSupport() {
        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
            assertThat(type).isEqualTo(String.class);
            if (value == null) {
                return null;
            }
            return type.cast(value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : value.toString());
        }
    };

    @Test
    void testGetCharsetFromContentTypeEmptyCharset() {
        // an empty charset parameter is no charset
        assertNull(HttpUtil.getCharsetFromContentType("text/plain; charset="));
        assertNull(HttpUtil.getCharsetFromContentType("text/plain; charset=\"\""));
        assertNull(HttpUtil.getCharsetFromContentType("text/plain; charset= "));
        assertNull(HttpUtil.getCharsetFromContentType("text/plain; charset=; format=flowed"));
    }

    @Test
    void applyHeaderValueGivesEachValueOfACollection() {
        Applied applied = applyHeaderValue(List.of("a", 2, "c"));

        assertThat(applied.values).containsExactly("a", "2", "c");
        assertThat(applied.single).isEqualTo("a");
        // each element is filtered by itself, as a String
        assertThat(applied.filtered).containsExactly("a", "2", "c");
    }

    @Test
    void applyHeaderValueGivesEachValueOfAnObjectArray() {
        Applied applied = applyHeaderValue(new String[] { "a", "b" });

        assertThat(applied.values).containsExactly("a", "b");
    }

    @Test
    void applyHeaderValueGivesTheOnlyValueOfACollectionAsSingleValue() {
        Applied applied = applyHeaderValue(List.of("a"));

        assertThat(applied.values).isNull();
        assertThat(applied.single).isEqualTo("a");
    }

    @Test
    void applyHeaderValueSkipsNullAndFilteredElements() {
        Applied applied = applyHeaderValue(Arrays.asList("a", null, FILTERED, "", "b"));

        // an empty element is a value
        assertThat(applied.values).containsExactly("a", "", "b");
    }

    @Test
    void applyHeaderValueGivesNoValueForACollectionWithoutValues() {
        for (Object value : new Object[] { List.of(), new Object[0], Arrays.asList(null, null), List.of(FILTERED) }) {
            Applied applied = applyHeaderValue(value);

            assertThat(applied.calls).isEqualTo(1);
            assertThat(applied.values).isNull();
            assertThat(applied.single).isNull();
        }
    }

    @Test
    void applyHeaderValueDoesNotSplitAString() {
        Applied applied = applyHeaderValue("a, b");

        assertThat(applied.values).isNull();
        assertThat(applied.single).isEqualTo("a, b");

        assertThat(applyHeaderValue("").single).isEmpty();
    }

    @Test
    void applyHeaderValueGivesAPrimitiveArrayAsSingleValue() {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        Applied applied = applyHeaderValue(bytes);

        assertThat(applied.values).isNull();
        assertThat(applied.single).isEqualTo("abc");
        // a single value is filtered as it is
        assertThat(applied.filtered).containsExactly(bytes);
    }

    @Test
    void applyHeaderValueGivesAMapAsSingleValue() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        Applied applied = applyHeaderValue(map);

        assertThat(applied.values).isNull();
        assertThat(applied.single).isEqualTo("{a=1, b=2}");
    }

    @Test
    void applyHeaderValueGivesAnIterableThatIsNotACollectionAsSingleValue() {
        // such as a Vert.x JsonObject or JsonArray, which iterate over their content
        Iterable<String> json = new Iterable<>() {
            @Override
            public Iterator<String> iterator() {
                return List.of("a", "b").iterator();
            }

            @Override
            public String toString() {
                return "[\"a\",\"b\"]";
            }
        };
        Applied applied = applyHeaderValue(json);

        assertThat(applied.values).isNull();
        assertThat(applied.single).isEqualTo("[\"a\",\"b\"]");
        assertThat(applied.filtered).containsExactly(json);
    }

    @Test
    void applyHeaderValueGivesNoValueForAFilteredOrNullValue() {
        Applied filtered = applyHeaderValue(FILTERED);
        assertThat(filtered.calls).isEqualTo(1);
        assertThat(filtered.values).isNull();
        assertThat(filtered.single).isNull();

        Applied none = applyHeaderValue(null);
        assertThat(none.calls).isEqualTo(1);
        assertThat(none.values).isNull();
        assertThat(none.single).isNull();
        assertThat(none.filtered).isEmpty();
    }

    private static Applied applyHeaderValue(Object value) {
        Applied applied = new Applied();
        HeaderFilterStrategy strategy = new HeaderFilterStrategy() {
            @Override
            public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
                assertThat(headerName).isEqualTo("X-Test");
                applied.filtered.add(headerValue);
                return FILTERED.equals(headerValue);
            }

            @Override
            public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
                throw new UnsupportedOperationException("the headers are copied from Camel to the HTTP message");
            }
        };
        HttpUtil.applyHeaderValue(strategy, null, value, CONVERTER, "X-Test", (values, single) -> {
            applied.calls++;
            applied.values = values;
            applied.single = single;
        });
        return applied;
    }

    private static final class Applied {
        private final List<Object> filtered = new ArrayList<>();
        private int calls;
        private List<String> values;
        private String single;
    }
}
