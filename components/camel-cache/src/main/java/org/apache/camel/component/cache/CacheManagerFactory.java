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

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CacheManagerFactory extends ServiceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CacheManagerFactory.class);
    private CacheManager cacheManager;

    public synchronized CacheManager getInstance() {
        if (cacheManager == null) {
            cacheManager = createCacheManagerInstance();

            // always turn off ET phone-home
            LOG.debug("Turning off EHCache update checker ...");
            Configuration config = cacheManager.getConfiguration();
            try {
                // need to set both the system property and bypass the setUpdateCheck method as that can be changed dynamically
                System.setProperty("net.sf.ehcache.skipUpdateCheck", "true");
                ReflectionHelper.setField(config.getClass().getDeclaredField("updateCheck"), config, false);

                LOG.info("Turned off EHCache update checker. updateCheck={}", config.getUpdateCheck());
            } catch (Throwable e) {
                // ignore
                LOG.warn("Error turning off EHCache update checker. Beware information sent over the internet!", e);
            }
        }

        return cacheManager;
    }

    /**
     * Creates {@link CacheManager}.
     * <p/>
     * The default implementation is {@link DefaultCacheManagerFactory}.
     *
     * @return {@link CacheManager}
     */
    protected abstract CacheManager createCacheManagerInstance();

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected synchronized void doStop() throws Exception {
        // only shutdown cache manager if no longer in use
        // (it may be reused when running in app servers like Karaf)
        if (cacheManager != null) {
            int size = cacheManager.getCacheNames().length;
            if (size <= 0) {
                LOG.info("Shutting down CacheManager as its no longer in use");
                cacheManager.shutdown();
                cacheManager = null;
            } else {
                LOG.info("Cannot stop CacheManager as its still in use by {} clients", size);
            }
        }
    }
}
