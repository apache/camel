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
package org.apache.camel.opentracing.registry;

import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a registry proxy to enable other OpenTracing related
 * proxies to be returned upon request. The initial purpose is to return a
 * proxy for the SLF4J Logger.
 *
 */
public class OpenTracingRegistryProxy implements Registry {

    private final Registry delegate;

    public OpenTracingRegistryProxy(Registry delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object lookupByName(String name) {
        return delegate.lookupByName(name);
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        return delegate.lookupByNameAndType(name, type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> ret = delegate.findByTypeWithName(type);
        if (type == org.slf4j.Logger.class) {
            Logger logger = null;
            String name = null;
            if (ret.size() == 0) {
                // Create logger
                name = "opentracing";
                logger = LoggerFactory.getLogger(name);
            } else if (ret.size() == 1) {
                name = ret.keySet().iterator().next();
                logger = (Logger)ret.get(name);
            }
            
            if (logger != null) {
                ret.put(name, (T)new OpenTracingLoggerProxy(name, logger));
            }
        }
        return ret;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        return delegate.findByType(type);
    }

    @Override
    public Object lookup(String name) {
        return delegate.lookup(name);
    }

    @Override
    public <T> T lookup(String name, Class<T> type) {
        return delegate.lookup(name, type);
    }

    @Override
    public <T> Map<String, T> lookupByType(Class<T> type) {
        return delegate.lookupByType(type);
    }

}
