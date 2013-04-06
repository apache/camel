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
import java.util.Map;
import java.util.Set;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.spi.Registry;

/**
 * A {@link Map}-based registry.
 */
public class SimpleRegistry extends HashMap<String, Object> implements Registry {

    private static final long serialVersionUID = -3739035212761568984L;

    public Object lookupByName(String name) {
        return get(name);
    }

    public <T> T lookupByNameAndType(String name, Class<T> type) {
        Object answer = lookupByName(name);

        // just to be safe
        if (answer == null) {
            return null;
        }

        try {
            return type.cast(answer);
        } catch (Throwable e) {
            String msg = "Found bean: " + name + " in SimpleRegistry: " + this
                    + " of type: " + answer.getClass().getName() + " expected type was: " + type;
            throw new NoSuchBeanException(name, msg, e);
        }
    }

    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> result = new HashMap<String, T>();
        for (Map.Entry<String, Object> entry : entrySet()) {
            if (type.isInstance(entry.getValue())) {
                result.put(entry.getKey(), type.cast(entry.getValue()));
            }
        }
        return result;
    }

    public <T> Set<T> findByType(Class<T> type) {
        Set<T> result = new HashSet<T>();
        for (Map.Entry<String, Object> entry : entrySet()) {
            if (type.isInstance(entry.getValue())) {
                result.add(type.cast(entry.getValue()));
            }
        }
        return result;
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
