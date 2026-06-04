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
package org.apache.camel.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultMessageHeaderTest {

    private CamelContext camelContext;

    @BeforeEach
    protected void setUp() {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @Test
    public void testHasHeaders() {
        Message msg = new DefaultMessage(camelContext);
        assertFalse(msg.hasHeaders());

        msg.setHeader("foo", "cheese");
        assertTrue(msg.hasHeaders());
        assertEquals("cheese", msg.getHeader("foo"));
    }

    @Test
    public void testLookupCaseAgnostic() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertEquals("cheese", msg.getHeader("FOO"));
    }

    @Test
    public void testLookupCaseAgnosticAddHeader() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertEquals("cheese", msg.getHeader("FOO"));
        assertNull(msg.getHeader("unknown"));

        msg.setHeader("bar", "beer");

        assertEquals("beer", msg.getHeader("bar"));
        assertEquals("beer", msg.getHeader("Bar"));
        assertEquals("beer", msg.getHeader("BAR"));
        assertNull(msg.getHeader("unknown"));
    }

    @Test
    public void testLookupCaseAgnosticAddHeader2() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("FOO"));
        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertNull(msg.getHeader("unknown"));

        msg.setHeader("bar", "beer");

        assertEquals("beer", msg.getHeader("BAR"));
        assertEquals("beer", msg.getHeader("bar"));
        assertEquals("beer", msg.getHeader("Bar"));
        assertNull(msg.getHeader("unknown"));
    }

    @Test
    public void testLookupCaseAgnosticAddHeaderRemoveHeader() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("Foo"));
        assertEquals("cheese", msg.getHeader("FOO"));
        assertNull(msg.getHeader("unknown"));

        msg.setHeader("bar", "beer");

        assertEquals("beer", msg.getHeader("bar"));
        assertEquals("beer", msg.getHeader("Bar"));
        assertEquals("beer", msg.getHeader("BAR"));
        assertNull(msg.getHeader("unknown"));

        msg.removeHeader("bar");
        assertNull(msg.getHeader("bar"));
        assertNull(msg.getHeader("unknown"));
    }

    @Test
    public void testSetWithDifferentCase() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");
        msg.setHeader("Foo", "bar");

        assertEquals("bar", msg.getHeader("FOO"));
        assertEquals("bar", msg.getHeader("foo"));
        assertEquals("bar", msg.getHeader("Foo"));
    }

    @Test
    public void testRemoveWithDifferentCase() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");
        msg.setHeader("Foo", "bar");

        assertEquals("bar", msg.getHeader("FOO"));
        assertEquals("bar", msg.getHeader("foo"));
        assertEquals("bar", msg.getHeader("Foo"));

        msg.removeHeader("FOO");

        assertNull(msg.getHeader("foo"));
        assertNull(msg.getHeader("Foo"));
        assertNull(msg.getHeader("FOO"));

        assertTrue(msg.getHeaders().isEmpty());
    }

    @Test
    public void testRemoveHeaderWithNullValue() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", null);
        msg.removeHeader("tick");

        assertTrue(msg.getHeaders().isEmpty());
    }

    @Test
    public void testRemoveHeadersWithWildcard() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");

        assertEquals("bla", msg.getHeader("tick"));
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));

        msg.removeHeaders("t*");

        assertTrue(msg.getHeaders().isEmpty());
    }

    @Test
    public void testRemoveHeadersAllWithWildcard() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");

        assertEquals("bla", msg.getHeader("tick"));
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));

        msg.removeHeaders("*");

        assertTrue(msg.getHeaders().isEmpty());
    }

    @Test
    public void testRemoveHeadersWithExclude() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tiack", "blaa");
        msg.setHeader("tiock", "blaaa");
        msg.setHeader("tiuck", "blaaaa");

        msg.removeHeaders("ti*", "tiuck", "tiack");

        assertEquals(2, msg.getHeaders().size());
        assertEquals("blaa", msg.getHeader("tiack"));
        assertEquals("blaaaa", msg.getHeader("tiuck"));
    }

    @Test
    public void testRemoveHeadersAllWithExclude() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");

        assertEquals("bla", msg.getHeader("tick"));
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));

        msg.removeHeaders("*", "tick", "tock", "toe");

        // new message headers
        assertEquals("bla", msg.getHeader("tick"));
        assertNull(msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));
    }

    @Test
    public void testRemoveHeadersWithWildcardInExclude() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("taick", "blaa");
        msg.setHeader("tock", "blaaa");

        msg.removeHeaders("*", "ta*");

        assertEquals(2, msg.getHeaders().size());
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaa", msg.getHeader("taick"));
    }

    @Test
    public void testRemoveHeadersWithNulls() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");
        msg.setHeader("taack", "blaaaa");

        assertEquals("bla", msg.getHeader("tick"));
        assertEquals("blaa", msg.getHeader("tack"));
        assertEquals("blaaa", msg.getHeader("tock"));
        assertEquals("blaaaa", msg.getHeader("taack"));

        msg.removeHeaders(null, null, null, null);

        assertFalse(msg.getHeaders().isEmpty());
    }

    @Test
    public void testRemoveHeadersWithNonExcludeHeaders() {
        Message msg = new DefaultMessage(camelContext);
        assertNull(msg.getHeader("foo"));

        msg.setHeader("tick", "bla");
        msg.setHeader("tack", "blaa");
        msg.setHeader("tock", "blaaa");

        msg.removeHeaders("*", "camels", "are", "fun");

        assertTrue(msg.getHeaders().isEmpty());
    }

    @Test
    public void testWithDefaults() {
        DefaultMessage msg = new DefaultMessage(camelContext);
        // must have exchange so to leverage the type converters
        msg.setExchange(new DefaultExchange(new DefaultCamelContext()));

        assertNull(msg.getHeader("foo"));

        msg.setHeader("foo", "cheese");

        assertEquals("cheese", msg.getHeader("foo"));
        assertEquals("cheese", msg.getHeader("foo", "foo"));
        assertEquals("cheese", msg.getHeader("foo", "foo", String.class));

        assertNull(msg.getHeader("beer"));
        assertEquals("foo", msg.getHeader("beer", "foo"));
        assertEquals(Integer.valueOf(123), msg.getHeader("beer", "123", Integer.class));
    }

    @Test
    public void testCopyOnWriteHeaders() {
        // Test that headers are shared until modified
        DefaultMessage msg1 = new DefaultMessage(camelContext);
        msg1.setHeader("foo", "cheese");
        msg1.setHeader("bar", "beer");

        DefaultMessage msg2 = new DefaultMessage(camelContext);
        msg2.copyFrom(msg1);

        // Both should have the same headers
        assertEquals("cheese", msg2.getHeader("foo"));
        assertEquals("beer", msg2.getHeader("bar"));

        // Modify msg2 - should trigger copy-on-write
        msg2.setHeader("foo", "wine");

        // msg2 should have the new value
        assertEquals("wine", msg2.getHeader("foo"));
        assertEquals("beer", msg2.getHeader("bar"));

        // msg1 should still have the original value (not affected by msg2's modification)
        assertEquals("cheese", msg1.getHeader("foo"));
        assertEquals("beer", msg1.getHeader("bar"));
    }

    @Test
    public void testCopyOnWriteHeadersRemove() {
        // Test that removeHeader triggers copy-on-write
        DefaultMessage msg1 = new DefaultMessage(camelContext);
        msg1.setHeader("foo", "cheese");
        msg1.setHeader("bar", "beer");

        DefaultMessage msg2 = new DefaultMessage(camelContext);
        msg2.copyFrom(msg1);

        // Remove header from msg2 - should trigger copy-on-write
        msg2.removeHeader("bar");

        // msg2 should not have bar
        assertNull(msg2.getHeader("bar"));
        assertEquals("cheese", msg2.getHeader("foo"));

        // msg1 should still have both headers
        assertEquals("cheese", msg1.getHeader("foo"));
        assertEquals("beer", msg1.getHeader("bar"));
    }

    @Test
    public void testCopyOnWriteHeadersRemoveMultiple() {
        // Test that removeHeaders triggers copy-on-write
        DefaultMessage msg1 = new DefaultMessage(camelContext);
        msg1.setHeader("foo", "cheese");
        msg1.setHeader("bar", "beer");
        msg1.setHeader("baz", "wine");

        DefaultMessage msg2 = new DefaultMessage(camelContext);
        msg2.copyFrom(msg1);

        // Remove headers from msg2 - should trigger copy-on-write
        msg2.removeHeaders("ba*");

        // msg2 should not have bar or baz
        assertNull(msg2.getHeader("bar"));
        assertNull(msg2.getHeader("baz"));
        assertEquals("cheese", msg2.getHeader("foo"));

        // msg1 should still have all headers
        assertEquals("cheese", msg1.getHeader("foo"));
        assertEquals("beer", msg1.getHeader("bar"));
        assertEquals("wine", msg1.getHeader("baz"));
    }

    @Test
    public void testLazyHeadersInitialization() {
        DefaultMessage msg = new DefaultMessage(camelContext);

        assertNull(msg.getHeader("foo"));
        assertNull(msg.getHeader("bar", Object.class));
        assertEquals("default", msg.getHeader("baz", "default"));

        assertFalse(msg.hasHeaders());
        assertTrue(msg.getHeaders().isEmpty());
    }

    @Test
    public void testCopyOnWriteGetHeadersPut() {
        // getHeaders().put() on a copy must NOT affect the original
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        // Mutate via getHeaders().put()
        copy.getHeaders().put("new-key", "new-value");

        // Original must be unaffected
        assertNull(original.getHeader("new-key"));
        assertEquals("bar", original.getHeader("foo"));
        assertEquals("world", original.getHeader("hello"));
        assertEquals(2, original.getHeaders().size());

        // Copy must have all three
        assertEquals("new-value", copy.getHeader("new-key"));
        assertEquals("bar", copy.getHeader("foo"));
        assertEquals(3, copy.getHeaders().size());
    }

    @Test
    public void testCopyOnWriteGetHeadersClear() {
        // getHeaders().clear() on a copy must NOT affect the original
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        // Clear copy's headers via map reference
        copy.getHeaders().clear();

        // Original must still have its headers
        assertEquals("bar", original.getHeader("foo"));
        assertEquals("world", original.getHeader("hello"));
        assertEquals(2, original.getHeaders().size());

        // Copy must be empty
        assertTrue(copy.getHeaders().isEmpty());
    }

    @Test
    public void testCopyOnWriteGetHeadersPutAll() {
        // getHeaders().putAll() on a copy must NOT affect the original
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        // putAll on copy
        copy.getHeaders().putAll(Map.of("x", "1", "y", "2"));

        // Original must only have foo
        assertEquals(1, original.getHeaders().size());
        assertEquals("bar", original.getHeader("foo"));
        assertNull(original.getHeader("x"));

        // Copy must have all three
        assertEquals(3, copy.getHeaders().size());
        assertEquals("bar", copy.getHeader("foo"));
        assertEquals("1", copy.getHeader("x"));
        assertEquals("2", copy.getHeader("y"));
    }

    @Test
    public void testCopyOnWriteGetHeadersRemove() {
        // getHeaders().remove() on a copy must NOT affect the original
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        // Remove via map reference
        copy.getHeaders().remove("foo");

        // Original must still have foo
        assertEquals("bar", original.getHeader("foo"));
        assertEquals(2, original.getHeaders().size());

        // Copy must not have foo
        assertNull(copy.getHeader("foo"));
        assertEquals(1, copy.getHeaders().size());
    }

    @Test
    public void testCopyOnWriteSetHeadersResetsShared() {
        // setHeaders() must break shared state
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        // Replace copy's headers entirely
        copy.setHeaders(new HashMap<>(Map.of("new", "headers")));

        // Mutating original should not affect copy
        original.setHeader("extra", "value");

        // Copy must only have {new=headers}
        assertEquals(1, copy.getHeaders().size());
        assertEquals("headers", copy.getHeader("new"));
        assertNull(copy.getHeader("foo"));
        assertNull(copy.getHeader("extra"));
    }

    @Test
    public void testCopyOnWriteMultipleCopiesIsolation() {
        // Multiple copies from the same original must be fully isolated
        DefaultMessage original = new DefaultMessage(camelContext);
        for (int i = 0; i < 100; i++) {
            original.setHeader("key-" + i, "value-" + i);
        }

        DefaultMessage copy1 = new DefaultMessage(camelContext);
        copy1.copyFrom(original);

        DefaultMessage copy2 = new DefaultMessage(camelContext);
        copy2.copyFrom(original);

        // Mutate each copy differently
        for (int i = 0; i < 10; i++) {
            copy1.getHeaders().put("copy1-" + i, "v1");
            copy2.getHeaders().put("copy2-" + i, "v2");
        }
        copy1.removeHeader("key-0");
        copy2.removeHeader("key-1");

        // Original must be unmodified (still exactly 100 headers)
        assertEquals(100, original.getHeaders().size());
        for (int i = 0; i < 100; i++) {
            assertEquals("value-" + i, original.getHeader("key-" + i));
        }
        assertNull(original.getHeader("copy1-0"));
        assertNull(original.getHeader("copy2-0"));

        // copy1: 99 original + 10 copy1 keys, no copy2 keys
        assertEquals(109, copy1.getHeaders().size());
        assertNull(copy1.getHeader("key-0"));
        assertEquals("value-1", copy1.getHeader("key-1"));
        assertEquals("v1", copy1.getHeader("copy1-0"));
        assertNull(copy1.getHeader("copy2-0"));

        // copy2: 99 original + 10 copy2 keys, no copy1 keys
        assertEquals(109, copy2.getHeaders().size());
        assertEquals("value-0", copy2.getHeader("key-0"));
        assertNull(copy2.getHeader("key-1"));
        assertNull(copy2.getHeader("copy1-0"));
        assertEquals("v2", copy2.getHeader("copy2-0"));
    }

    @Test
    public void testLazyCopyOnWriteReadOperations() {
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        Map<String, Object> copyHeaders = copy.getHeaders();

        // Read operations should work correctly without triggering COW
        assertEquals(2, copyHeaders.size());
        assertTrue(copyHeaders.containsKey("foo"));
        assertTrue(copyHeaders.containsKey("hello"));
        assertEquals("bar", copyHeaders.get("foo"));
        assertEquals("world", copyHeaders.get("hello"));
        assertFalse(copyHeaders.isEmpty());

        // Original must be unaffected by reads on the copy
        assertEquals("bar", original.getHeader("foo"));
        assertEquals("world", original.getHeader("hello"));
        assertEquals(2, original.getHeaders().size());

        // Iterate over entries (read operation)
        int count = 0;
        for (Map.Entry<String, Object> entry : copyHeaders.entrySet()) {
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
            count++;
        }
        assertEquals(2, count);

        // Original still intact after iteration
        assertEquals("bar", original.getHeader("foo"));
        assertEquals("world", original.getHeader("hello"));
        assertEquals(2, original.getHeaders().size());

        // Now mutate - this should trigger the copy
        copyHeaders.put("new-key", "new-value");

        // Original must be unaffected by the mutation
        assertNull(original.getHeader("new-key"));
        assertEquals(2, original.getHeaders().size());

        // Copy must have the new key
        assertEquals("new-value", copy.getHeader("new-key"));
        assertEquals(3, copy.getHeaders().size());
    }

    @Test
    public void testLazyCopyOnWriteRemove() {
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        Map<String, Object> copyHeaders = copy.getHeaders();

        // Read first - original must be unaffected
        assertEquals("bar", copyHeaders.get("foo"));
        assertEquals("bar", original.getHeader("foo"));
        assertEquals(2, original.getHeaders().size());

        // Now remove from copy
        copyHeaders.remove("foo");

        // Original unaffected
        assertEquals("bar", original.getHeader("foo"));
        assertEquals(2, original.getHeaders().size());

        // Copy affected
        assertNull(copy.getHeader("foo"));
        assertEquals(1, copy.getHeaders().size());
    }

    @Test
    public void testLazyCopyOnWriteClear() {
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        Map<String, Object> copyHeaders = copy.getHeaders();

        // Read operations - original must be unaffected
        assertFalse(copyHeaders.isEmpty());
        assertEquals("bar", original.getHeader("foo"));
        assertEquals(2, original.getHeaders().size());

        // Clear copy
        copyHeaders.clear();

        // Original unaffected
        assertEquals(2, original.getHeaders().size());
        assertEquals("bar", original.getHeader("foo"));

        // Copy cleared
        assertTrue(copy.getHeaders().isEmpty());
    }

    @Test
    public void testLazyCopyOnWriteKeySetIteration() {
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        Map<String, Object> copyHeaders = copy.getHeaders();

        // Iterate over keySet (read-only)
        int count = 0;
        for (String key : copyHeaders.keySet()) {
            assertNotNull(key);
            count++;
        }
        assertEquals(2, count);

        // Original must be unaffected by read-only iteration
        assertEquals("bar", original.getHeader("foo"));
        assertEquals("world", original.getHeader("hello"));
        assertEquals(2, original.getHeaders().size());

        // Now use iterator.remove() - triggers COW
        var iterator = copyHeaders.keySet().iterator();
        assertTrue(iterator.hasNext());
        iterator.next();
        iterator.remove();

        // Original unaffected
        assertEquals(2, original.getHeaders().size());

        // Copy affected
        assertEquals(1, copy.getHeaders().size());
    }

    @Test
    public void testLazyCopyOnWriteValues() {
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        Map<String, Object> copyHeaders = copy.getHeaders();

        // Read from values()
        Collection<Object> values = copyHeaders.values();
        assertEquals(2, values.size());
        assertTrue(values.contains("bar"));
        assertTrue(values.contains("world"));

        // Original must be unaffected by values() read
        assertEquals("bar", original.getHeader("foo"));
        assertEquals(2, original.getHeaders().size());

        // Remove from values() - triggers COW
        values.remove("bar");

        // Original unaffected
        assertEquals("bar", original.getHeader("foo"));
        assertEquals(2, original.getHeaders().size());

        // Copy affected
        assertNull(copy.getHeader("foo"));
        assertEquals(1, copy.getHeaders().size());
    }

    @Test
    public void testLazyCopyOnWriteEntrySet() {
        DefaultMessage original = new DefaultMessage(camelContext);
        original.setHeader("foo", "bar");
        original.setHeader("hello", "world");

        DefaultMessage copy = new DefaultMessage(camelContext);
        copy.copyFrom(original);

        Map<String, Object> copyHeaders = copy.getHeaders();

        // Read from entrySet()
        Set<Map.Entry<String, Object>> entries = copyHeaders.entrySet();
        assertEquals(2, entries.size());

        // Iterate (read-only)
        for (Map.Entry<String, Object> entry : entries) {
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
        }

        // Original must be unaffected by read-only entrySet iteration
        assertEquals("bar", original.getHeader("foo"));
        assertEquals("world", original.getHeader("hello"));
        assertEquals(2, original.getHeaders().size());

        // Remove from entrySet iterator - triggers COW
        var iterator = entries.iterator();
        assertTrue(iterator.hasNext());
        iterator.next();
        iterator.remove();

        // Original unaffected
        assertEquals(2, original.getHeaders().size());

        // Copy affected
        assertEquals(1, copy.getHeaders().size());
    }

    // ========== Lazy populated headers tests ==========

    private static class LazyPopulatedMessage extends DefaultMessage {
        LazyPopulatedMessage(CamelContext camelContext) {
            super(camelContext);
        }

        @Override
        protected void populateInitialHeaders(Map<String, Object> map) {
            map.put("transport-header", "transport-value");
            map.put("content-type", "text/plain");
            map.put("message-id", "12345");
        }

        @Override
        protected boolean isPopulateHeadersSupported() {
            return true;
        }
    }

    @Test
    public void testLazyPopulatedHeadersGetHeader() {
        LazyPopulatedMessage msg = new LazyPopulatedMessage(camelContext);

        assertEquals("transport-value", msg.getHeader("transport-header"));
        assertEquals("text/plain", msg.getHeader("content-type"));
        assertEquals("12345", msg.getHeader("message-id"));
        assertNull(msg.getHeader("nonexistent"));
    }

    @Test
    public void testLazyPopulatedHeadersGetHeaderWithDefault() {
        LazyPopulatedMessage msg = new LazyPopulatedMessage(camelContext);

        assertEquals("transport-value", msg.getHeader("transport-header", "fallback"));
        assertEquals("fallback", msg.getHeader("nonexistent", "fallback"));
    }

    @Test
    public void testLazyPopulatedHeadersGetHeaderWithType() {
        LazyPopulatedMessage msg = new LazyPopulatedMessage(camelContext);
        msg.setExchange(new DefaultExchange(camelContext));

        assertEquals("12345", msg.getHeader("message-id", String.class));
        assertEquals(Integer.valueOf(12345), msg.getHeader("message-id", Integer.class));
        assertNull(msg.getHeader("nonexistent", String.class));
    }

    @Test
    public void testLazyPopulatedHeadersRemoveHeader() {
        LazyPopulatedMessage msg = new LazyPopulatedMessage(camelContext);

        assertEquals("transport-value", msg.removeHeader("transport-header"));
        assertNull(msg.getHeader("transport-header"));
        assertEquals("text/plain", msg.getHeader("content-type"));
        assertEquals(2, msg.getHeaders().size());
    }

    @Test
    public void testLazyPopulatedHeadersGetHeaders() {
        LazyPopulatedMessage msg = new LazyPopulatedMessage(camelContext);

        Map<String, Object> headers = msg.getHeaders();
        assertEquals(3, headers.size());
        assertEquals("transport-value", headers.get("transport-header"));
        assertEquals("text/plain", headers.get("content-type"));
        assertEquals("12345", headers.get("message-id"));
    }

    @Test
    public void testLazyPopulatedHeadersHasHeaders() {
        LazyPopulatedMessage msg = new LazyPopulatedMessage(camelContext);

        assertTrue(msg.hasHeaders());
    }

}
