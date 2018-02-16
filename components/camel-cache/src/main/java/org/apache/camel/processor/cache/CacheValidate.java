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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheValidate {
    private static final Logger LOG = LoggerFactory.getLogger(CacheValidate.class);

    public boolean isValid(CacheManager cacheManager, String cacheName, String key) {
        LOG.trace("Cache Name: {}", cacheName);

        if (!cacheManager.cacheExists(cacheName)) {
            LOG.debug("No existing Cache found with name: {}"
                    + ". Please ensure a cache is first instantiated using a Cache Consumer or Cache Producer."
                    + " Replacement will not be performed since the cache {} does not presently exist", cacheName, cacheName);
            return false;
        }

        LOG.debug("Found an existing cache: {}", cacheName);

        if (LOG.isTraceEnabled()) {
            LOG.trace("Cache {} currently contains {} elements", cacheName, cacheManager.getCache(cacheName).getSize());
        }
        Ehcache cache = cacheManager.getCache(cacheName);

        if (!cache.isKeyInCache(key)) {
            LOG.debug("No Key with name: {} presently exists in the cache. It is also possible that the key may have expired in the cache."
                    + " Replacement will not be performed until an appropriate key/value pair is added to (or) found in the cache.", key);
            return false;
        }

        return true;
    }

}
