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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StateStoreTtlTest extends CamelTestSupport {

    @Test
    void testEntryExpiresAfterTtl() throws Exception {
        // put a value with 200ms TTL
        template.requestBodyAndHeaders(
                "direct:put", "expiring",
                java.util.Map.of(StateStoreConstants.KEY, "ttlKey"));

        // should be retrievable immediately
        Object result = template.requestBodyAndHeaders(
                "direct:get", null,
                java.util.Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertEquals("expiring", result);

        // wait for TTL to expire
        Thread.sleep(300);

        // should be expired now
        Object expired = template.requestBodyAndHeaders(
                "direct:get", null,
                java.util.Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertNull(expired);
    }

    @Test
    void testContainsReturnsFalseAfterTtl() throws Exception {
        template.requestBodyAndHeaders(
                "direct:put", "expiring",
                java.util.Map.of(StateStoreConstants.KEY, "ttlKey"));

        Thread.sleep(300);

        Object exists = template.requestBodyAndHeaders(
                "direct:contains", null,
                java.util.Map.of(StateStoreConstants.KEY, "ttlKey"));
        assertEquals(false, exists);
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
