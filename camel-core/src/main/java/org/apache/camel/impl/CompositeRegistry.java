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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Registry;

/**
 * This registry will look up the object with the sequence of the registry list until it finds the Object.
 */
public class CompositeRegistry implements Registry {
    private List<Registry> registryList;
    
    public CompositeRegistry() {
        registryList = new ArrayList<Registry>();
    }
    
    public CompositeRegistry(List<Registry> registries) {
        registryList = registries;
    }
    
    public void addRegistry(Registry registry) {
        registryList.add(registry);
    }

    public <T> T lookup(String name, Class<T> type) {
        T answer = null;
        RuntimeCamelException ex = null;
        for (Registry registry : registryList) {
            try {
                answer = registry.lookup(name, type);
            } catch (Throwable e) {
                // do not double wrap the exception
                if (e instanceof NoSuchBeanException) {
                    ex = (NoSuchBeanException)e;
                } else {
                    ex = new NoSuchBeanException(name, "Cannot lookup: " + name + " from registry: " + registry
                        + " with expected type: " + type + " due: " + e.getMessage(), e);
                }
            }
            if (answer != null) {
                return answer;
            }
        }
        if (ex != null) { 
            throw ex;
        } else {
            return answer;
        }
    }

    public Object lookup(String name) {
        Object answer = null;
        for (Registry registry : registryList) {
            answer = registry.lookup(name);
            if (answer != null) {
                break;
            }
        }
        return answer;
    }

    public <T> Map<String, T> lookupByType(Class<T> type) {
        Map<String, T> answer = Collections.<String, T>emptyMap();
        for (Registry registry : registryList) {
            answer = registry.lookupByType(type);
            if (!answer.isEmpty()) {
                break;
            }
        }
        return answer;
    }

}
