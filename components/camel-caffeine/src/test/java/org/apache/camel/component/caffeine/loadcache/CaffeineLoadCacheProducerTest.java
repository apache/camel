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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.caffeine.CaffeineConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class CaffeineLoadCacheProducerTest extends CaffeineLoadCacheTestSupport {

    // ****************************
    // Clear
    // ****************************

    @Test
    public void testCacheClear() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived((Object)null);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_CLEANUP).to("direct://start").send();

        assertMockEndpointsSatisfied();
    }

    // ****************************
    // Put
    // ****************************

    @Test
    public void testCachePut() throws Exception {
        final int key = 1;
        final int val = 3;

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(val);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_PUT).withHeader(CaffeineConstants.KEY, key).withBody(val).to("direct://start").send();

        assertTrue(getTestCache().getIfPresent(key) != null);
        assertEquals(val, getTestCache().getIfPresent(key));
    }

    @Test
    public void testCachePutAll() throws Exception {
        final Map<Integer, Integer> map = new HashMap<>();
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        final Set<Integer> keys = map.keySet().stream().limit(2).collect(Collectors.toSet());

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_PUT_ALL).withBody(map).to("direct://start").send();

        MockEndpoint mock1 = getMockEndpoint("mock:result");
        mock1.expectedMinimumMessageCount(1);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock1.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        final Map<String, String> elements = getTestCache().getAllPresent(keys);
        keys.forEach(k -> {
            assertTrue(elements.containsKey(k));
            assertEquals(map.get(k), elements.get(k));
        });

        assertMockEndpointsSatisfied();
    }

    // ****************************
    // Get
    // ****************************

    @Test
    public void testCacheGet() throws Exception {
        final Integer key = 1;
        final Integer val = 2;

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(val);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, true);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_GET).withHeader(CaffeineConstants.KEY, key).withBody(val).to("direct://start").send();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCacheGetAll() throws Exception {
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

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_GET_ALL).withHeader(CaffeineConstants.KEYS, keys).to("direct://start").send();

        assertMockEndpointsSatisfied();

        final Map<String, String> elements = mock.getExchanges().get(0).getIn().getBody(Map.class);
        keys.forEach(k -> {
            assertTrue(elements.containsKey(k));
            assertEquals(map.get(k), elements.get(k));
        });
    }

    // ****************************
    // INVALIDATE
    // ****************************

    @Test
    public void testCacheInvalidate() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final Integer key = 1;
        final Integer val = 1;

        cache.put(key, val);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(CaffeineConstants.ACTION_SUCCEEDED, true);

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_INVALIDATE).withHeader(CaffeineConstants.KEY, key).to("direct://start").send();

        assertMockEndpointsSatisfied();

        assertFalse(cache.getIfPresent(key) != null);
    }

    @Test
    public void testCacheInvalidateAll() throws Exception {
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

        fluentTemplate().withHeader(CaffeineConstants.ACTION, CaffeineConstants.ACTION_INVALIDATE_ALL).withHeader(CaffeineConstants.KEYS, keys).to("direct://start").send();

        assertMockEndpointsSatisfied();

        final Map<String, String> elements = getTestCache().getAllPresent(keys);
        keys.forEach(k -> {
            assertFalse(elements.containsKey(k));
        });
    }

    // ****************************
    // Route
    // ****************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start").toF("caffeine-loadcache://%s?cache=#cache", "test").to("log:org.apache.camel.component.caffeine?level=INFO&showAll=true&multiline=true")
                    .to("mock:result");
            }
        };
    }
}
