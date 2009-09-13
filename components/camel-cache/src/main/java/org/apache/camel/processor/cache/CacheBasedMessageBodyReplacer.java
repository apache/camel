package org.apache.camel.processor.cache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cache.factory.CacheManagerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CacheBasedMessageBodyReplacer extends CacheValidate implements Processor {
    private static final transient Log LOG = LogFactory.getLog(CacheBasedMessageBodyReplacer.class);
	private String cacheName;
	private String key;
    CacheManager cacheManager;
    Ehcache cache;

	public CacheBasedMessageBodyReplacer(String cacheName, String key) {
		super();
		if (cacheName.contains("cache://")) {
		    this.setCacheName(cacheName.replace("cache://", ""));
		} else {
		    this.setCacheName(cacheName);
		}
		this.setKey(key);
	}


	public void process(Exchange exchange) throws Exception {
        // Cache the buffer to the specified Cache against the specified key 
        cacheManager = new CacheManagerFactory().instantiateCacheManager();

        if (isValid(cacheManager, cacheName, key)) {
            cache = cacheManager.getCache(cacheName);
            LOG.info("Replacing Message Body from CacheName " + cacheName + " for key " + key);
            exchange.getIn().setHeader("CACHE_KEY", key);
        	exchange.getIn().setBody(cache.get(key).getObjectValue());
        }
        
	}


	public String getCacheName() {
		return cacheName;
	}


	public void setCacheName(String cacheName) {
		this.cacheName = cacheName;
	}


	public String getKey() {
		return key;
	}


	public void setKey(String key) {
		this.key = key;
	}

	
}
