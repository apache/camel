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
package org.apache.camel.component.statestore.caffeine;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.statestore.StateStoreConstants;
import org.apache.camel.component.statestore.StateStoreOperations;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CaffeineStateStoreBackendTest extends CamelTestSupport {

    @BindToRegistry("caffeineBackend")
    private final CaffeineStateStoreBackend backend = new CaffeineStateStoreBackend();

    @Test
    void testPutAndGet() {
        template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void testPutReturnsOldValue() {
        template.requestBodyAndHeaders(
                "direct:put", "first",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object previous = template.requestBodyAndHeaders(
                "direct:put", "second",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(previous).isEqualTo("first");
    }

    @Test
    void testDelete() {
        template.requestBodyAndHeaders(
                "direct:put", "value",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object removed = template.requestBodyAndHeaders(
                "direct:delete", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(removed).isEqualTo("value");

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(result).isNull();
    }

    @Test
    void testContains() {
        template.requestBodyAndHeaders(
                "direct:put", "value",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object exists = template.requestBodyAndHeaders(
                "direct:dynamic", null,
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.contains,
                        StateStoreConstants.KEY, "key1"));
        assertThat(exists).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testKeysAndSize() {
        template.requestBodyAndHeaders(
                "direct:put", "v1",
                Map.of(StateStoreConstants.KEY, "a"));
        template.requestBodyAndHeaders(
                "direct:put", "v2",
                Map.of(StateStoreConstants.KEY, "b"));

        Set<String> keys = (Set<String>) template.requestBodyAndHeaders(
                "direct:dynamic", null,
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.keys));
        assertThat(keys).containsExactlyInAnyOrder("a", "b");

        Object size = template.requestBodyAndHeaders(
                "direct:dynamic", null,
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.size));
        assertThat(size).isEqualTo(2);
    }

    @Test
    void testPutIfAbsent() {
        Object result = template.requestBodyAndHeaders(
                "direct:dynamic", "first",
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.putIfAbsent,
                        StateStoreConstants.KEY, "key1"));
        assertThat(result).isNull();

        result = template.requestBodyAndHeaders(
                "direct:dynamic", "second",
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.putIfAbsent,
                        StateStoreConstants.KEY, "key1"));
        assertThat(result).isEqualTo("first");
    }

    @Test
    void testClear() {
        template.requestBodyAndHeaders(
                "direct:put", "v1",
                Map.of(StateStoreConstants.KEY, "a"));

        template.requestBodyAndHeaders(
                "direct:dynamic", null,
                Map.of(StateStoreConstants.OPERATION, StateStoreOperations.clear));

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "a"));
        assertThat(result).isNull();
    }

    @Test
    void testTtlExpiry() {
        template.requestBodyAndHeaders(
                "direct:put-ttl", "expiring",
                Map.of(StateStoreConstants.KEY, "ttlKey"));

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertThat(result).isEqualTo("expiring");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            backend.keys(); // trigger Caffeine cleanup
            Object expired = template.requestBodyAndHeaders(
                    "direct:get", null,
                    Map.of(StateStoreConstants.KEY, "ttlKey"));
            assertThat(expired).isNull();
        });
    }

    @Test
    void testPerMessageTtlHeader() {
        template.requestBodyAndHeaders(
                "direct:put", "expiring",
                Map.of(StateStoreConstants.KEY, "ttlKey",
                        StateStoreConstants.TTL, 200L));

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertThat(result).isEqualTo("expiring");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            backend.keys();
            Object expired = template.requestBodyAndHeaders(
                    "direct:get", null,
                    Map.of(StateStoreConstants.KEY, "ttlKey"));
            assertThat(expired).isNull();
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put").to("state-store:myStore?operation=put&backend=#caffeineBackend");
                from("direct:put-ttl").to("state-store:myStore?operation=put&backend=#caffeineBackend&ttl=200");
                from("direct:get").to("state-store:myStore?operation=get&backend=#caffeineBackend");
                from("direct:delete").to("state-store:myStore?operation=delete&backend=#caffeineBackend");
                from("direct:dynamic").to("state-store:myStore?backend=#caffeineBackend");
            }
        };
    }
}
