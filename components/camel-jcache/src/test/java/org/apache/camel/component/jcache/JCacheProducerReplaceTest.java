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

public class JCacheProducerReplaceTest extends JCacheComponentTestSupport {
    @Test
    public void testReplace() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key  = randomString();
        final String val  = randomString();
        final String val1 = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REPLACE");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:replace", val1, headers);

        MockEndpoint mock = getMockEndpoint("mock:replace");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedHeaderReceived(JCacheConstants.RESULT, true);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                assertNotNull("body", exchange.getIn().getBody());
                return exchange.getIn().getBody().equals(val1);
            }
        });

        mock.assertIsSatisfied();

        assertEquals(val1, cache.get(key));
    }

    @Test
    public void testReplaceIf() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key  = randomString();
        final String val  = randomString();
        final String val1 = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REPLACE");
        headers.put(JCacheConstants.KEY, key);
        headers.put(JCacheConstants.OLD_VALUE, val);
        sendBody("direct:replace", val1, headers);

        MockEndpoint mock = getMockEndpoint("mock:replace");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedHeaderReceived(JCacheConstants.RESULT, true);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                assertNotNull("body", exchange.getIn().getBody());
                return exchange.getIn().getBody().equals(val1);
            }
        });

        mock.assertIsSatisfied();

        assertEquals(val1, cache.get(key));
    }

    @Test
    public void testReplaceIfFailure() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key  = randomString();
        final String val  = randomString();
        final String val1 = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REPLACE");
        headers.put(JCacheConstants.KEY, key);
        headers.put(JCacheConstants.OLD_VALUE, val1);
        sendBody("direct:replace", val1, headers);

        MockEndpoint mock = getMockEndpoint("mock:replace");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedHeaderReceived(JCacheConstants.RESULT, false);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                assertNotNull("body", exchange.getIn().getBody());
                return exchange.getIn().getBody().equals(val1);
            }
        });

        mock.assertIsSatisfied();

        assertEquals(val, cache.get(key));
    }

    @Test
    public void testReplaceFail() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        assertFalse(cache.containsKey(key));

        headers.clear();
        headers.put(JCacheConstants.ACTION, "REPLACE");
        headers.put(JCacheConstants.KEY, key);
        sendBody("direct:replace-fail", val, headers);

        MockEndpoint mock = getMockEndpoint("mock:replace-fail");
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

        assertFalse(cache.containsKey(key));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:replace")
                    .to("jcache://test-cache")
                        .to("mock:replace");
                from("direct:replace-fail")
                    .to("jcache://test-cache")
                        .to("mock:replace-fail");
            }
        };
    }
}
