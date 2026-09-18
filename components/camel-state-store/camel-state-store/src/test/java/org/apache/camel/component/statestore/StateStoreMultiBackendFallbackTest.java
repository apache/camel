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
import org.apache.camel.support.MemoryKeyValueRepository;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that when multiple {@link org.apache.camel.spi.KeyValueRepository} instances are in the registry and no
 * explicit backend is specified, the component falls back to a fresh {@link MemoryKeyValueRepository} and logs a
 * warning.
 */
class StateStoreMultiBackendFallbackTest extends CamelTestSupport {

    @BindToRegistry("backend1")
    private final MemoryKeyValueRepository backendOne = new MemoryKeyValueRepository();

    @BindToRegistry("backend2")
    private final MemoryKeyValueRepository backendTwo = new MemoryKeyValueRepository();

    @Test
    void testFallsBackToInMemoryWhenMultipleBackends() {
        // With two backends in the registry and no explicit reference,
        // auto-discovery should fall back to a fresh MemoryKeyValueRepository
        Object previous = template.requestBodyAndHeaders(
                "direct:put", "hello",
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(previous).isNull();

        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "key1"));
        assertThat(result).isEqualTo("hello");

        // Verify neither registered backend was used
        assertThat(backendOne.size()).isZero();
        assertThat(backendTwo.size()).isZero();
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
