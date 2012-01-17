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
package org.apache.camel.component.cdi;

import org.apache.camel.spi.Registry;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CdiBeanRegistry used by Camel to perform lookup into the
 * Cdi BeanManager. The BeanManager must be passed as argument
 * to the CdiRegistry constructor.
 */
public class CdiBeanRegistry implements Registry {

    private final Logger log = LoggerFactory.getLogger(getClass());

    BeanManager delegate;

    /**
     * @param delegate
     * @throws IllegalArgumentException
     */
    public CdiBeanRegistry(final BeanManager delegate)
            throws IllegalArgumentException {
        ObjectHelper.notNull(delegate, "delegate");
        this.delegate = delegate;
    }

    /**
     * @see org.apache.camel.spi.Registry#lookup(java.lang.String)
     */
    @Override
    public Object lookup(final String name) {
        ObjectHelper.notEmpty(name, "name");
        log.trace("Looking up bean using name = [{}] in CDI registry ...", name);

        final Set<Bean<?>> beans = getDelegate().getBeans(name);
        if (beans.isEmpty()) {
            log.debug(
                    "Found no bean matching name = [{}] in CDI registry.", name);
            return null;
        }
        if (beans.size() > 1) {
            throw new IllegalStateException(
                    "Expected to find exactly one bean having name [" + name
                            + "], but got [" + beans.size() + "]");
        }
        final Bean<?> bean = beans.iterator().next();
        log.debug("Found bean [{}] matching name = [{}] in CDI registry.",
                bean, name);

        final CreationalContext<?> creationalContext = getDelegate()
                .createCreationalContext(null);

        return getDelegate().getReference(bean, bean.getBeanClass(),
                creationalContext);
    }

    /**
     * @see org.apache.camel.spi.Registry#lookup(java.lang.String,
     *      java.lang.Class)
     */
    @Override
    public <T> T lookup(final String name, final Class<T> type) {
        ObjectHelper.notEmpty(name, "name");
        ObjectHelper.notNull(type, "type");
        log.trace(
                "Looking up bean using name = [{}] having expected type = [{}] in CDI registry ...",
                name, type.getName());

        return type.cast(lookup(name));
    }

    /**
     * @see org.apache.camel.spi.Registry#lookupByType(java.lang.Class)
     */
    @Override
    public <T> Map<String, T> lookupByType(final Class<T> type) {
        ObjectHelper.notNull(type, "type");
        log.trace(
                "Looking up all beans having expected type = [{}] in CDI registry ...",
                type.getName());

        final Set<Bean<?>> beans = getDelegate().getBeans(type);
        if (beans.isEmpty()) {
            log.debug(
                    "Found no beans having expected type = [{}] in CDI registry.",
                    type.getName());

            return Collections.emptyMap();
        }
        log.debug(
                "Found [{}] beans having expected type = [{}] in CDI registry.",
                Integer.valueOf(beans.size()), type.getName());

        final Map<String, T> beansByName = new HashMap<String, T>(beans.size());
        final CreationalContext<?> creationalContext = getDelegate()
                .createCreationalContext(null);
        for (final Bean<?> bean : beans) {
            beansByName.put(
                    bean.getName(),
                    type.cast(getDelegate().getReference(bean, type,
                            creationalContext)));
        }

        return beansByName;
    }

    @Override
    public String toString() {
        return "CdiRegistry@" + this.hashCode() + "[delegate = "
                + this.delegate + "]";
    }

    private Logger getLog() {
        return this.log;
    }

    private BeanManager getDelegate() {
        return this.delegate;
    }
}
