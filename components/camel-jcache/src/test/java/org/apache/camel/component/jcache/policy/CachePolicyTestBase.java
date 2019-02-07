package org.apache.camel.component.jcache.policy;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Before;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.util.UUID;

public class CachePolicyTestBase extends CamelTestSupport {

    @Before
    public void before(){
        //reset mock
        MockEndpoint mock = getMockEndpoint("mock:value");
        mock.reset();
        mock.whenAnyExchangeReceived((e)->
                e.getMessage().setBody(generateValue(e.getMessage().getBody(String.class))));
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

    @After
    public void after(){
        //The RouteBuilder code is called for every test, so we destroy cache after each test
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        cacheManager.getCacheNames().forEach((s)->cacheManager.destroyCache(s));
    }
}
