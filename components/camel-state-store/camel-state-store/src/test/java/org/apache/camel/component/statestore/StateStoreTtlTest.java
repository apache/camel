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
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StateStoreTtlTest extends CamelTestSupport {

    @Test
    void testEntryExpiresAfterTtl() {
        // put a value with 200ms TTL
        template.requestBodyAndHeaders(
                "direct:put", "expiring",
                Map.of(StateStoreConstants.KEY, "ttlKey"));

        // should be retrievable immediately
        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertThat(result).isEqualTo("expiring");

        // wait for TTL to expire
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Object expired = template.requestBodyAndHeaders(
                    "direct:get", null,
                    Map.of(StateStoreConstants.KEY, "ttlKey"));
            assertThat(expired).isNull();
        });
    }

    @Test
    void testContainsReturnsFalseAfterTtl() {
        template.requestBodyAndHeaders(
                "direct:put", "expiring",
                Map.of(StateStoreConstants.KEY, "ttlKey"));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Object exists = template.requestBodyAndHeaders(
                    "direct:contains", null,
                    Map.of(StateStoreConstants.KEY, "ttlKey"));
            assertThat(exists).isEqualTo(false);
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put").to("state-store:ttlStore?operation=put&ttl=200");
                from("direct:get").to("state-store:ttlStore?operation=get");
                from("direct:contains").to("state-store:ttlStore?operation=contains");
            }
        };
    }
}
