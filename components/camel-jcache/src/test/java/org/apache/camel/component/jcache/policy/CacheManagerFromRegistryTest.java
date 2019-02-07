package org.apache.camel.component.jcache.policy;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.net.URI;

//This test requires a registered CacheManager, but the others do not.
public class CacheManagerFromRegistryTest extends CachePolicyTestBase {

    //Register cacheManager in CamelContext. Set cacheName
    @Test
    public void testCacheManagerFromContext() throws Exception {
        final String key  = randomString();

        //Send exchange
        Object responseBody = this.template().requestBody("direct:policy-context-manager", key);

        //Verify the cacheManager "hzsecond" registered in the CamelContext was used
        assertNull(lookupCache("contextCacheManager"));
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager(URI.create("hzsecond"),null);
        Cache cache = cacheManager.getCache("contextCacheManager");

        assertEquals(generateValue(key),cache.get(key));
        assertEquals(generateValue(key),responseBody);
        assertEquals(1,getMockEndpoint("mock:value").getExchanges().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                //Use the cacheManager registered in CamelContext. See createRegistry(). Set cacheName
                //During the test JndiRegistry is used, so we add the cacheManager to JNDI. In Spring context a bean works.
                CachePolicy cachePolicy = new CachePolicy();
                cachePolicy.setCacheName("contextCacheManager");

                from("direct:policy-context-manager")
                        .policy(cachePolicy)
                        .to("mock:value");
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        registry.bind("cachemanager-hzsecond",Caching.getCachingProvider().getCacheManager(URI.create("hzsecond"),null));

        return registry;
    }

}
