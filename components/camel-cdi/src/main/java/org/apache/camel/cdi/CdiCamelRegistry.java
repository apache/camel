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
import java.util.Map;
import java.util.Set;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Registry} used by Camel to perform lookup into the CDI {@link BeanManager}.
 */
@Vetoed
final class CdiCamelRegistry implements Registry {

    private final Logger logger = LoggerFactory.getLogger(CdiCamelRegistry.class);

    private final BeanManager manager;

    CdiCamelRegistry(BeanManager manager) {
        this.manager = manager;
    }

    @Override
    public Object lookupByName(String name) {
        ObjectHelper.notEmpty(name, "name");
        logger.trace("Looking up bean with name [{}]", name);
        // Work-around for WELD-2089
        if ("properties".equals(name) && findByTypeWithName(PropertiesComponent.class).containsKey("properties")) {
            return BeanManagerHelper.getReferenceByName(manager, name, PropertiesComponent.class);
        }
        return BeanManagerHelper.getReferenceByName(manager, name, Object.class);
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        ObjectHelper.notEmpty(name, "name");
        ObjectHelper.notNull(type, "type");
        logger.trace("Looking up bean with name [{}] of type [{}]", name, type);
        return BeanManagerHelper.getReferenceByName(manager, name, type);
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        ObjectHelper.notNull(type, "type");
        logger.trace("Looking up named beans of type [{}]", type);
        Map<String, T> references = new HashMap<>();
        for (Bean<?> bean : manager.getBeans(type, AnyLiteral.INSTANCE)) {
            if (bean.getName() != null) {
                references.put(bean.getName(), BeanManagerHelper.getReference(manager, type, bean));
            }
        }
        return references;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        ObjectHelper.notNull(type, "type");
        logger.trace("Looking up beans of type [{}]", type);
        return BeanManagerHelper.getReferencesByType(manager, type, AnyLiteral.INSTANCE);
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
}
