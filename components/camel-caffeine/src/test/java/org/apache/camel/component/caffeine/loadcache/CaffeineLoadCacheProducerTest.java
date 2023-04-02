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
package org.apache.camel.component.caffeine.loadcache;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.caffeine.CaffeineConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CaffeineLoadCacheProducerTest extends CaffeineLoadCacheTestSupport {

    // ****************************
    // Clear
    // ****************************

    @Test
    void testCacheClear() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived((Object) null);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_CLEANUP).to("direct://start").send();

        MockEndpoint.assertIsSatisfied(context);
    }

    // ****************************
    // Put
    // ****************************

    @Test
    void testCachePut() {
        final String key = "1";
        final int val = 3;

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(val);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_PUT)
                .withHeader(CaffeineConstants.KEY, key).withBody(val).to("direct://start").send();

        assertNotNull(getTestCache().getIfPresent(key));
        assertEquals(val, getTestCache().getIfPresent(key));
    }

    @Test
    void testCachePutAll() throws Exception {
        final Map<Integer, Integer> map = new HashMap<>();
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        final Set<Integer> keys = map.keySet().stream().limit(2).collect(Collectors.toSet());

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_PUT_ALL).withBody(map)
                .to("direct://start").send();

        MockEndpoint mock1 = getMockEndpoint("mock:result");
        mock1.expectedMinimumMessageCount(1);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        final Map<String, String> elements = getTestCache().getAllPresent(keys);
        keys.forEach(k -> {
            assertTrue(elements.containsKey(k));
            assertSame(map.get(k), elements.get(k));
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    // ****************************
    // Get
    // ****************************

    @Test
    void testCacheGet() throws Exception {
        final String key = "1";
        final Integer val = 2;

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(val);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, true);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_GET)
                .withHeader(CaffeineConstants.KEY, key).withBody(val).to("direct://start").send();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testCacheGetAll() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final Map<Integer, Integer> map = new HashMap<>();
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        final Set<Integer> keys = map.keySet().stream().limit(2).collect(Collectors.toSet());

        cache.putAll(map);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, true);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_GET_ALL)
                .withHeader(CaffeineConstants.KEYS, keys).to("direct://start").send();

        MockEndpoint.assertIsSatisfied(context);

        final Map<String, String> elements = mock.getExchanges().get(0).getIn().getBody(Map.class);
        keys.forEach(k -> {
            assertTrue(elements.containsKey(k));
            assertSame(map.get(k), elements.get(k));
        });
    }

    // ****************************
    // INVALIDATE
    // ****************************

    @Test
    void testCacheInvalidate() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final String key = "1";
        final Integer val = 1;

        cache.put(key, val);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_INVALIDATE)
                .withHeader(CaffeineConstants.KEY, key).to("direct://start").send();

        MockEndpoint.assertIsSatisfied(context);

        assertNull(cache.getIfPresent(key));
    }

    @Test
    void testCacheInvalidateAll() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final Map<Integer, Integer> map = new HashMap<>();
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        final Set<Integer> keys = map.keySet().stream().limit(2).collect(Collectors.toSet());

        cache.putAll(map);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_INVALIDATE_ALL)
                .withHeader(CaffeineConstants.KEYS, keys).to("direct://start").send();

        MockEndpoint.assertIsSatisfied(context);

        final Map<String, String> elements = getTestCache().getAllPresent(keys);
        keys.forEach(k -> {
            assertFalse(elements.containsKey(k));
        });
    }

    @Test
    void testCacheInvalidateAllWithoutKeys() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final Map<Integer, Integer> map = new HashMap<>();
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);

        cache.putAll(map);

        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_INVALIDATE_ALL)
                .to("direct://start").send();

        MockEndpoint.assertIsSatisfied(context);

        assertTrue(getTestCache().asMap().keySet().isEmpty());
    }

    @Test
    void testCacheAsMap() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final Map<String, String> map = new HashMap<>();
        map.put("A", "AA");
        map.put("B", "BB");
        map.put("C", "CC");

        cache.putAll(map);

        final MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, true);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        final Exchange exchange = fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_AS_MAP)
                .to("direct://start").send();

        MockEndpoint.assertIsSatisfied(context);

        final Map<String, String> elements = exchange.getMessage().getBody(Map.class);
        map.forEach((k, s) -> {
            assertTrue(elements.containsKey(k));
            assertEquals(s, elements.get(k));
        });
    }

    // ****************************
    // Route
    // ****************************

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                        .to("caffeine-loadcache://cache")
                        .to("log:org.apache.camel.component.caffeine?level=INFO&showAll=true&multiline=true")
                        .to("mock:result");
            }
        };
    }
}
