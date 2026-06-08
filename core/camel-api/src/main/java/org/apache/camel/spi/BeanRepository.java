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
package org.apache.camel.spi;

import java.util.Map;
import java.util.Set;

import org.apache.camel.NoSuchBeanTypeException;
import org.jspecify.annotations.Nullable;

/**
 * Pluggable contract for looking up beans by name or type, forming the foundation of Camel's
 * <a href="https://camel.apache.org/manual/registry.html">Registry</a>.
 * <p/>
 * At runtime, the active {@link org.apache.camel.CamelContext} exposes a composite
 * {@link org.apache.camel.spi.Registry} that delegates to one or more {@code BeanRepository} instances, allowing Camel
 * to integrate with external containers such as Spring {@code ApplicationContext}, CDI, JNDI, or OSGi service
 * registries without changes to core code. The composited view is used transparently whenever a route references a bean
 * by name (for example in the {@code .bean()} DSL call) or when Camel auto-wires component options from the registry.
 * <p/>
 * Lookup is available in three forms: by name only ({@link #lookupByName}), by name and expected type
 * ({@link #lookupByNameAndType}), and by type alone ({@link #findByType} / {@link #findByTypeWithName}). Prefer the
 * typed variants to avoid ambiguity when the same name is bound more than once.
 *
 * @see   org.apache.camel.spi.Registry
 * @since 3.0
 */
public interface BeanRepository {

    /**
     * Looks up a bean in the registry based purely on name, returning the bean or <tt>null</tt> if it could not be
     * found.
     * <p/>
     * Important: Multiple beans of different types may be bound with the same name, and its encouraged to use the
     * {@link #lookupByNameAndType(String, Class)} to lookup the bean with a specific type, or to use any of the
     * <tt>find</tt> methods.
     *
     * @param  name the name of the bean
     * @return      the bean from the registry or <tt>null</tt> if it could not be found
     */
    @Nullable
    Object lookupByName(String name);

    /**
     * Looks up a bean in the registry, returning the bean or <tt>null</tt> if it could not be found.
     *
     * @param  name the name of the bean
     * @param  type the type of the required bean
     * @return      the bean from the registry or <tt>null</tt> if it could not be found
     */
    <T> @Nullable T lookupByNameAndType(String name, Class<T> type);

    /**
     * Finds beans in the registry by their type.
     *
     * @param  type the type of the beans
     * @return      the types found, with their bean ids as the key. Returns an empty Map if none found.
     */
    <T> Map<String, T> findByTypeWithName(Class<T> type);

    /**
     * Finds beans in the registry by their type.
     *
     * @param  type the type of the beans
     * @return      the types found. Returns an empty Set if none found.
     */
    <T> Set<T> findByType(Class<T> type);

    /**
     * Finds the bean by type, if there is exactly only one instance of the bean
     *
     * @param  type the type of the beans
     * @return      the single bean instance, or null if none found or there are more than one bean of the given type.
     */
    default <T> @Nullable T findSingleByType(Class<T> type) {
        Set<T> set = findByType(type);
        if (set.size() == 1) {
            return set.iterator().next();
        } else {
            return null;
        }
    }

    /**
     * Finds the bean by type, if there is exactly only one instance of the bean
     *
     * @param  type the type of the beans
     * @return      the single bean instance, or throws {@link NoSuchBeanTypeException} if not exactly one bean was
     *              found.
     */
    default <T> T mandatoryFindSingleByType(Class<T> type) {
        T answer = findSingleByType(type);
        if (answer == null) {
            throw new NoSuchBeanTypeException(type);
        }
        return answer;
    }

    /**
     * Strategy to wrap the value to be stored in the registry.
     *
     * @param  value the value
     * @return       the value to return
     */
    default Object unwrap(Object value) {
        return value;
    }

}
