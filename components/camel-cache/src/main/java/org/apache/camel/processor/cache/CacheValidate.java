package org.apache.camel.processor.cache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CacheValidate {
    private static final transient Log LOG = LogFactory.getLog(CacheValidate.class);

    public boolean isValid(CacheManager cacheManager, String cacheName, String key) {
        LOG.info("Cache Name: " + cacheName);
        if (!cacheManager.cacheExists(cacheName)) {
        	LOG.info("No existing Cache found with name: " + cacheName + ". Please ensure a cache is first instantiated using a Cache Consumer or Cache Producer");
        	LOG.info("Replacement will not be performed since the cache " + cacheName + "does not presently exist");
            return false;
        }
         
        LOG.info("Found an existing cache: " + cacheName);
        LOG.info("Cache " + cacheName + " currently contains " + cacheManager.getCache(cacheName).getSize() + " elements");
        Ehcache cache = cacheManager.getCache(cacheName);
        if (!cache.isKeyInCache(key)) {
        	LOG.info("No Key with name: " + key + "presently exists in the cache. It is also possible that the key may have expired in the cache");
         	LOG.info("Replacement will not be performed until an appropriate key/value pair is added to (or) found in the cache.");
         	return false;
         	
        }
        
		return true;
    }
}
