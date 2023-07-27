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

import org.apache.camel.BeanScope;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The bean component is for invoking Java beans from Camel.
 */
@org.apache.camel.spi.annotations.Component("bean")
public class BeanComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(BeanComponent.class);

    // use an internal soft cache for BeanInfo as they are costly to introspect
    // for example the bean language using OGNL expression runs much faster reusing the BeanInfo from this cache
    private final Map<BeanInfoCacheKey, BeanInfo> beanInfoCache = LRUCacheFactory.newLRUSoftCache(1000);

    @Metadata(defaultValue = "Singleton", description = "Scope of bean."
                                                        + " When using singleton scope (default) the bean is created or looked up only once and reused for the lifetime of the endpoint."
                                                        + " The bean should be thread-safe in case concurrent threads is calling the bean at the same time."
                                                        + " When using request scope the bean is created or looked up once per request (exchange). This can be used if you want to store state on a bean"
                                                        + " while processing a request and you want to call the same bean instance multiple times while processing the request."
                                                        + " The bean does not have to be thread-safe as the instance is only called from the same request."
                                                        + " When using delegate scope, then the bean will be looked up or created per call. However in case of lookup then this is delegated "
                                                        + " to the bean registry such as Spring or CDI (if in use), which depends on their configuration can act as either singleton or prototype scope."
                                                        + " so when using prototype then this depends on the delegated registry.")
    private BeanScope scope = BeanScope.Singleton;

    public BeanComponent() {
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanEndpoint endpoint = new BeanEndpoint(uri, this);
        endpoint.setBeanName(remaining);
        endpoint.setScope(scope);
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
        if (LOG.isDebugEnabled() && beanInfoCache instanceof LRUCache<BeanInfoCacheKey, BeanInfo> cache) {
            LOG.debug("Clearing BeanInfo cache[size={}, hits={}, misses={}, evicted={}]", cache.size(), cache.getHits(),
                    cache.getMisses(), cache.getEvicted());
        }
        beanInfoCache.clear();
    }

    public BeanScope getScope() {
        return scope;
    }

    public void setScope(BeanScope scope) {
        this.scope = scope;
    }
}
