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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that when multiple {@link StateStoreBackend} instances are in the registry and no explicit backend is
 * specified, the component falls back to {@link InMemoryStateStoreBackend} and logs a warning.
 */
class StateStoreMultiBackendFallbackTest extends CamelTestSupport {

    @BindToRegistry("backend1")
    private final InMemoryStateStoreBackend backendOne = new InMemoryStateStoreBackend();

    @BindToRegistry("backend2")
    private final InMemoryStateStoreBackend backendTwo = new InMemoryStateStoreBackend();

    @Test
    void testFallsBackToInMemoryWhenMultipleBackends() {
        // With two backends in the registry and no explicit reference,
        // auto-discovery should fall back to a fresh InMemoryStateStoreBackend
        Object previous = template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertNull(previous);

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertEquals("hello", result);

        // Verify neither registered backend was used
        assertEquals(0, backendOne.size());
        assertEquals(0, backendTwo.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put").to("state-store:myStore?operation=put");
                from("direct:get").to("state-store:myStore?operation=get");
            }
        };
    }
}
