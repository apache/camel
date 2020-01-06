/*
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
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.util.PropertiesHelper;

/**
 * The Bean component is for invoking Java beans from Camel.
 */
@org.apache.camel.spi.annotations.Component("bean")
public class BeanComponent extends DefaultComponent {

    // use an internal soft cache for BeanInfo as they are costly to introspect
    // for example the bean language using OGNL expression runs much faster reusing the BeanInfo from this cache
    @SuppressWarnings("unchecked")
    private final Map<BeanInfoCacheKey, BeanInfo> beanInfoCache = LRUCacheFactory.newLRUSoftCache(1000);

    @Deprecated
    @Metadata(defaultValue = "true", description = "Use singleton option instead.")
    private Boolean cache;
    @Metadata(defaultValue = "true", description = "Whether to use singleton scoped beans. If enabled then the bean"
            + " is created or looked up once and reused (the bean should be thread-safe). Setting this to false will let Camel create/lookup"
            + " a new bean instance, per use; which acts as prototype scoped. However beware that if you lookup the bean, then the registry that holds the bean, would return "
            + " a bean accordingly to its configuration, which can be singleton or prototype scoped. For example if you use Spring, or CDI, which"
            + " has their own settings for setting bean scopes.")
    private Boolean singleton = Boolean.TRUE;

    public BeanComponent() {
    }
    
    // Implementation methods
    //-----------------------------------------------------------------------
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanEndpoint endpoint = new BeanEndpoint(uri, this);
        endpoint.setBeanName(remaining);
        if (cache != null) {
            endpoint.setCache(cache);
        }
        endpoint.setSingleton(singleton);
        setProperties(endpoint, parameters);

        // the bean.xxx options is for the bean
        Map<String, Object> options = PropertiesHelper.extractProperties(parameters, "bean.");
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

    @Deprecated
    public Boolean getCache() {
        return singleton;
    }

    @Deprecated
    public void setCache(Boolean cache) {
        this.singleton = cache;
    }

    public Boolean getSingleton() {
        return singleton;
    }

    public void setSingleton(Boolean singleton) {
        this.singleton = singleton;
    }
}
