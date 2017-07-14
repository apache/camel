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
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.component.cache.CacheConstants;
import org.apache.camel.component.cache.DefaultCacheManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheBasedMessageBodyReplacer extends CacheValidate implements Processor, Service {
    private static final Logger LOG = LoggerFactory.getLogger(CacheBasedMessageBodyReplacer.class);
    private CacheManager cacheManager;
    private String cacheName;
    private Expression key;

    public CacheBasedMessageBodyReplacer(String cacheName, String key) {
        this(cacheName, ExpressionBuilder.constantExpression(key));
    }

    public CacheBasedMessageBodyReplacer(String cacheName, Expression key) {
        if (cacheName.contains("cache://")) {
            this.setCacheName(cacheName.replace("cache://", ""));
        } else {
            this.setCacheName(cacheName);
        }
        this.setKey(key);
    }

    public void process(Exchange exchange) throws Exception {
        String cacheKey = key.evaluate(exchange, String.class);

        if (isValid(cacheManager, cacheName, cacheKey)) {
            Ehcache cache = cacheManager.getCache(cacheName);
            LOG.debug("Replacing Message Body from CacheName {} for key {}", cacheName, cacheKey);
            exchange.getIn().setHeader(CacheConstants.CACHE_KEY, cacheKey);
            exchange.getIn().setBody(cache.get(cacheKey).getObjectValue());
        }
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public Expression getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = ExpressionBuilder.constantExpression(key);
    }

    public void setKey(Expression key) {
        this.key = key;
    }

    @Override
    public void start() throws Exception {
        // Cache the buffer to the specified Cache against the specified key
        if (cacheManager == null) {
            cacheManager = new DefaultCacheManagerFactory().getInstance();
        }
    }

    @Override
    public void stop() throws Exception {
        // noop
    }
}
