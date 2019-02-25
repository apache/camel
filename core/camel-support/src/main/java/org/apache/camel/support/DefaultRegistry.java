/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.support;

import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.BeanRepository;
import org.apache.camel.spi.Registry;

/**
 * The default {@link Registry} which supports using a given first-choice repository to lookup the beans,
 * such as Spring, JNDI, OSGi etc. And to use a secondary {@link SimpleRegistry} as the fallback repository
 * to lookup and bind beans.
 */
public class DefaultRegistry implements Registry {

    private final BeanRepository repository;
    private final Registry simple = new SimpleRegistry();

    /**
     * Creates a default registry that only uses {@link SimpleRegistry} as the repository.
     */
    public DefaultRegistry() {
        this(null);
    }

    /**
     * Creates a registry that uses the given {@link BeanRepository} as first choice bean repository to lookup beans.
     * Will fallback and use {@link SimpleRegistry} as internal registry if the beans cannot be found in the first
     * choice bean repository.
     *
     * @param repository the first choice repository such as Spring, JNDI, OSGi etc.
     */
    public DefaultRegistry(BeanRepository repository) {
        this.repository = repository;
    }

    @Override
    public void bind(String id, Object bean) {
        // favour use the real repository
        if (repository != null && repository instanceof Registry) {
            ((Registry) repository).bind(id, bean);
        } else {
            simple.bind(id, bean);
        }
    }

    @Override
    public Object lookupByName(String name) {
        Object answer = repository != null ? repository.lookupByName(name) : null;
        if (answer == null) {
            answer = simple.lookupByName(name);
        }
        return answer;
    }

    @Override
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        T answer = repository != null ? repository.lookupByNameAndType(name, type) : null;
        if (answer == null) {
            answer = simple.lookupByNameAndType(name, type);
        }
        return answer;
    }

    @Override
    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> answer = repository != null ? repository.findByTypeWithName(type) : null;
        if (answer == null) {
            answer = simple.findByTypeWithName(type);
        }
        return answer;
    }

    @Override
    public <T> Set<T> findByType(Class<T> type) {
        Set<T> answer = repository != null ? repository.findByType(type) : null;
        if (answer == null) {
            answer = simple.findByType(type);
        }
        return answer;
    }
}
