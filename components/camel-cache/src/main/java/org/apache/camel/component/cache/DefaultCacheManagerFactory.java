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

import java.io.InputStream;

import net.sf.ehcache.CacheManager;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultCacheManagerFactory extends CacheManagerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCacheManagerFactory.class);

    private InputStream is;

    private String configurationFile;

    public DefaultCacheManagerFactory() {
        this(null, null);
    }

    public DefaultCacheManagerFactory(InputStream is, String configurationFile) {
        this.is = is;
        this.configurationFile = configurationFile;
    }

    @Override
    protected CacheManager createCacheManagerInstance() {
        if (is == null) {
            // it will still look for "/ehcache.xml" before defaulting to "/ehcache-failsafe.xml"
            LOG.info("Creating CacheManager using Ehcache defaults");
            return EHCacheUtil.createCacheManager();
        }
        LOG.info("Creating CacheManager using camel-cache configuration: {}", configurationFile);
        return EHCacheUtil.createCacheManager(is);
    }

    @Override
    protected void doStop() throws Exception {
        if (is != null) {
            IOHelper.close(is);
        }
        super.doStop();
    }
}
