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

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.hamcrest.Matchers.is;

public class JCacheProducerGetTest extends JCacheComponentTestSupport {

    @Test
    public void testGet() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "GET");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:get", null, headers);

        MockEndpoint mock = getMockEndpoint("mock:get");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
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
    public void testGetAndRemove() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "GETANDREMOVE");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:get-and-remove", null, headers);

        MockEndpoint mock = getMockEndpoint("mock:get-and-remove");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                assertNotNull("body", exchange.getIn().getBody());
                return exchange.getIn().getBody().equals(val);
            }
        });

        mock.assertIsSatisfied();

        assertFalse(cache.containsKey(key));
    }


    @Test
    public void testGetAndReplace() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key  = randomString();
        final String val  = randomString();
        final String val2 = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "GETANDREPLACE");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:get-and-replace", val2, headers);

        MockEndpoint mock = getMockEndpoint("mock:get-and-replace");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                assertNotNull("body", exchange.getIn().getBody());
                return exchange.getIn().getBody().equals(val);
            }
        });

        mock.assertIsSatisfied();

        assertTrue(cache.containsKey(key));
        assertEquals(val2, cache.get(key));
    }

    @Test
    public void testGetAndPut() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key  = randomString();
        final String val  = randomString();
        final String val2 = randomString();

        headers.clear();
        headers.put(JCacheConstants.ACTION, "GETANDPUT");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:get-and-put", val2, headers);

        MockEndpoint mock = getMockEndpoint("mock:get-and-put");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody() == null;
            }
        });

        mock.assertIsSatisfied();

        assertTrue(cache.containsKey(key));
        assertEquals(val2, cache.get(key));
    }

    @Test
    public void testGetAll() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        Map<Object, Object> values = new HashMap<>();
        values.put(randomString(), randomString());
        values.put(randomString(), randomString());

        cache.putAll(values);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "GETALL");
        headers.put(JCacheConstants.KEYS, values.keySet());

        sendBody("direct:get-all", values, headers);

        MockEndpoint mock = getMockEndpoint("mock:get-all");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEYS, values.keySet());
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Map<Object, Object> values = exchange.getIn().getBody(Map.class);
                assertThat(values, is(values));

                return true;
            }
        });

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:get")
                    .to("jcache://test-cache")
                        .to("mock:get");
                from("direct:get-and-remove")
                    .to("jcache://test-cache")
                        .to("mock:get-and-remove");
                from("direct:get-and-replace")
                    .to("jcache://test-cache")
                        .to("mock:get-and-replace");
                from("direct:get-and-put")
                    .to("jcache://test-cache")
                        .to("mock:get-and-put");
                from("direct:get-all")
                    .to("jcache://test-cache")
                        .to("mock:get-all");
            }
        };
    }
}
