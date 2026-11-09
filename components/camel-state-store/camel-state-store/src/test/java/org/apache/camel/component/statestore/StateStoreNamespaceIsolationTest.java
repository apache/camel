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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that multiple state stores sharing the same underlying {@link KeyValueRepository} keep their keys and
 * operations isolated within their respective namespaces.
 */
class StateStoreNamespaceIsolationTest extends CamelTestSupport {

    private final MemoryKeyValueRepository sharedBackend = new MemoryKeyValueRepository();

    @BindToRegistry("sharedBackend")
    public KeyValueRepository getSharedBackend() {
        return sharedBackend;
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCrossStoreIsolationAndClear() {
        // Store entries in storeA
        template.requestBodyAndHeaders("direct:putA", "valA1", Map.of(StateStoreConstants.KEY, "item1"));
        template.requestBodyAndHeaders("direct:putA", "valA2", Map.of(StateStoreConstants.KEY, "item2"));

        // Store entry in storeB with identical key name
        template.requestBodyAndHeaders("direct:putB", "valB1", Map.of(StateStoreConstants.KEY, "item1"));

        // Pre-existing external key in shared backend (e.g. from an aggregation repository)
        sharedBackend.put("aggregate:grp1", "holderData");

        // Verify isolation
        assertThat(template.requestBodyAndHeaders("direct:getA", null, Map.of(StateStoreConstants.KEY, "item1")))
                .isEqualTo("valA1");
        assertThat(template.requestBodyAndHeaders("direct:getB", null, Map.of(StateStoreConstants.KEY, "item1")))
                .isEqualTo("valB1");

        // Verify keys in storeA
        Set<String> keysA = (Set<String>) template.requestBody("direct:keysA", (Object) null);
        assertThat(keysA).containsExactlyInAnyOrder("item1", "item2");

        // Clear storeA
        template.requestBody("direct:clearA", (Object) null);

        // storeA is now empty
        assertThat((Set<String>) template.requestBody("direct:keysA", (Object) null)).isEmpty();
        assertThat(template.requestBodyAndHeaders("direct:getA", null, Map.of(StateStoreConstants.KEY, "item1")))
                .isNull();

        // storeB and shared backend entries remain intact
        assertThat(template.requestBodyAndHeaders("direct:getB", null, Map.of(StateStoreConstants.KEY, "item1")))
                .isEqualTo("valB1");
        assertThat(sharedBackend.get("aggregate:grp1")).isEqualTo("holderData");
    }

    @Test
    void testPatternKeyNamesDoNotCollide() {
        // Store a key with a prefix that resembles aggregation or idempotent pattern prefixes
        template.requestBodyAndHeaders("direct:putA", "customPayload", Map.of(StateStoreConstants.KEY, "aggregate:grp1"));

        // Ensure key in underlying repository is safely prefixed under the state store namespace
        assertThat(sharedBackend.get("statestore:storeA:aggregate:grp1")).isEqualTo("customPayload");

        // Underlying raw aggregate:grp1 is not overwritten
        assertThat(sharedBackend.get("aggregate:grp1")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testStoreNamesSharingAPrefixAreIsolated() {
        template.requestBodyAndHeaders("direct:putDynamic", "archived",
                Map.of("reproStore", "orders:archive", StateStoreConstants.KEY, "x"));
        template.requestBodyAndHeaders("direct:putDynamic", "live",
                Map.of("reproStore", "orders", StateStoreConstants.KEY, "y"));

        Set<String> keys = (Set<String>) template.requestBodyAndHeaders("direct:keysDynamic", null,
                Map.of("reproStore", "orders"));
        assertThat(keys).containsExactly("y");

        // Clear on 'orders' must not wipe 'orders:archive'
        template.requestBodyAndHeaders("direct:clearDynamic", null,
                Map.of("reproStore", "orders"));

        assertThat((Set<String>) template.requestBodyAndHeaders("direct:keysDynamic", null,
                Map.of("reproStore", "orders"))).isEmpty();
        assertThat(template.requestBodyAndHeaders("direct:getDynamic", null,
                Map.of("reproStore", "orders:archive", StateStoreConstants.KEY, "x")))
                .isEqualTo("archived");
    }

    @Test
    void testOptOutNamespacingForBackwardCompatibility() {
        // Data written by 4.23 without namespacing can be accessed by opting out of namespacing
        sharedBackend.put("legacyKey", "legacyValue");

        Object value = template.requestBodyAndHeaders("direct:getUnnamespaced", null,
                Map.of(StateStoreConstants.KEY, "legacyKey"));
        assertThat(value).isEqualTo("legacyValue");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putA").to("state-store:storeA?operation=put");
                from("direct:getA").to("state-store:storeA?operation=get");
                from("direct:keysA").to("state-store:storeA?operation=keys");
                from("direct:clearA").to("state-store:storeA?operation=clear");

                from("direct:putB").to("state-store:storeB?operation=put");
                from("direct:getB").to("state-store:storeB?operation=get");

                from("direct:putDynamic").toD("state-store:${header.reproStore}?operation=put");
                from("direct:getDynamic").toD("state-store:${header.reproStore}?operation=get");
                from("direct:keysDynamic").toD("state-store:${header.reproStore}?operation=keys");
                from("direct:clearDynamic").toD("state-store:${header.reproStore}?operation=clear");

                from("direct:getUnnamespaced").to("state-store:legacyStore?namespaced=false&operation=get");
            }
        };
    }
}
