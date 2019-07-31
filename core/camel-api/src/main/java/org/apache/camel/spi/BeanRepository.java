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

/**
 * Represents a bean repository used to lookup components by name and type.
 * This allows Camel to plugin to third-party bean repositories such as Spring, JNDI, OSGi.
 */
public interface BeanRepository {

    /**
     * Looks up a bean in the registry based purely on name,
     * returning the bean or <tt>null</tt> if it could not be found.
     * <p/>
     * Important: Multiple beans of different types may be bound with the same name, and its
     * encouraged to use the {@link #lookupByNameAndType(String, Class)} to lookup the bean
     * with a specific type, or to use any of the <tt>find</tt> methods.
     *
     * @param name the name of the bean
     * @return the bean from the registry or <tt>null</tt> if it could not be found
     */
    Object lookupByName(String name);

    /**
     * Looks up a bean in the registry, returning the bean or <tt>null</tt> if it could not be found.
     *
     * @param name the name of the bean
     * @param type the type of the required bean
     * @return the  bean from the registry or <tt>null</tt> if it could not be found
     */
    <T> T lookupByNameAndType(String name, Class<T> type);

    /**
     * Finds beans in the registry by their type.
     *
     * @param type  the type of the beans
     * @return the types found, with their bean ids as the key. Returns an empty Map if none found.
     */
    <T> Map<String, T> findByTypeWithName(Class<T> type);

    /**
     * Finds beans in the registry by their type.
     *
     * @param type  the type of the beans
     * @return the types found. Returns an empty Set if none found.
     */
    <T> Set<T> findByType(Class<T> type);

    /**
     * Strategy to wrap the value to be stored in the registry.
     *
     * @param value  the value
     * @return the value to return
     */
    default Object unwrap(Object value) {
        return value;
    }

}
