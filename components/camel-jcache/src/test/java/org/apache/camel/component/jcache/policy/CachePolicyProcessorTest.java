package org.apache.camel.component.jcache.policy;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jcache.JCacheConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CachePolicyProcessorTest extends CamelTestSupport {
    private static final Logger log = LoggerFactory.getLogger(CachePolicyProcessorTest.class);

    @Before
    public void before(){
        //reset mock
        MockEndpoint mock = getMockEndpoint("mock:value");
        mock.reset();
        mock.whenAnyExchangeReceived((e)->
                e.getMessage().setBody(generateValue(e.getMessage().getBody(String.class))));

        //clear cache
        Cache cache = lookupCache("simple");
        cache.clear();
    }

    //Basic test to verify value gets cached and route is not executed for the second time
    @Test
    public void testValueGetsCached() throws Exception {
        final String key  = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");
        Cache cache = lookupCache("simple");

        //Send first, key is not in cache
        Object responseBody = this.template().requestBody("direct:cached-simple", key);

        //We got back the value, mock was called once, value got cached.
        assertEquals(generateValue(key),cache.get(key));
        assertEquals(generateValue(key),responseBody);
        assertEquals(1,mock.getExchanges().size());

        //Send again, key is already in cache
        responseBody = this.template().requestBody("direct:cached-simple", key);

        //We got back the stored value, but the mock was not called again
        assertEquals(generateValue(key),cache.get(key));
        assertEquals(generateValue(key),responseBody);
        assertEquals(1,mock.getExchanges().size());

    }

    //Cache is closed
    @Test
    public void testClosedCache() throws Exception {
        final String key  = randomString();
        MockEndpoint mock = getMockEndpoint("mock:value");

        //Send first, key is not in cache
        Object responseBody = this.template().requestBody("direct:cached-closed", key);

        //We got back the value, mock was called once
        assertEquals(generateValue(key),responseBody);
        assertEquals(1,mock.getExchanges().size());

        //Send again, cache is closed
        responseBody = this.template().requestBody("direct:cached-closed", key);

        //We got back the stored value, mock was called again
        assertEquals(generateValue(key),responseBody);
        assertEquals(2,mock.getExchanges().size());

    }

    //Key is already stored
    @Test
    public void testValueWasCached() throws Exception {
        final String key  = randomString();
        final String value = "test";
        MockEndpoint mock = getMockEndpoint("mock:value");

        //Prestore value in cache
        Cache cache = lookupCache("simple");
        cache.put(key, value);

        //Send first, key is already in cache
        Object responseBody = this.template().requestBody("direct:cached-simple", key);

        //We got back the value, mock was not called, cache was not modified
        assertEquals(value,cache.get(key));
        assertEquals(value,responseBody);
        assertEquals(0,mock.getExchanges().size());
    }

    //Null final body

    //Key expression

    //Key expression exception

    //Async route - callback is called
    //Async route with exception - callback is called


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                CachingProvider cachingProvider = Caching.getCachingProvider();
                CacheManager cacheManager = cachingProvider.getCacheManager();

                //Simple cache - with default config
                Cache cache =cacheManager.createCache("simple",new MutableConfiguration<>());
                CachePolicy cachePolicy = new CachePolicy();
                cachePolicy.setCache(cache);

                from("direct:cached-simple")
                    .policy(cachePolicy)
                    .to("mock:value");


                //Closed cache
                cache =cacheManager.createCache("closed",new MutableConfiguration<>());
                cache.close();
                cachePolicy = new CachePolicy();
                cachePolicy.setCache(cache);


                from("direct:cached-closed")
                    .policy(cachePolicy)
                    .to("mock:value");

            }
        };
    }

    protected String randomString() {
        return UUID.randomUUID().toString();
    }

    protected Cache lookupCache(String cacheName) {
        //This will also open a closed cache
        return Caching.getCachingProvider().getCacheManager().getCache(cacheName);
    }
    public static String generateValue(String key){
        return "value-"+key;
    }
}
