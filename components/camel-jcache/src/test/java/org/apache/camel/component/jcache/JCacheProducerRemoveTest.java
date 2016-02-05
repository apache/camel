/**
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
package org.apache.camel.component.jcache;

import java.util.HashMap;
import java.util.Map;
import javax.cache.Cache;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JCacheProducerRemoveTest extends JCacheComponentTestSupport {

    @Test
    public void testRemove() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REMOVE");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:remove", null, headers);

        MockEndpoint mock = getMockEndpoint("mock:remove");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedHeaderReceived(JCacheConstants.RESULT, true);
        mock.assertIsSatisfied();

        assertFalse(cache.containsKey(key));
    }

    @Test
    public void testRemoveIf() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REMOVE");
        headers.put(JCacheConstants.KEY, key);
        headers.put(JCacheConstants.OLD_VALUE, val);
        sendBody("direct:remove-if", null, headers);

        MockEndpoint mock = getMockEndpoint("mock:remove-if");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedHeaderReceived(JCacheConstants.RESULT, true);
        mock.assertIsSatisfied();

        assertFalse(cache.containsKey(key));
    }

    @Test
    public void testRemoveIfFailure() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REMOVE");
        headers.put(JCacheConstants.KEY, key);
        headers.put(JCacheConstants.OLD_VALUE, "x");
        sendBody("direct:remove-if-failure", null, headers);

        MockEndpoint mock = getMockEndpoint("mock:remove-if-failure");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedHeaderReceived(JCacheConstants.RESULT, false);
        mock.assertIsSatisfied();

        assertTrue(cache.containsKey(key));
    }

    @Test
    public void testRemoveAll() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        Map<Object, Object> values = generateRandomMap(2);
        cache.putAll(values);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REMOVEALL");
        sendBody("direct:remove-all", null, headers);

        for (Object key : values.keySet()) {
            assertFalse(cache.containsKey(key));
        }
    }

    @Test
    public void testRemoveSubset() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        Map<Object, Object> values1 = generateRandomMap(4);
        Map<Object, Object> values2 = generateRandomMap(2);

        cache.putAll(values1);
        cache.putAll(values2);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REMOVEALL");
        headers.put(JCacheConstants.KEYS, values2.keySet());
        sendBody("direct:remove-subset", null, headers);

        for (Object key : values1.keySet()) {
            assertTrue(cache.containsKey(key));
        }

        for (Object key : values2.keySet()) {
            assertFalse(cache.containsKey(key));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:remove")
                    .to("jcache://test-cache")
                        .to("mock:remove");
                from("direct:remove-if")
                    .to("jcache://test-cache")
                        .to("mock:remove-if");
                from("direct:remove-if-failure")
                    .to("jcache://test-cache")
                        .to("mock:remove-if-failure");
                from("direct:remove-all")
                    .to("jcache://test-cache");
                from("direct:remove-subset")
                    .to("jcache://test-cache");
            }
        };
    }
}
