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
package org.apache.camel.component.statestore;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateStoreStreamBodyTest extends CamelTestSupport {

    // not valid UTF-8, so it would not survive a conversion to text
    private static final byte[] BINARY = { 0, 1, 2, (byte) 0xC3, (byte) 0x28, (byte) 0xFF, (byte) 0xFE };

    @Test
    void testPutStreamCacheStoresItsBytes() {
        // the route caches the stream, so the producer gets a stream cache
        template.requestBodyAndHeaders("direct:put", new ByteArrayInputStream(BINARY),
                Map.of(StateStoreConstants.KEY, "key1"));

        assertStoredBytes("key1", BINARY);
    }

    @Test
    void testPutInputStreamStoresItsBytes() {
        // without stream caching the producer gets the InputStream itself
        template.requestBodyAndHeaders("direct:putNoStreamCaching", new ByteArrayInputStream(BINARY),
                Map.of(StateStoreConstants.KEY, "key1"));

        assertStoredBytes("key1", BINARY);
    }

    @Test
    void testPutGivenStreamCacheStoresItsBytes() {
        template.requestBodyAndHeaders("direct:putNoStreamCaching", new InputStreamCache(BINARY),
                Map.of(StateStoreConstants.KEY, "key1"));

        assertStoredBytes("key1", BINARY);
    }

    @Test
    void testPutIfAbsentReaderStoresItsBytes() {
        template.requestBodyAndHeaders("direct:putIfAbsentNoStreamCaching", new StringReader("hello world"),
                Map.of(StateStoreConstants.KEY, "key1"));

        assertStoredBytes("key1", "hello world".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testPutReaderStoresItsTextInTheCharsetOfTheExchange() {
        byte[] latin1 = "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1);

        // a reader that the route caches, and one that it does not
        template.requestBodyAndHeaders("direct:put", new StringReader("caf\u00e9"),
                Map.of(StateStoreConstants.KEY, "key1", Exchange.CHARSET_NAME, "ISO-8859-1"));
        template.requestBodyAndHeaders("direct:putNoStreamCaching", new StringReader("caf\u00e9"),
                Map.of(StateStoreConstants.KEY, "key2", Exchange.CHARSET_NAME, "ISO-8859-1"));

        assertStoredBytes("key1", latin1);
        assertStoredBytes("key2", latin1);
    }

    @Test
    void testPutIfAbsentStreamStoresItsBytes() {
        Object existing = template.requestBodyAndHeaders("direct:putIfAbsent", new ByteArrayInputStream(BINARY),
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(existing).isNull();

        assertStoredBytes("key1", BINARY);
    }

    @Test
    void testPutGivesThePreviousBytes() {
        template.requestBodyAndHeaders("direct:put", new ByteArrayInputStream(BINARY),
                Map.of(StateStoreConstants.KEY, "key1"));

        Object previous = template.requestBodyAndHeaders("direct:put", "next",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(previous).isInstanceOf(byte[].class);
        assertThat((byte[]) previous).containsExactly(BINARY);
    }

    @Test
    void testOtherBodiesAreStoredAsTheyAre() {
        byte[] bytes = { 1, 2, 3 };
        List<String> list = List.of("a", "b");
        Map<String, Object> map = Map.of("name", "camel");

        template.requestBodyAndHeaders("direct:put", bytes, Map.of(StateStoreConstants.KEY, "bytes"));
        template.requestBodyAndHeaders("direct:put", list, Map.of(StateStoreConstants.KEY, "list"));
        template.requestBodyAndHeaders("direct:put", map, Map.of(StateStoreConstants.KEY, "map"));
        template.requestBodyAndHeaders("direct:put", "text", Map.of(StateStoreConstants.KEY, "text"));

        assertThat(get("bytes")).isSameAs(bytes);
        assertThat(get("list")).isSameAs(list);
        assertThat(get("map")).isSameAs(map);
        assertThat(get("text")).isEqualTo("text");
    }

    private void assertStoredBytes(String key, byte[] expected) {
        // every get gives the whole content, not a stream that an earlier get has read
        for (int i = 0; i < 3; i++) {
            Object value = get(key);
            assertThat(value).isInstanceOf(byte[].class);
            assertThat((byte[]) value).containsExactly(expected);
        }
    }

    private Object get(String key) {
        return template.requestBodyAndHeaders("direct:get", null, Map.of(StateStoreConstants.KEY, key));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put").streamCaching().to("state-store:myStore?operation=put");
                from("direct:putIfAbsent").streamCaching().to("state-store:myStore?operation=putIfAbsent");
                from("direct:putNoStreamCaching").noStreamCaching().to("state-store:myStore?operation=put");
                from("direct:putIfAbsentNoStreamCaching").noStreamCaching()
                        .to("state-store:myStore?operation=putIfAbsent");
                from("direct:get").to("state-store:myStore?operation=get");
            }
        };
    }
}
