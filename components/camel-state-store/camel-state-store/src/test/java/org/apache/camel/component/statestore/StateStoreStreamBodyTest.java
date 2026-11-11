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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateStoreStreamBodyTest extends CamelTestSupport {

    // not valid UTF-8, so it would not survive a conversion to text
    private static final byte[] BINARY = { 0, 1, 2, (byte) 0xC3, (byte) 0x28, (byte) 0xFF, (byte) 0xFE };

    // not ASCII, so a wrong charset shows
    private static final String TEXT = "caf\u00e9";

    // the backends of the stores whose first get fails
    private final FailingOnceBackend redeliveryBackend = new FailingOnceBackend();
    private final FailingOnceBackend failingBackend = new FailingOnceBackend();

    @Test
    void testPutStreamCacheStoresItsBytes() {
        // the route caches the stream, so the producer gets a stream cache
        put("direct:put", new ByteArrayInputStream(BINARY), "streamCache");

        assertStoredBytes("streamCache", BINARY);
    }

    @Test
    void testPutInputStreamStoresItsBytes() {
        // without stream caching the producer gets the InputStream itself
        put("direct:putNoStreamCaching", new ByteArrayInputStream(BINARY), "inputStream");

        assertStoredBytes("inputStream", BINARY);
    }

    @Test
    void testPutGivenStreamCacheStoresItsBytes() {
        put("direct:putNoStreamCaching", new InputStreamCache(BINARY), "givenStreamCache");

        assertStoredBytes("givenStreamCache", BINARY);
    }

    @Test
    void testPutPartlyReadStreamCacheStoresAllItsBytes() {
        InputStreamCache cache = new InputStreamCache(BINARY);
        // an earlier step read part of it, and a route without stream caching does not reset it
        cache.read();
        cache.read();

        put("direct:putNoStreamCaching", cache, "partlyRead");

        assertStoredBytes("partlyRead", BINARY);
    }

    @Test
    void testRedeliveryStoresAllTheBytes() {
        // the first get of the backend fails, and the error handler sends the message as it came to the producer
        // again, with the stream cache reset
        put("direct:putRedelivery", new ByteArrayInputStream(BINARY), "redelivered");

        Object value = redeliveryBackend.get("statestore:redeliveryStore:redelivered");
        assertThat(value).isInstanceOf(byte[].class);
        assertThat((byte[]) value).containsExactly(BINARY);
    }

    @Test
    void testFailedPutLeavesTheBytesAsTheBody() throws Exception {
        MockEndpoint failed = getMockEndpoint("mock:failed");
        failed.expectedMessageCount(1);

        // the first get of the backend fails, after the put has read the stream, which cannot be read again
        put("direct:putFailing", new ByteArrayInputStream(BINARY), "failed");

        failed.assertIsSatisfied();
        Object body = failed.getReceivedExchanges().get(0).getMessage().getBody();
        assertThat(body).isInstanceOf(byte[].class);
        assertThat((byte[]) body).containsExactly(BINARY);
    }

    @Test
    void testPutReaderStoresItsText() {
        // a Reader holds text, so the charset of the exchange plays no part
        template.requestBodyAndHeaders("direct:put", new StringReader(TEXT),
                Map.of(StateStoreConstants.KEY, "cachedReader", Exchange.CHARSET_NAME, "ISO-8859-1"));
        template.requestBodyAndHeaders("direct:putNoStreamCaching", new StringReader(TEXT),
                Map.of(StateStoreConstants.KEY, "plainReader", Exchange.CHARSET_NAME, "ISO-8859-1"));

        assertStoredText("cachedReader", TEXT);
        assertStoredText("plainReader", TEXT);
    }

    @Test
    void testPutReaderWithACharsetNameThatJavaDoesNotKnow() {
        template.requestBodyAndHeaders("direct:put", new StringReader(TEXT),
                Map.of(StateStoreConstants.KEY, "cachedReaderUnknownCharset", Exchange.CHARSET_NAME, "utf8mb4"));
        template.requestBodyAndHeaders("direct:putNoStreamCaching", new StringReader(TEXT),
                Map.of(StateStoreConstants.KEY, "plainReaderUnknownCharset", Exchange.CHARSET_NAME, "utf8mb4"));

        assertStoredText("cachedReaderUnknownCharset", TEXT);
        assertStoredText("plainReaderUnknownCharset", TEXT);
    }

    @Test
    void testPutIfAbsentReaderStoresItsText() {
        put("direct:putIfAbsentNoStreamCaching", new StringReader("hello world"), "putIfAbsentReader");

        assertStoredText("putIfAbsentReader", "hello world");
    }

    @Test
    void testPutIfAbsentStreamStoresItsBytes() {
        Object existing = template.requestBodyAndHeaders("direct:putIfAbsent", new ByteArrayInputStream(BINARY),
                Map.of(StateStoreConstants.KEY, "putIfAbsentStream"));
        assertThat(existing).isNull();

        assertStoredBytes("putIfAbsentStream", BINARY);
    }

    @Test
    void testPutGivesThePreviousBytes() {
        put("direct:put", new ByteArrayInputStream(BINARY), "previous");

        Object previous = template.requestBodyAndHeaders("direct:put", "next",
                Map.of(StateStoreConstants.KEY, "previous"));
        assertThat(previous).isInstanceOf(byte[].class);
        assertThat((byte[]) previous).containsExactly(BINARY);
    }

    @Test
    void testOtherBodiesAreStoredAsTheyAre() {
        byte[] bytes = { 1, 2, 3 };
        List<String> list = List.of("a", "b");
        Map<String, Object> map = Map.of("name", "camel");

        put("direct:put", bytes, "otherBytes");
        put("direct:put", list, "otherList");
        put("direct:put", map, "otherMap");
        put("direct:put", "text", "otherText");

        assertThat(get("otherBytes")).isSameAs(bytes);
        assertThat(get("otherList")).isSameAs(list);
        assertThat(get("otherMap")).isSameAs(map);
        assertThat(get("otherText")).isEqualTo("text");
    }

    private void put(String uri, Object body, String key) {
        template.requestBodyAndHeaders(uri, body, Map.of(StateStoreConstants.KEY, key));
    }

    private void assertStoredBytes(String key, byte[] expected) {
        // every get gives the whole content, not a stream that an earlier get has read
        for (int i = 0; i < 3; i++) {
            Object value = get(key);
            assertThat(value).isInstanceOf(byte[].class);
            assertThat((byte[]) value).containsExactly(expected);
        }
    }

    private void assertStoredText(String key, String expected) {
        assertThat(get(key)).isEqualTo(expected);
        // and as a route that gets it as text reads it
        assertThat(template.requestBodyAndHeaders("direct:getText", null, Map.of(StateStoreConstants.KEY, key)))
                .isEqualTo(expected);
    }

    private Object get(String key) {
        return template.requestBodyAndHeaders("direct:get", null, Map.of(StateStoreConstants.KEY, key));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                StateStoreEndpoint redelivery
                        = getContext().getEndpoint("state-store:redeliveryStore?operation=put", StateStoreEndpoint.class);
                redelivery.setBackend(redeliveryBackend);
                StateStoreEndpoint failing
                        = getContext().getEndpoint("state-store:failingStore?operation=put", StateStoreEndpoint.class);
                failing.setBackend(failingBackend);

                from("direct:put").streamCaching().to("state-store:myStore?operation=put");
                from("direct:putIfAbsent").streamCaching().to("state-store:myStore?operation=putIfAbsent");
                from("direct:putNoStreamCaching").noStreamCaching().to("state-store:myStore?operation=put");
                from("direct:putIfAbsentNoStreamCaching").noStreamCaching()
                        .to("state-store:myStore?operation=putIfAbsent");
                from("direct:putRedelivery").streamCaching()
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0))
                        .to(redelivery);
                from("direct:putFailing").noStreamCaching()
                        .errorHandler(deadLetterChannel("mock:failed"))
                        .to(failing);
                from("direct:get").to("state-store:myStore?operation=get");
                from("direct:getText").to("state-store:myStore?operation=get").convertBodyTo(String.class);
            }
        };
    }

    /**
     * A backend whose first get fails, as a remote backend may on a transient error.
     */
    private static final class FailingOnceBackend extends MemoryKeyValueRepository {

        private final AtomicBoolean failed = new AtomicBoolean();

        @Override
        public Object get(String key) {
            if (failed.compareAndSet(false, true)) {
                throw new IllegalStateException("The first get of the backend fails");
            }
            return super.get(key);
        }
    }
}
