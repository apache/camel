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
package org.apache.camel.component.ehcache;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.ehcache.event.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the default (no explicit cacheManager) behavior of camel-ehcache: shared CacheManager across endpoints,
 * consumer event delivery on unconfigured caches, null body handling, and independent key/value type defaults.
 */
class EhcacheDefaultsTest extends CamelTestSupport {

    /**
     * Bug 1: Two endpoints sharing the same cache name without explicit cacheManager should share data. A PUT on one
     * endpoint followed by a GET on another endpoint must return the stored value.
     */
    @Test
    void testSharedDefaultCacheManager() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:get-result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, true);

        String key = "shared-key";
        String val = "shared-value";

        // Two distinct endpoint instances (different URIs) must share the same manager
        EhcacheEndpoint putEndpoint
                = context.getEndpoint("ehcache://defaultTestCache?action=PUT", EhcacheEndpoint.class);
        EhcacheEndpoint getEndpoint
                = context.getEndpoint("ehcache://defaultTestCache?action=GET", EhcacheEndpoint.class);
        assertSame(putEndpoint.getManager(), getEndpoint.getManager(),
                "distinct endpoints for the same cache name must share the default CacheManager");

        // PUT via the first endpoint
        fluentTemplate()
                .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT)
                .withHeader(EhcacheConstants.KEY, key)
                .withBody(val)
                .to("direct://put")
                .send();

        // GET via a different endpoint (same cache name, no explicit cacheManager)
        fluentTemplate()
                .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET)
                .withHeader(EhcacheConstants.KEY, key)
                .to("direct://get")
                .send();

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(val, mock.getExchanges().get(0).getIn().getBody(String.class));
    }

    /**
     * Bug 2: A consumer on a cache without explicit configuration should receive CREATED events when values are PUT via
     * the producer. Previously, the UserManagedCache fallback lacked event executor services, so listeners were
     * silently never called.
     */
    @Test
    void testConsumerReceivesEventsWithoutExplicitConfig() throws Exception {
        MockEndpoint created = getMockEndpoint("mock:events");
        created.expectedMinimumMessageCount(1);
        created.expectedHeaderReceived(EhcacheConstants.EVENT_TYPE, EventType.CREATED);

        String key = "event-key";
        String val = "event-value";

        fluentTemplate()
                .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT)
                .withHeader(EhcacheConstants.KEY, key)
                .withBody(val)
                .to("direct://put-events")
                .send();

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(key, created.getExchanges().get(0).getIn().getHeader(EhcacheConstants.KEY));
        assertEquals(val, created.getExchanges().get(0).getIn().getBody(String.class));
    }

    /**
     * Bug 3: A PUT with null body and no CamelEhcacheValue header must throw CamelExchangeException, not silently store
     * the DefaultMessage object.
     */
    @Test
    void testPutWithNullBodyThrows() throws Exception {
        Exchange result = fluentTemplate()
                .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT)
                .withHeader(EhcacheConstants.KEY, "null-body-key")
                .withBody((Object) null)
                .to("direct://put")
                .send();

        assertNotNull(result.getException());
        assertInstanceOf(CamelExchangeException.class, result.getException());
    }

    /**
     * Bug 4: Setting valueType without keyType should not cause NPE. The keyType must independently default to
     * Object.class.
     */
    @Test
    void testValueTypeWithoutKeyTypeDoesNotThrowNPE() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:typed-result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);

        String key = "typed-key";
        String val = "typed-value";

        fluentTemplate()
                .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT)
                .withHeader(EhcacheConstants.KEY, key)
                .withBody(val)
                .to("direct://put-typed")
                .send();

        MockEndpoint.assertIsSatisfied(context);

        // Verify we can GET the value back
        MockEndpoint getMock = getMockEndpoint("mock:typed-get-result");
        getMock.expectedMinimumMessageCount(1);
        getMock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, true);

        fluentTemplate()
                .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET)
                .withHeader(EhcacheConstants.KEY, key)
                .to("direct://get-typed")
                .send();

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(val, getMock.getExchanges().get(0).getIn().getBody(String.class));
    }

    /**
     * Bug 3 (variant): PUT_IF_ABSENT with null body must also throw.
     */
    @Test
    void testPutIfAbsentWithNullBodyThrows() throws Exception {
        Exchange result = fluentTemplate()
                .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT_IF_ABSENT)
                .withHeader(EhcacheConstants.KEY, "null-body-key-2")
                .withBody((Object) null)
                .to("direct://put")
                .send();

        assertNotNull(result.getException());
        assertInstanceOf(CamelExchangeException.class, result.getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // Bug 1: two separate endpoints for the same cache, no explicit cacheManager
                from("direct://put")
                        .to("ehcache://defaultTestCache")
                        .to("mock:put-result");
                from("direct://get")
                        .to("ehcache://defaultTestCache")
                        .to("mock:get-result");

                // Bug 2: consumer on a cache without explicit config
                from("direct://put-events")
                        .to("ehcache://eventsCache")
                        .to("mock:put-events-result");
                from("ehcache://eventsCache?eventTypes=CREATED&eventFiring=SYNCHRONOUS")
                        .to("mock:events");

                // Bug 4: valueType set without keyType
                from("direct://put-typed")
                        .to("ehcache://typedCache?valueType=java.lang.String")
                        .to("mock:typed-result");
                from("direct://get-typed")
                        .to("ehcache://typedCache?valueType=java.lang.String")
                        .to("mock:typed-get-result");
            }
        };
    }
}
