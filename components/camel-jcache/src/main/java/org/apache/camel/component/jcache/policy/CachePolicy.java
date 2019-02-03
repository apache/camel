package org.apache.camel.component.jcache.policy;

import org.apache.camel.*;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Policy for routes. It caches the "result" of a route and next time takes it from the cache instead of executing the route.
 * The cache key is determined by the keyExpression. If there is an object in the cache under that key the rest of the route is not executed, but the cached object is added to the Exchange.
 * By default the message body is cached as the "result" of a route.
 *
 * Fields:
 * cache: JCache to use
 * keyExpression: The Expression to generate the key for the cache. E.g simple("${header.username}")
 * valueExpression: The Expression to generate the value stored in the cache for the key. E.g exchangeProperty("orders")
 * addCachedObject: A function how to add the cached object to the Exchange if found. Practically this is executed instead of the route.
 */
public class CachePolicy implements Policy {
    private static final Logger log = LoggerFactory.getLogger(CachePolicy.class);

    private CacheManager cacheManager;
    private Configuration cacheConfiguration;
    private Cache cache;
    private String cacheName;
    private Expression keyExpression;
    private boolean enabled = true;
//    private Expression valueExpression;
//    private BiConsumer<Exchange, Object> addCachedObject;

    @Override
    public void beforeWrap(RouteContext routeContext, NamedNode namedNode) {

    }

    @Override
    public Processor wrap(RouteContext routeContext, Processor processor) {

//        Expression valueExpression = this.valueExpression != null ? this.valueExpression : org.apache.camel.builder.Builder.body();
//        Expression addCachedObject = this.addCachedObject != null ? this.addCachedObject : (e,o) -> e.getMessage().setBody(o) ;

//        Expression keyExpression = this.keyExpression != null ? this.keyExpression :

        //Don't add processor if cachePolicy is disabled. This means enable/disable has impact only during startup
        if ( !isEnabled() ) return null;

        Cache cache = this.cache;
        if (cache == null) {
            //Create cache based on given configuration

            //Lookup CacheManager from CamelContext if it's not set
            CacheManager cacheManager = this.cacheManager;
            if (cacheManager == null) {
                log.debug("Looking up CacheManager.");
                Set<CacheManager> lookupResult = routeContext.getCamelContext().getRegistry().findByType(CacheManager.class);
                if (ObjectHelper.isEmpty(lookupResult))
                    throw new IllegalStateException("No CacheManager was set or found.");
                //Use the first cache manager found
                cacheManager=lookupResult.iterator().next();
            }

            //Use routeId as cacheName if it's not set
            String cacheName = ObjectHelper.isNotEmpty(this.cacheName) ? this.cacheName : routeContext.getRoute().getId();
            log.debug("Getting cache:{}", cacheName);

            //Get cache or create a new one using the cacheConfiguration
            cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.debug("Create cache:{}", cacheName);
                cache = cacheManager.createCache(cacheName,
                        cacheConfiguration != null ? this.cacheConfiguration : (Configuration)new MutableConfiguration());
            }

        }

        //Create processor
        return new CachePolicyProcessor(cache, keyExpression, processor);


    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public Configuration getCacheConfiguration() {
        return cacheConfiguration;
    }

    public void setCacheConfiguration(Configuration cacheConfiguration) {
        this.cacheConfiguration = cacheConfiguration;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public Expression getKeyExpression() {
        return keyExpression;
    }

    public void setKeyExpression(Expression keyExpression) {
        this.keyExpression = keyExpression;
    }

//    public Expression getValueExpression() {
//        return valueExpression;
//    }
//
//    public void setValueExpression(Expression valueExpression) {
//        this.valueExpression = valueExpression;
//    }
//
//    public BiConsumer<Exchange, Object> getAddCachedObject() {
//        return addCachedObject;
//    }
//
//    public void setAddCachedObject(BiConsumer<Exchange, Object> addCachedObject) {
//        this.addCachedObject = addCachedObject;
//    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "CachePolicy{" +
                "keyExpression=" + keyExpression +
                '}';
    }
}
