package org.apache.camel.component.jcache.policy;

import org.apache.camel.*;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import java.util.Set;

/**
 * Policy for routes. It caches the final body of a route and next time takes it from the cache instead of executing the route.
 * The cache key is determined by the keyExpression (message body by default). If there is an object in the cache under that key the rest of the route is not executed, but the cached object is added to the Exchange.
 *
 * Fields:
 * cache: JCache to use
 * cacheManager: If cache is not set, a new one is get or created using this cacheManager. If cacheManager is not set, we try to lookup one from CamelContext.
 * cacheName: Name of the cache to use or create. RouteId is used by default.
 * cacheConfiguration: CacheConfiguration used if a new cache is created. Using default MutableConfiguration if not set.
 * keyExpression: The Expression to generate the key for the cache. E.g simple("${header.username}")
 * enabled: If CachePolicy is not enabled, no policy is added to the route. Has an impact only during startup.
 */
public class CachePolicy implements Policy {
    private static final Logger log = LoggerFactory.getLogger(CachePolicy.class);

    private Cache cache;
    private CacheManager cacheManager;
    private String cacheName;
    private Configuration cacheConfiguration;
    private Expression keyExpression;
    private boolean enabled = true;

    @Override
    public void beforeWrap(RouteContext routeContext, NamedNode namedNode) {

    }

    @Override
    public Processor wrap(RouteContext routeContext, Processor processor) {
        //Don't add CachePolicyProcessor if CachePolicy is disabled. This means enable/disable has impact only during startup
        if ( !isEnabled() ) return processor;

        Cache cache = this.cache;
        if (cache == null) {
            //Create cache based on given configuration

            //Find CacheManager
            CacheManager cacheManager = this.cacheManager;

            //Lookup CacheManager from CamelContext if it's not set
            if (cacheManager == null) {
                Set<CacheManager> lookupResult = routeContext.getCamelContext().getRegistry().findByType(CacheManager.class);
                if (ObjectHelper.isNotEmpty(lookupResult)) {

                    //Use the first cache manager found
                    cacheManager = lookupResult.iterator().next();
                    log.debug("CacheManager from CamelContext registry: {}", cacheManager);
                }
            }

            //Lookup CacheManager the standard way
            if (cacheManager == null) {
                cacheManager = Caching.getCachingProvider().getCacheManager();
                log.debug("CacheManager from CachingProvider: {}",cacheManager);
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
                ", enabled=" + enabled +
                '}';
    }
}
