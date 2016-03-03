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
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import javax.cache.processor.MutableEntry;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JCacheProducerInvokeTest extends JCacheComponentTestSupport {
    private static final EntryProcessor<Object, Object, Object> ENTRY_PROCESSOR = new EntryProcessor<Object, Object, Object>() {
        @Override
        public Object process(MutableEntry<Object, Object> entry, Object... arguments) throws EntryProcessorException {
            return "processor-" + entry.getValue();
        }
    };

    @Test
    public void testInvoke() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final String key = randomString();
        final String val = randomString();

        cache.put(key, val);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "INVOKE");
        headers.put(JCacheConstants.KEY, key);
        headers.put(JCacheConstants.ENTRY_PROCESSOR, ENTRY_PROCESSOR);

        sendBody("direct:invoke", null, headers);

        MockEndpoint mock = getMockEndpoint("mock:invoke");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEY, key);
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getBody(String.class).equals("processor-" + val);
            }
        });

        mock.assertIsSatisfied();

        assertTrue(cache.containsKey(key));
        assertEquals(val, cache.get(key));
    }

    @Test
    public void testInvokeAll() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        final Cache<Object, Object> cache = getCacheFromEndpoint("jcache://test-cache");

        final Map<Object, Object> values1 = generateRandomMap(4);
        final Map<Object, Object> values2 = generateRandomMap(2);

        cache.putAll(values1);
        cache.putAll(values2);

        headers.clear();
        headers.put(JCacheConstants.ACTION, "INVOKE");
        headers.put(JCacheConstants.KEYS, values2.keySet());
        headers.put(JCacheConstants.ENTRY_PROCESSOR, ENTRY_PROCESSOR);

        sendBody("direct:invoke-all", null, headers);

        MockEndpoint mock = getMockEndpoint("mock:invoke-all");
        mock.expectedMinimumMessageCount(1);
        mock.expectedHeaderReceived(JCacheConstants.KEYS, values2.keySet());
        mock.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Map<Object, EntryProcessorResult<Object>> map = exchange.getIn().getBody(Map.class);
                for (Map.Entry<Object, Object> entry : values2.entrySet()) {
                    assertTrue(map.containsKey(entry.getKey()));
                    assertEquals("processor-" + entry.getValue(), map.get(entry.getKey()).get());
                }

                return true;
            }
        });

        mock.assertIsSatisfied();

        for (Map.Entry<Object, Object> entry : values1.entrySet()) {
            assertTrue(cache.containsKey(entry.getKey()));
            assertEquals(entry.getValue(), cache.get(entry.getKey()));
        }

        for (Map.Entry<Object, Object> entry : values2.entrySet()) {
            assertTrue(cache.containsKey(entry.getKey()));
            assertEquals(entry.getValue(), cache.get(entry.getKey()));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:invoke")
                    .to("jcache://test-cache")
                        .to("mock:invoke");
                from("direct:invoke-all")
                    .to("jcache://test-cache")
                        .to("mock:invoke-all");
            }
        };
    }
}
