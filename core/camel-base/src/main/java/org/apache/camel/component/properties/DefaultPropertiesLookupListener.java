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

package org.apache.camel.component.properties;

import java.util.Map;

import org.apache.camel.PropertiesLookupListener;
import org.apache.camel.spi.PropertiesResolvedValue;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;

/**
 * A {@link PropertiesLookupListener} listener that captures the resolved properties for dev consoles, management and
 * troubleshooting purposes.
 */
public class DefaultPropertiesLookupListener extends ServiceSupport implements PropertiesLookupListener {

    private Map<String, PropertiesResolvedValue> properties;

    @Override
    public void onLookup(String name, String value, String defaultValue, String source) {
        properties.put(name, new PropertiesResolvedValue(name, value, value, defaultValue, source));
    }

    void updateValue(String name, String newValue, String newSource) {
        var p = properties.get(name);
        if (p != null) {
            String source = newSource != null ? newSource : p.source();
            properties.put(
                    name, new PropertiesResolvedValue(p.name(), p.originalValue(), newValue, p.defaultValue(), source));
        }
    }

    public PropertiesResolvedValue getProperty(String key) {
        return properties.get(key);
    }

    @Override
    protected void doBuild() throws Exception {
        // use a cache with max limit to avoid capturing endless property values
        // if there are a lot of dynamic values
        properties = LRUCacheFactory.newLRUCache(1000);
    }

    @Override
    protected void doShutdown() throws Exception {
        properties.clear();
    }
}
