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
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.Registry;

/**
 * The default {@link Registry} which supports using a given first-choice repository to lookup the beans,
 * such as Spring, JNDI, OSGi etc. And to use a secondary {@link SimpleRegistry} as the fallback repository
 * to lookup and bind beans.
 * <p/>
 * Notice that beans in the fallback registry are not managed by the first-choice registry, so these beans
 * may not support dependency injection and other features that the first-choice registry may offer.
 */
public class DefaultRegistry implements Registry, CamelContextAware {

    private CamelContext camelContext;
    private List<BeanRepository> repositories;
    private final Registry simple = new SimpleRegistry();

    /**
     * Creates a default registry that only uses {@link SimpleRegistry} as the repository.
     */
    public DefaultRegistry() {
        // noop
    }

    /**
     * Creates a registry that uses the given {@link BeanRepository} as first choice bean repository to lookup beans.
     * Will fallback and use {@link SimpleRegistry} as internal registry if the beans cannot be found in the first
     * choice bean repository.
     *
     * @param repositories the first choice repositories such as Spring, JNDI, OSGi etc.
     */
    public DefaultRegistry(BeanRepository... repositories) {
        if (repositories != null) {
            this.repositories = new ArrayList<>(Arrays.asList(repositories));
        }
    }

    /**
     * Creates a registry that uses the given {@link BeanRepository} as first choice bean repository to lookup beans.
     * Will fallback and use {@link SimpleRegistry} as internal registry if the beans cannot be found in the first
     * choice bean repository.
     *
     * @param repositories the first choice repositories such as Spring, JNDI, OSGi etc.
     */
    public DefaultRegistry(Collection<BeanRepository> repositories) {
        if (repositories != null) {
            this.repositories = new ArrayList<>(repositories);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Gets the bean repositories.
     *
     * @return the bean repositories, or <tt>null</tt> if none are in use.
     */
    public List<BeanRepository> getRepositories() {
        if (repositories == null) {
            return null;
        } else {
            return Collections.unmodifiableList(repositories);
        }
    }

    @Override
    public void bind(String id, Object bean) {
        simple.bind(id, bean);
    }

    @Override
    public Object lookupByName(String name) {
        try {
            // Must avoid attempting placeholder resolution when looking up
            // the properties component or else we end up in an infinite loop.
            if (camelContext != null && !name.equals("properties")) {
                name = camelContext.resolvePropertyPlaceholders(name);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        if (repositories != null) {
            for (BeanRepository r : repositories) {
                Object answer = r.lookupByName(name);
                if (answer != null) {
                    return answer;
                }
            }
        }
        return simple.lookupByName(name);
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        try {
            // Must avoid attempting placeholder resolution when looking up
            // the properties component or else we end up in an infinite loop.
            if (camelContext != null && !name.equals("properties")) {
                name = camelContext.resolvePropertyPlaceholders(name);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }

        if (repositories != null) {
            for (BeanRepository r : repositories) {
                T answer = r.lookupByNameAndType(name, type);
                if (answer != null) {
                    return answer;
                }
            }
        }
        return simple.lookupByNameAndType(name, type);
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        if (repositories != null) {
            for (BeanRepository r : repositories) {
                Map<String, T> answer = r.findByTypeWithName(type);
                if (answer != null) {
                    return answer;
                }
            }
        }
        return simple.findByTypeWithName(type);
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        if (repositories != null) {
            for (BeanRepository r : repositories) {
                Set<T> answer = r.findByType(type);
                if (answer != null) {
                    return answer;
                }
            }
        }
        return simple.findByType(type);
    }
}
