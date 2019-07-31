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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.ehcache.Cache;
import org.junit.Test;

public class EhcacheProducerTest extends EhcacheTestSupport {

    // ****************************
    // Clear
    // ****************************

    @Test
    public void testCacheClear() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived((Object)null);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);

        fluentTemplate()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_CLEAR)
            .to("direct://start")
            .send();
        
        assertMockEndpointsSatisfied();
    }

    // ****************************
    // Put
    // ****************************

    @Test
    public void testCachePut() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final String key = generateRandomString();
        final String val = generateRandomString();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(val);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);

        fluentTemplate()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT)
            .withHeader(EhcacheConstants.KEY, key)
            .withBody(val)
            .to("direct://start")
            .send();

        assertMockEndpointsSatisfied();

        assertTrue(cache.containsKey(key));
        assertEquals(val, cache.get(key));
    }

    @Test
    public void testCachePutAll() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final Map<String, String> map = generateRandomMapOfString(3);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);

        fluentTemplate()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT_ALL)
            .withBody(map)
            .to("direct://start")
            .send();

        assertMockEndpointsSatisfied();

        map.forEach((k, v) -> {
            assertTrue(cache.containsKey(k));
            assertEquals(v, cache.get(k));
        });
    }

    @Test
    public void testCachePutIfAbsent() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final String key = generateRandomString();
        final String val1 = generateRandomString();
        final String val2 = generateRandomString();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        mock.expectedBodiesReceived(val1, val2);
        mock.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.ACTION_HAS_RESULT, false, true);
        mock.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.ACTION_SUCCEEDED, true, true);
        mock.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.OLD_VALUE, null, val1);

        fluentTemplate()
            .clearHeaders()
            .clearBody()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT_IF_ABSENT)
            .withHeader(EhcacheConstants.KEY, key)
            .withBody(val1)
            .to("direct://start")
            .send();
        fluentTemplate()
            .clearHeaders()
            .clearBody()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_PUT_IF_ABSENT)
            .withHeader(EhcacheConstants.KEY, key)
            .withBody(val2)
            .to("direct://start")
            .send();

        assertMockEndpointsSatisfied();

        assertTrue(cache.containsKey(key));
        assertEquals(val1, cache.get(key));
    }

    // ****************************
    // Get
    // ****************************

    @Test
    public void testCacheGet() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final String key = generateRandomString();
        final String val = generateRandomString();

        cache.put(key, val);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(val);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, true);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);

        fluentTemplate()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET)
            .withHeader(EhcacheConstants.KEY, key)
            .withBody(val)
            .to("direct://start")
            .send();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCacheGetAll() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final Map<String, String> map = generateRandomMapOfString(3);
        final Set<String> keys = map.keySet().stream().limit(2).collect(Collectors.toSet());

        cache.putAll(map);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, true);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);

        fluentTemplate()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_GET_ALL)
            .withHeader(EhcacheConstants.KEYS, keys)
            .to("direct://start")
            .send();

        assertMockEndpointsSatisfied();

        final Map<String, String> elements = mock.getExchanges().get(0).getIn().getBody(Map.class);
        keys.forEach(k -> {
            assertTrue(elements.containsKey(k));
            assertEquals(map.get(k), elements.get(k));
        });
    }

    // ****************************
    // Remove
    // ****************************

    @Test
    public void testCacheRemove() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final String key = generateRandomString();
        final String val = generateRandomString();

        cache.put(key, val);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);

        fluentTemplate()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_REMOVE)
            .withHeader(EhcacheConstants.KEY, key)
            .to("direct://start")
            .send();

        assertMockEndpointsSatisfied();

        assertFalse(cache.containsKey(key));
    }

    @Test
    public void testCacheRemoveIf() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final String key = generateRandomString();
        final String val1 = generateRandomString();
        final String val2 = generateRandomString();

        cache.put(key, val1);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        mock.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.ACTION_HAS_RESULT, false, false);
        mock.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.ACTION_SUCCEEDED, false, true);

        fluentTemplate()
            .clearHeaders()
            .clearBody()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_REMOVE)
            .withHeader(EhcacheConstants.KEY, key)
            .withHeader(EhcacheConstants.OLD_VALUE, val2)
            .to("direct://start")
            .send();

        assertTrue(cache.containsKey(key));

        fluentTemplate()
            .clearHeaders()
            .clearBody()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_REMOVE)
            .withHeader(EhcacheConstants.KEY, key)
            .withHeader(EhcacheConstants.OLD_VALUE, val1)
            .to("direct://start")
            .send();

        assertMockEndpointsSatisfied();

        assertFalse(cache.containsKey(key));
    }

    @Test
    public void testCacheRemoveAll() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final Map<String, String> map = generateRandomMapOfString(3);
        final Set<String> keys = map.keySet().stream().limit(2).collect(Collectors.toSet());

        cache.putAll(map);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_HAS_RESULT, false);
        mock.expectedHeaderReceived(EhcacheConstants.ACTION_SUCCEEDED, true);

        fluentTemplate()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_REMOVE_ALL)
            .withHeader(EhcacheConstants.KEYS, keys)
            .to("direct://start")
            .send();

        assertMockEndpointsSatisfied();

        cache.forEach(e -> assertFalse(keys.contains(e.getKey())));
    }

    // ****************************
    // Replace
    // ****************************

    @Test
    public void testCacheReplace() throws Exception {
        final Cache<Object, Object> cache = getTestCache();
        final String key = generateRandomString();
        final String val1 = generateRandomString();
        final String val2 = generateRandomString();
        final String val3 = generateRandomString();

        cache.put(key, val1);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);
        mock.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.ACTION_HAS_RESULT, false, false, false);
        mock.expectedHeaderValuesReceivedInAnyOrder(EhcacheConstants.ACTION_SUCCEEDED, true, false, true);

        assertEquals(val1, cache.get(key));

        fluentTemplate()
            .clearHeaders()
            .clearBody()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_REPLACE)
            .withHeader(EhcacheConstants.KEY, key)
            .withBody(val2)
            .to("direct://start")
            .send();

        assertEquals(val2, cache.get(key));

        fluentTemplate()
            .clearHeaders()
            .clearBody()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_REPLACE)
            .withHeader(EhcacheConstants.KEY, key)
            .withHeader(EhcacheConstants.OLD_VALUE, val1)
            .withBody(val3)
            .to("direct://start")
            .send();

        assertEquals(val2, cache.get(key));

        fluentTemplate()
            .clearHeaders()
            .clearBody()
            .withHeader(EhcacheConstants.ACTION, EhcacheConstants.ACTION_REPLACE)
            .withHeader(EhcacheConstants.KEY, key)
            .withHeader(EhcacheConstants.OLD_VALUE, val2)
            .withBody(val3)
            .to("direct://start")
            .send();

        assertEquals(val3, cache.get(key));

        assertMockEndpointsSatisfied();
    }

    // ****************************
    // Route
    // ****************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                  .toF("ehcache://%s?cacheManager=#cacheManager", TEST_CACHE_NAME)
                    .to("log:org.apache.camel.component.ehcache?level=INFO&showAll=true&multiline=true")
                    .to("mock:result");
            }
        };
    }
}
