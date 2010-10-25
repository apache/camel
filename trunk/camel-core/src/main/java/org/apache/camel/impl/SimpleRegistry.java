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
import java.util.Map;

import org.apache.camel.spi.Registry;

/**
 * A {@link Map}-based registry.
 */
public class SimpleRegistry extends HashMap<String, Object> implements Registry {

    private static final long serialVersionUID = -3739035212761568984L;

    public Object lookup(String name) {
        return get(name);
    }

    public <T> T lookup(String name, Class<T> type) {
        Object o = lookup(name);
        return type.cast(o);
    }

    public <T> Map<String, T> lookupByType(Class<T> type) {
        Map<String, T> result = new HashMap<String, T>();
        for (Map.Entry<String, Object> entry : entrySet()) {
            if (type.isInstance(entry.getValue())) {
                result.put(entry.getKey(), type.cast(entry.getValue()));
            }
        }
        return result;
    }
    
}
