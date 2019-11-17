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
package org.apache.camel.cdi;

import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.spi.BeanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toMap;
import static org.apache.camel.cdi.AnyLiteral.ANY;
import static org.apache.camel.cdi.BeanManagerHelper.getReference;
import static org.apache.camel.cdi.BeanManagerHelper.getReferenceByName;
import static org.apache.camel.cdi.BeanManagerHelper.getReferencesByType;
import static org.apache.camel.util.ObjectHelper.notNull;
import static org.apache.camel.util.StringHelper.notEmpty;

/**
 * The {@link BeanRepository} used by Camel to perform lookup into the CDI {@link BeanManager}.
 */
@Vetoed
final class CdiCamelBeanRepository implements BeanRepository {

    private final Logger logger = LoggerFactory.getLogger(CdiCamelBeanRepository.class);

    private final BeanManager manager;

    CdiCamelBeanRepository(BeanManager manager) {
        this.manager = manager;
    }

    @Override
    public Object lookupByName(String name) {
        notEmpty(name, "name");
        logger.trace("Looking up bean with name [{}]", name);
        // Work-around for WELD-2089
        if ("properties".equals(name) && findByTypeWithName(PropertiesComponent.class).containsKey("properties")) {
            return getReferenceByName(manager, name, PropertiesComponent.class).orElse(null);
        }
        return getReferenceByName(manager, name, Object.class).orElse(null);
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        notEmpty(name, "name");
        notNull(type, "type");
        logger.trace("Looking up bean with name [{}] of type [{}]", name, type);
        return getReferenceByName(manager, name, type).orElse(null);
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        notNull(type, "type");
        logger.trace("Looking up named beans of type [{}]", type);
        return manager.getBeans(type, ANY).stream()
            .filter(bean -> bean.getName() != null)
            .collect(toMap(Bean::getName, bean -> getReference(manager, type, bean)));
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        notNull(type, "type");
        logger.trace("Looking up beans of type [{}]", type);
        return getReferencesByType(manager, type, ANY);
    }

}
