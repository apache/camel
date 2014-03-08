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
import java.net.URL;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.Configuration;

/**
 * A utility class for ehcache
 */
final class EHCacheUtil {

    private static boolean useCreateNewMethod;
    
    static {
        // to support ehcache's version range given in camel-cache (e.g., ehcache 2.5.1, 2.7.2, etc),
        // if method newInstance is found, use the newInstance methods; otherwise use the old create methods.
        // no reflection used for the actual call as the code is compiled against the newer ehcache.
        try {
            CacheManager.class.getMethod("newInstance", (Class<?>[])null);
            useCreateNewMethod = true;

        } catch (NoSuchMethodException e) {
            // ignore
        }
    }
    
    private EHCacheUtil() {
        // 
    }
    
    static CacheManager createCacheManager() throws CacheException {
        if (useCreateNewMethod) {
            return CacheManager.newInstance();
        } else {
            return CacheManager.create();
        }
    }
    
    static CacheManager createCacheManager(String configurationFileName) throws CacheException {
        if (useCreateNewMethod) {
            return CacheManager.newInstance(configurationFileName);
        } else {
            return CacheManager.create(configurationFileName);
        }
    }

    static CacheManager createCacheManager(URL configurationFileURL) throws CacheException {
        if (useCreateNewMethod) {
            return CacheManager.newInstance(configurationFileURL);
        } else {
            return CacheManager.create(configurationFileURL);
        }
    }
    
    static CacheManager createCacheManager(InputStream inputStream) throws CacheException {
        if (useCreateNewMethod) {
            return CacheManager.newInstance(inputStream);
        } else {
            return CacheManager.create(inputStream);
        }
    }
    
    static CacheManager createCacheManager(Configuration conf) throws CacheException {
        if (useCreateNewMethod) {
            return CacheManager.newInstance(conf);
        } else {
            return CacheManager.create(conf);
        }
    }

}
