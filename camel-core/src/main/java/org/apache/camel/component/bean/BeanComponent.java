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
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.LRUCacheFactory;
import org.apache.camel.util.LRUSoftCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/bean.html">Bean Component</a> is for invoking Java beans from Camel.
 */
public class BeanComponent extends UriEndpointComponent {

    private static final Logger LOG = LoggerFactory.getLogger(BeanComponent.class);
    // use an internal soft cache for BeanInfo as they are costly to introspect
    // for example the bean language using OGNL expression runs much faster reusing the BeanInfo from this cache
    @SuppressWarnings("unchecked")
    private final LRUSoftCache<BeanInfoCacheKey, BeanInfo> cache = LRUCacheFactory.newLRUSoftCache(1000);

    public BeanComponent() {
        super(BeanEndpoint.class);
    }
    
    public BeanComponent(Class<? extends Endpoint> endpointClass) {
        super(endpointClass);
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanEndpoint endpoint = new BeanEndpoint(uri, this);
        endpoint.setBeanName(remaining);
        setProperties(endpoint, parameters);

        // the bean.xxx options is for the bean
        Map<String, Object> options = IntrospectionSupport.extractProperties(parameters, "bean.");
        endpoint.setParameters(options);
        return endpoint;
    }
    
    BeanInfo getBeanInfoFromCache(BeanInfoCacheKey key) {
        return cache.get(key);
    }

    void addBeanInfoToCache(BeanInfoCacheKey key, BeanInfo beanInfo) {
        cache.put(key, beanInfo);
    }

    @Override
    protected void doShutdown() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Clearing BeanInfo cache[size={}, hits={}, misses={}, evicted={}]", new Object[]{cache.size(), cache.getHits(), cache.getMisses(), cache.getEvicted()});
        }
        cache.clear();
    }
}
