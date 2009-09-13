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

package org.apache.camel.component.cache;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class CacheEventListener implements net.sf.ehcache.event.CacheEventListener {

    private static final transient Log LOG = LogFactory.getLog(CacheEventListener.class);
    CacheConsumer cacheConsumer;

    
    public CacheEventListener() {
        super();
    }

    public CacheEventListener(CacheConsumer cacheConsumer) {
        super();
        this.cacheConsumer = cacheConsumer;
    }

    public void notifyElementEvicted(Ehcache cache, Element element) {
        LOG.info("Element" + element.toString() + " is being evicted from cache " + cache.getName());
        
    }

    public void notifyElementExpired(Ehcache cache, Element element) {
        LOG.info("Element" + element.toString() + " has expired in cache " + cache.getName());        
    }

    public void notifyElementPut(Ehcache cache, Element element)
        throws CacheException {
        LOG.info("Element" + element.toString() + " has just been added/put in cache " + cache.getName());
        dispatchExchange(cache, element, "ADD");
    }

    public void notifyElementRemoved(Ehcache cache, Element element)
        throws CacheException {
        LOG.info("Element" + element.toString() + " has just been removed from cache " + cache.getName());
        dispatchExchange(cache, element, "DELETE");        
    }

    public void notifyElementUpdated(Ehcache cache, Element element)
        throws CacheException {
        LOG.info("Element" + element.toString() + " has just been updated in cache " + cache.getName());
        dispatchExchange(cache, element, "UPDATE");            
    }

    public void notifyRemoveAll(Ehcache cache) {
        LOG.info("Cache " + cache.getName() + " is being emptied and all elements removed");
        dispatchExchange(cache, null, "DELETEALL");
        
    }

    private void dispatchExchange(Ehcache cache, Element element, String operation) {
        Exchange exchange;
        
        LOG.info("Consumer Dispatching the Exchange containing the Element " + element.toString() + " in cache " + cache.getName());
        if (element == null) {
            exchange = cacheConsumer.getEndpoint().createCacheExchange(operation, "", "");
        } else {
            exchange = cacheConsumer.getEndpoint().createCacheExchange(operation, (String) element.getObjectKey(), element.getObjectValue());
        }
        try {
            cacheConsumer.getProcessor().process(exchange);
        } catch (Exception e) {
            throw new CacheException("Error in consumer while dispatching exchange containing Key " + (String) element.getObjectKey() + " for further processing  ", e);
        }
    }
    
    public CacheConsumer getCacheConsumer() {
        return cacheConsumer;
    }

    public void setCacheConsumer(CacheConsumer cacheConsumer) {
        this.cacheConsumer = cacheConsumer;
    }

    public void dispose() {
        
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
    
}
