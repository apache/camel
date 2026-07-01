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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests that a single {@link StateStoreBackend} in the registry is auto-discovered when no explicit backend is
 * specified on the endpoint.
 */
class StateStoreAutoDiscoveryTest extends CamelTestSupport {

    private final TrackingInMemoryBackend trackingBackend = new TrackingInMemoryBackend();

    @BindToRegistry("myBackend")
    public StateStoreBackend getBackend() {
        return trackingBackend;
    }

    @Test
    void testAutoDiscoveryFromRegistry() {
        // No backend=#myBackend in the URI, should auto-discover from registry
        template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("hello", result);

        // Verify our tracking backend was used, not the default InMemoryStateStoreBackend
        assertInstanceOf(TrackingInMemoryBackend.class, trackingBackend);
        assertEquals(Set.of("key1"), trackingBackend.keys());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // No backend=# reference — relies on auto-discovery
                from("direct:put").to("state-store:myStore?operation=put");
                from("direct:get").to("state-store:myStore?operation=get");
            }
        };
    }

    /**
     * A simple wrapper around InMemoryStateStoreBackend to verify it was used instead of a fresh default instance.
     */
    static class TrackingInMemoryBackend extends InMemoryStateStoreBackend {
    }
}
