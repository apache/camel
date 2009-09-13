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
    CacheManager cacheManager;
    Ehcache cache;
    private String cacheName;
    private String key;
    

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
