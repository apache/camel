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

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StateStoreTest extends CamelTestSupport {

    @Test
    void testPutAndGet() {
        Object previous = template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertNull(previous);

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("hello", result);
    }

    @Test
    void testPutReturnsOldValue() {
        template.requestBodyAndHeaders(
                "direct:put", "first",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object previous = template.requestBodyAndHeaders(
                "direct:put", "second",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("first", previous);
    }

    @Test
    void testGetNonExistent() {
        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "missing"));
        assertNull(result);
    }

    @Test
    void testDelete() {
        template.requestBodyAndHeaders(
                "direct:put", "value",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object removed = template.requestBodyAndHeaders(
                "direct:delete", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("value", removed);

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertNull(result);
    }

    @Test
    void testContains() {
        template.requestBodyAndHeaders(
                "direct:put", "value",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object exists = template.requestBodyAndHeaders(
                "direct:contains", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals(true, exists);

        Object notExists = template.requestBodyAndHeaders(
                "direct:contains", null,
                Map.of(StateStoreConstants.KEY, "missing"));
        assertEquals(false, notExists);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testKeys() {
        template.requestBodyAndHeaders(
                "direct:put", "v1",
                Map.of(StateStoreConstants.KEY, "a"));
        template.requestBodyAndHeaders(
                "direct:put", "v2",
                Map.of(StateStoreConstants.KEY, "b"));

        Set<String> keys = (Set<String>) template.requestBody("direct:keys", (Object) null);
        assertEquals(Set.of("a", "b"), keys);
    }

    @Test
    void testSize() {
        template.requestBodyAndHeaders(
                "direct:put", "v1",
                Map.of(StateStoreConstants.KEY, "a"));
        template.requestBodyAndHeaders(
                "direct:put", "v2",
                Map.of(StateStoreConstants.KEY, "b"));

        Object size = template.requestBody("direct:size", (Object) null);
        assertEquals(2, size);
    }

    @Test
    void testClear() {
        template.requestBodyAndHeaders(
                "direct:put", "v1",
                Map.of(StateStoreConstants.KEY, "a"));

        template.requestBody("direct:clear", (Object) null);

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "a"));
        assertNull(result);
    }

    @Test
    void testPutIfAbsent() {
        // first put should succeed
        Object result = template.requestBodyAndHeaders(
                "direct:putIfAbsent", "first",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertNull(result);

        // second put should return existing value
        result = template.requestBodyAndHeaders(
                "direct:putIfAbsent", "second",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("first", result);

        // verify original value is still there
        Object value = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("first", value);
    }

    @Test
    void testOperationViaHeader() {
        template.requestBodyAndHeaders(
                "direct:dynamic", "hello",
                Map.of(
                        StateStoreConstants.OPERATION, StateStoreOperations.put,
                        StateStoreConstants.KEY, "key1"));

        Object result = template.requestBodyAndHeaders(
                "direct:dynamic", null,
                Map.of(
                        StateStoreConstants.OPERATION, "get",
                        StateStoreConstants.KEY, "key1"));
        assertEquals("hello", result);
    }

    @Test
    void testPerMessageTtlHeader() throws Exception {
        // put with per-message TTL of 200ms (no TTL on endpoint)
        template.requestBodyAndHeaders(
                "direct:put", "expiring",
                Map.of(
                        StateStoreConstants.KEY, "ttlKey",
                        StateStoreConstants.TTL, 200L));

        // should be there immediately
        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertEquals("expiring", result);

        Thread.sleep(1000);

        // should be expired
        result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertNull(result);
    }

    @Test
    void testMissingKeyHeaderThrows() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBody("direct:get", (Object) null);
        });
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    void testMissingOperationThrows() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> {
            template.requestBodyAndHeaders("direct:dynamic", "value", Map.of());
        });
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put").to("state-store:myStore?operation=put");
                from("direct:putIfAbsent").to("state-store:myStore?operation=putIfAbsent");
                from("direct:get").to("state-store:myStore?operation=get");
                from("direct:delete").to("state-store:myStore?operation=delete");
                from("direct:contains").to("state-store:myStore?operation=contains");
                from("direct:keys").to("state-store:myStore?operation=keys");
                from("direct:size").to("state-store:myStore?operation=size");
                from("direct:clear").to("state-store:myStore?operation=clear");
                from("direct:dynamic").to("state-store:myStore");
            }
        };
    }
}
