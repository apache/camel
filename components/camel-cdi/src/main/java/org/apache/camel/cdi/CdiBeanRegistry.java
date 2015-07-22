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
package org.apache.camel.cdi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CdiBeanRegistry used by Camel to perform lookup into the CDI {@link javax.enterprise.inject.spi.BeanManager}.
 */
public class CdiBeanRegistry implements Registry {
    private static final Logger LOG = LoggerFactory.getLogger(CdiBeanRegistry.class);

    private final BeanManager beanManager;

    public CdiBeanRegistry(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    @Override
    public Object lookupByName(final String name) {
        ObjectHelper.notEmpty(name, "name");
        LOG.trace("Looking up bean with name {}", name);

        return CdiBeanManagerHelper.lookupBeanByName(beanManager, name);
    }

    @Override
    public <T> T lookupByNameAndType(final String name, final Class<T> type) {
        ObjectHelper.notEmpty(name, "name");
        ObjectHelper.notNull(type, "type");

        LOG.trace("Looking up bean with name {} of type {}", name, type);
        return CdiBeanManagerHelper.lookupBeanByNameAndType(beanManager, name, type);
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(final Class<T> type) {
        ObjectHelper.notNull(type, "type");

        LOG.trace("Lookups based of type {}", type);
        Map<String, T> beans = new HashMap<String, T>();
        Set<Bean<?>> definitions = beanManager.getBeans(type);

        if (definitions == null) {
            return beans;
        }
        for (Bean<?> bean : definitions) {
            if (bean.getName() != null) {
                T obj = CdiBeanManagerHelper.lookupBeanByNameAndType(beanManager, bean.getName(), type);
                beans.put(bean.getName(), obj);
            }
        }
        return beans;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        ObjectHelper.notNull(type, "type");

        LOG.trace("Lookups based of type {}", type);
        Set<T> beans = new HashSet<T>();
        Set<Bean<?>> definitions = beanManager.getBeans(type);

        if (definitions == null) {
            return beans;
        }
        for (Bean<?> bean : definitions) {
            if (bean.getName() != null) {
                T obj = CdiBeanManagerHelper.lookupBeanByNameAndType(beanManager, bean.getName(), type);
                beans.add(obj);
            }
        }
        return beans;
    }

    @Override
    public Object lookup(String name) {
        return lookupByName(name);
    }

    @Override
    public <T> T lookup(String name, Class<T> type) {
        return lookupByNameAndType(name, type);
    }

    @Override
    public <T> Map<String, T> lookupByType(Class<T> type) {
        return findByTypeWithName(type);
    }

    @Override
    public String toString() {
        return "CdiRegistry[" + System.identityHashCode(this) + "]";
    }
}
