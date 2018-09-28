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
package org.apache.camel.component.bean;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUCacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/bean.html">Bean Component</a> is for invoking Java beans from Camel.
 */
public class BeanComponent extends DefaultComponent {

    // use an internal soft cache for BeanInfo as they are costly to introspect
    // for example the bean language using OGNL expression runs much faster reusing the BeanInfo from this cache
    @SuppressWarnings("unchecked")
    private final Map<BeanInfoCacheKey, BeanInfo> beanInfoCache = LRUCacheFactory.newLRUSoftCache(1000);

    @Metadata(label = "advanced", description = "If enabled, Camel will cache the result of the first Registry look-up."
        + " Cache can be enabled if the bean in the Registry is defined as a singleton scope.")
    private Boolean cache;

    public BeanComponent() {
    }
    
    // Implementation methods
    //-----------------------------------------------------------------------
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanEndpoint endpoint = new BeanEndpoint(uri, this);
        endpoint.setBeanName(remaining);
        endpoint.setCache(cache);
        setProperties(endpoint, parameters);

        // the bean.xxx options is for the bean
        Map<String, Object> options = IntrospectionSupport.extractProperties(parameters, "bean.");
        endpoint.setParameters(options);
        return endpoint;
    }
    
    BeanInfo getBeanInfoFromCache(BeanInfoCacheKey key) {
        return beanInfoCache.get(key);
    }

    void addBeanInfoToCache(BeanInfoCacheKey key, BeanInfo beanInfo) {
        beanInfoCache.put(key, beanInfo);
    }

    @Override
    protected void doShutdown() throws Exception {
        if (log.isDebugEnabled() && beanInfoCache instanceof LRUCache) {
            LRUCache cache = (LRUCache) this.beanInfoCache;
            log.debug("Clearing BeanInfo cache[size={}, hits={}, misses={}, evicted={}]", cache.size(), cache.getHits(), cache.getMisses(), cache.getEvicted());
        }
        beanInfoCache.clear();
    }

    public Boolean getCache() {
        return cache;
    }

    /**
     * If enabled, Camel will cache the result of the first Registry look-up.
     * Cache can be enabled if the bean in the Registry is defined as a singleton scope.
     */
    public void setCache(Boolean cache) {
        this.cache = cache;
    }
}
