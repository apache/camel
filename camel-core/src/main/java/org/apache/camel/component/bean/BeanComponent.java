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
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.util.LRUSoftCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/bean.html">Bean Component</a>
 * will look up the URI in the {@link org.apache.camel.spi.Registry} and use that to handle message dispatching.
 *
 * @version 
 */
public class BeanComponent extends DefaultComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(BeanComponent.class);
    // use an internal soft cache for BeanInfo as they are costly to introspect
    // for example the bean language using OGNL expression runs much faster reusing the BeanInfo from this cache
    private final LRUSoftCache<BeanInfoCacheKey, BeanInfo> cache = new LRUSoftCache<BeanInfoCacheKey, BeanInfo>(1000);

    public BeanComponent() {
    }
    
    /**
     * A helper method to create a new endpoint from a bean with a generated URI
     */
    public ProcessorEndpoint createEndpoint(Object bean) {
        // used by servicemix-camel
        String uri = "bean:generated:" + bean;
        return createEndpoint(bean, uri);
    }

    /**
     * A helper method to create a new endpoint from a bean with a given URI
     */
    public ProcessorEndpoint createEndpoint(Object bean, String uri) {
        // used by servicemix-camel
        BeanProcessor processor = new BeanProcessor(bean, getCamelContext());
        return createEndpoint(uri, processor);
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        BeanEndpoint endpoint = new BeanEndpoint(uri, this);
        endpoint.setBeanName(remaining);
        Boolean cache = getAndRemoveParameter(parameters, "cache", Boolean.class, Boolean.FALSE);
        endpoint.setCache(cache);
        Processor processor = endpoint.getProcessor();
        setProperties(processor, parameters);
        return endpoint;
    }
    
    protected BeanEndpoint createEndpoint(String uri, BeanProcessor processor) {
        return new BeanEndpoint(uri, this, processor);
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
