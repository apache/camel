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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Registry;

/**
 * This registry will look up the object with the sequence of the registry list until it finds the Object.
 * Nested registries will be searched breadth-first.
 */
public class CompositeRegistry implements Registry {
    private final List<Registry> registryList;
    
    public CompositeRegistry() {
        registryList = new LinkedList<>();
    }
    
    public CompositeRegistry(List<Registry> registries) {
        registryList = registries;
    }
    
    public void addRegistry(Registry registry) {
        registryList.add(registry);
    }

    List<Registry> getRegistryList() {
        return registryList;
    }

    public <T> T getRegistry(Class<T> type) {
        Set<CompositeRegistry> seenCompositeRegistries = new HashSet<CompositeRegistry>();
        Queue<CompositeRegistry> queue = new LinkedList<CompositeRegistry>();
        queue.add(this);
        seenCompositeRegistries.add(this);
        while (!queue.isEmpty()) {
            CompositeRegistry root = queue.remove();
            for (Registry child : root.getRegistryList()) {
                if (child instanceof CompositeRegistry) {
                    if (!seenCompositeRegistries.contains(child)) {
                        queue.add(((CompositeRegistry)child));
                        seenCompositeRegistries.add((CompositeRegistry)child);
                    }
                } else {
                    if (type.isAssignableFrom(child.getClass())) {
                        return type.cast(child);
                    }
                }
            }
        }
        return null;
    }

    public <T> T lookupByNameAndType(String name, Class<T> type) {
        T answer = null;
        RuntimeCamelException ex = null;
        Set<CompositeRegistry> seenCompositeRegistries = new HashSet<CompositeRegistry>();
        Queue<CompositeRegistry> queue = new LinkedList<CompositeRegistry>();
        queue.add(this);
        seenCompositeRegistries.add(this);
        while (!queue.isEmpty()) {
            CompositeRegistry root = queue.remove();
            for (Registry child : root.getRegistryList()) {
                if (child instanceof CompositeRegistry) {
                    if (!seenCompositeRegistries.contains(child)) {
                        queue.add(((CompositeRegistry)child));
                        seenCompositeRegistries.add((CompositeRegistry)child);
                    }
                } else {
                    try {
                        answer = child.lookupByNameAndType(name, type);
                    } catch (Throwable e) {
                        // do not double wrap the exception
                        if (e instanceof NoSuchBeanException) {
                            ex = (NoSuchBeanException)e;
                        } else {
                            ex = new NoSuchBeanException(name, "Cannot lookup: " + name + " from registry: " + child
                                + " with expected type: " + type + " due: " + e.getMessage(), e);
                        }
                    }
                    if (answer != null) {
                        return answer;
                    }
                }
            }
        }
        if (ex != null) {
            throw ex;
        } else {
            return null;
        }
    }

    public Object lookupByName(String name) {
        Object answer;
        Set<CompositeRegistry> seenCompositeRegistries = new HashSet<CompositeRegistry>();
        Queue<CompositeRegistry> queue = new LinkedList<CompositeRegistry>();
        queue.add(this);
        seenCompositeRegistries.add(this);
        while (!queue.isEmpty()) {
            CompositeRegistry root = queue.remove();
            for (Registry child : root.getRegistryList()) {
                if (child instanceof CompositeRegistry) {
                    if (!seenCompositeRegistries.contains(child)) {
                        queue.add(((CompositeRegistry)child));
                        seenCompositeRegistries.add((CompositeRegistry)child);
                    }
                } else {
                    answer = child.lookupByName(name);
                    if (answer != null) {
                        return answer;
                    }
                }
            }
        }
        return null;
    }

    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> answer = new HashMap<String, T>();
        Set<CompositeRegistry> seenCompositeRegistries = new HashSet<CompositeRegistry>();
        Queue<CompositeRegistry> queue = new LinkedList<CompositeRegistry>();
        queue.add(this);
        seenCompositeRegistries.add(this);
        while (!queue.isEmpty()) {
            CompositeRegistry root = queue.remove();
            for (Registry child : root.getRegistryList()) {
                if (child instanceof CompositeRegistry) {
                    if (!seenCompositeRegistries.contains(child)) {
                        queue.add(((CompositeRegistry)child));
                        seenCompositeRegistries.add((CompositeRegistry)child);
                    }
                } else {
                    answer.putAll(child.findByTypeWithName(type));
                }
            }
        }
        return answer;
    }

    public <T> Set<T> findByType(Class<T> type) {
        Set<T> answer = new HashSet<T>();
        Set<CompositeRegistry> seenCompositeRegistries = new HashSet<CompositeRegistry>();
        Queue<CompositeRegistry> queue = new LinkedList<CompositeRegistry>();
        queue.add(this);
        seenCompositeRegistries.add(this);
        while (!queue.isEmpty()) {
            CompositeRegistry root = queue.remove();
            for (Registry child : root.getRegistryList()) {
                if (child instanceof CompositeRegistry) {
                    if (!seenCompositeRegistries.contains(child)) {
                        queue.add(((CompositeRegistry)child));
                        seenCompositeRegistries.add((CompositeRegistry)child);
                    }
                } else {
                    answer.addAll(child.findByType(type));
                }
            }
        }
        return answer;
    }

    public Object lookup(String name) {
        return lookupByName(name);
    }

    public <T> T lookup(String name, Class<T> type) {
        return lookupByNameAndType(name, type);
    }

    public <T> Map<String, T> lookupByType(Class<T> type) {
        return findByTypeWithName(type);
    }
}
