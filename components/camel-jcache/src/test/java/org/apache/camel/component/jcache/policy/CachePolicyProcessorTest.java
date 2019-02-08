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
package org.apache.camel.component.jcache.policy;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.simple.types.SimpleIllegalSyntaxException;
import org.junit.Test;

public class CachePolicyProcessorTest extends CachePolicyTestBase {

    //Basic test to verify value gets cached and route is not executed for the second time
    @Test
    public void testValueGetsCached() throws Exception {
        final String key = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");
        Cache cache = lookupCache("simple");

        //Send first, key is not in cache
        Object responseBody = this.template().requestBody("direct:cached-simple", key);

        //We got back the value, mock was called once, value got cached.
        assertEquals(generateValue(key), cache.get(key));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, mock.getExchanges().size());

        //Send again, key is already in cache
        responseBody = this.template().requestBody("direct:cached-simple", key);

        //We got back the stored value, but the mock was not called again
        assertEquals(generateValue(key), cache.get(key));
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, mock.getExchanges().size());

    }

    //Cache is closed
    @Test
    public void testClosedCache() throws Exception {
        final String key = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");

        //Send first, key is not in cache
        Object responseBody = this.template().requestBody("direct:cached-closed", key);

        //We got back the value, mock was called once
        assertEquals(generateValue(key), responseBody);
        assertEquals(1, mock.getExchanges().size());

        //Send again, cache is closed
        responseBody = this.template().requestBody("direct:cached-closed", key);

        //We got back the stored value, mock was called again
        assertEquals(generateValue(key), responseBody);
        assertEquals(2, mock.getExchanges().size());

    }

    //Key is already stored
    @Test
    public void testValueWasCached() throws Exception {
        final String key = randomString();
        final String value = "test";
        MockEndpoint mock = getMockEndpoint("mock:value");

        //Prestore value in cache
        Cache cache = lookupCache("simple");
        cache.put(key, value);

        //Send first, key is already in cache
        Object responseBody = this.template().requestBody("direct:cached-simple", key);

        //We got back the value, mock was not called, cache was not modified
        assertEquals(value, cache.get(key));
        assertEquals(value, responseBody);
        assertEquals(0, mock.getExchanges().size());
    }

    //Null final body
    @Test
    public void testNullResult() throws Exception {
        final String key = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");
        mock.whenAnyExchangeReceived((e) -> e.getMessage().setBody(null));

        //Send first
        this.template().requestBody("direct:cached-simple", key);

        assertEquals(1, mock.getExchanges().size());

        //Send again, nothing was cached
        this.template().requestBody("direct:cached-simple", key);
        assertEquals(2, mock.getExchanges().size());

    }

    //Use a key expression ${header.mykey}
    @Test
    public void testKeyExpression() throws Exception {
        final String key = randomString();
        final String body = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");
        Cache cache = lookupCache("simple");

        //Send first, key is not in cache
        Object responseBody = this.template().requestBodyAndHeader("direct:cached-byheader", body, "mykey", key);

        //We got back the value, mock was called once, value got cached.
        assertEquals(generateValue(body), cache.get(key));
        assertEquals(generateValue(body), responseBody);
        assertEquals(1, mock.getExchanges().size());

        //Send again, use another body, but the same key
        responseBody = this.template().requestBodyAndHeader("direct:cached-byheader", randomString(), "mykey", key);

        //We got back the stored value, and the mock was not called again
        assertEquals(generateValue(body), cache.get(key));
        assertEquals(generateValue(body), responseBody);
        assertEquals(1, mock.getExchanges().size());

    }

    //Use an invalid key expression causing an exception
    @Test
    public void testInvalidKeyExpression() throws Exception {
        final String body = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");
        Cache cache = lookupCache("simple");

        //Send
        Exchange response = this.template().request("direct:cached-invalidkey",
            (e) -> e.getMessage().setBody(body));

        //Exception is on the exchange, cache is empty
        assertIsInstanceOf(SimpleIllegalSyntaxException.class, response.getException().getCause());
        assertEquals(0, mock.getExchanges().size());
        assertFalse(cache.iterator().hasNext());

    }

    //Value is cached after handled exception
    @Test
    public void testHandledException() throws Exception {
        final String key = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");
        Cache cache = lookupCache("simple");

        //Send first, key is not in cache
        Object responseBody = this.template().requestBody("direct:cached-exception", key);

        //We got back the value after exception handler, mock was called once, value got cached.
        assertEquals("handled-" + generateValue(key), cache.get(key));
        assertEquals("handled-" + generateValue(key), responseBody);
        assertEquals(1, mock.getExchanges().size());

    }

    //Nothing is cached after an unhandled exception
    @Test
    public void testException() throws Exception {
        final String key = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");
        mock.whenAnyExchangeReceived((e) -> {
            throw new RuntimeException("unexpected");
        });
        Cache cache = lookupCache("simple");

        //Send
        Exchange response = this.template().request("direct:cached-exception",
            (e) -> e.getMessage().setBody(key));

        //Exception is on the exchange, cache is empty
        assertEquals("unexpected", response.getException().getMessage());
        assertEquals(1, mock.getExchanges().size());
        assertFalse(cache.iterator().hasNext());

    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();

                //Simple cache - with default config
                Cache cache = cacheManager.createCache("simple", new MutableConfiguration<>());
                CachePolicy cachePolicy = new CachePolicy();
                cachePolicy.setCache(cache);

                from("direct:cached-simple")
                    .policy(cachePolicy)
                    .to("mock:value");

                //Cache after exception handling
                from("direct:cached-exception")
                    .onException(Exception.class)
                    .onWhen(exceptionMessage().isEqualTo("test"))
                    .handled(true)
                    .setBody(simple("handled-${body}"))
                    .end()

                    .policy(cachePolicy)
                    .to("mock:value")
                    .throwException(new Exception("test"));

                //Closed cache
                cache = cacheManager.createCache("closed", new MutableConfiguration<>());
                cache.close();
                cachePolicy = new CachePolicy();
                cachePolicy.setCache(cache);

                from("direct:cached-closed")
                    .policy(cachePolicy)
                    .to("mock:value");


                //Use ${header.mykey} as the key
                cachePolicy = new CachePolicy();
                cachePolicy.setCache(cacheManager.getCache("simple"));
                cachePolicy.setKeyExpression(simple("${header.mykey}"));

                from("direct:cached-byheader")
                    .policy(cachePolicy)
                    .to("mock:value");

                //Use an invalid keyExpression
                cachePolicy = new CachePolicy();
                cachePolicy.setCache(cacheManager.getCache("simple"));
                cachePolicy.setKeyExpression(simple("${unexpected}"));

                from("direct:cached-invalidkey")
                    .policy(cachePolicy)
                    .to("mock:value");
            }
        };
    }
}
