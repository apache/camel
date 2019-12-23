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
package org.apache.camel.component.jcache;

import java.util.HashMap;
import java.util.Map;

import javax.cache.Cache;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JCacheProducerPutTest extends JCacheComponentTestSupport {

    @Test
    public void testPut() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        headers.clear();
        headers.put(JCacheConstants.ACTION, "PUT");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:put", val, headers);

        assertTrue(cache.containsKey(key));
        assertEquals(val, cache.get(key));
    }

    @Test
    public void testPutWithDefaultAction() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache?action=PUT");

        final String key = randomString();
        final String val = randomString();

        headers.clear();
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:put-with-default-action", val, headers);

        assertTrue(cache.containsKey(key));
        assertEquals(val, cache.get(key));
    }

    @Test
    public void testPutIfAbsent() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        headers.clear();
        headers.put(JCacheConstants.ACTION, "PUTIFABSENT");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:put-if-absent", val, headers);

        assertTrue(cache.containsKey(key));
        assertEquals(val, cache.get(key));

        MockEndpoint mock = getMockEndpoint("mock:put-if-absent");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedHeaderReceived(JCacheConstants.RESULT, true);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                assertNotNull("body", exchange.getIn().getBody());
                return exchange.getIn().getBody().equals(val);
            }
        });

        mock.assertIsSatisfied();
    }

    @Test
    public void testPutIfAbsentFailure() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "PUTIFABSENT");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:put-if-absent", val, headers);

        MockEndpoint mock = getMockEndpoint("mock:put-if-absent");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedHeaderReceived(JCacheConstants.RESULT, false);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                assertNotNull("body", exchange.getIn().getBody());
                return exchange.getIn().getBody().equals(val);
            }
        });

        mock.assertIsSatisfied();
    }

    @Test
    public void testPutAll() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        Map<Object, Object> values = generateRandomMap(2);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "PUTALL");
        headers.put(JCacheConstants.KEY, values);

        sendBody("direct:put", values, headers);

        for (Map.Entry<Object, Object> entry : values.entrySet()) {
            assertTrue(cache.containsKey(entry.getKey()));
            assertEquals(entry.getValue(), cache.get(entry.getKey()));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:put")
                    .to("jcache://test-cache");
                from("direct:put-with-default-action")
                    .to("jcache://test-cache?action=PUT");
                from("direct:put-if-absent")
                    .to("jcache://test-cache")
                        .to("mock:put-if-absent");
                from("direct:put-all")
                    .to("jcache://test-cache");
            }
        };
    }
}
