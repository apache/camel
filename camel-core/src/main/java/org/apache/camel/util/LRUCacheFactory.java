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
package org.apache.camel.util;

import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory to create {@link LRUCache} instances.
 */
public final class LRUCacheFactory {

    // TODO: use LRUCacheFactory in other places to create the LRUCaches

    private static final Logger LOG = LoggerFactory.getLogger(LRUCacheFactory.class);

    private LRUCacheFactory() {
    }

    @SuppressWarnings("unchecked")
    public static void warmUp() {
        // create a dummy map in a separate thread to warmup the Caffeine cache
        // as we want to do this as early as possible while creating CamelContext
        // so when Camel is starting up its faster as the Caffeine cache has been initialized
        Runnable warmup = () -> {
            LOG.debug("Warming up LRUCache ...");
            newLRUCache(16);
            LOG.debug("Warming up LRUCache complete");
        };

        String threadName = ThreadHelper.resolveThreadName(null, "LRUCacheFactory");

        Thread thread = new Thread(warmup, threadName);
        thread.start();
    }

    public static LRUCache newLRUCache(int maximumCacheSize) {
        return new LRUCache(maximumCacheSize);
    }

    public static LRUWeakCache newLRUWeakCache(int maximumCacheSize) {
        return new LRUWeakCache(maximumCacheSize);
    }
}
