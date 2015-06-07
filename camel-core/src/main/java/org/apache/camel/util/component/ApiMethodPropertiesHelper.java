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
package org.apache.camel.util.component;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.util.IntrospectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to work with ApiMethod arguments to be extended by components.
 */
public abstract class ApiMethodPropertiesHelper<C> {

    protected static final Logger LOG = LoggerFactory.getLogger(ApiMethodPropertiesHelper.class);

    // set of field names which are specific to the api, to be excluded from method argument considerations
    protected final Set<String> componentConfigFields = new HashSet<String>();

    protected final Class<?> componentConfigClass;
    protected final String propertyPrefix;

    private final int prefixLength;
    private final String camelCasePrefix;

    protected ApiMethodPropertiesHelper(Class<C> componentConfiguration, String propertyPrefix) {

        this.componentConfigClass = componentConfiguration;
        this.propertyPrefix = propertyPrefix;

        this.prefixLength = propertyPrefix.length();
        if (!Character.isLetterOrDigit(propertyPrefix.charAt(prefixLength - 1))) {
            this.camelCasePrefix = propertyPrefix.substring(0, prefixLength - 1);
        } else {
            this.camelCasePrefix = null;
        }

        for (Field field : componentConfiguration.getDeclaredFields()) {
            componentConfigFields.add(field.getName());
        }
    }

    /**
     * Gets exchange header properties that start with propertyPrefix.
     *
     * @param exchange Camel exchange
     * @param properties map to collect properties with required prefix
     */
    public Map<String, Object> getExchangeProperties(Exchange exchange, Map<String, Object> properties) {

        int nProperties = 0;
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith(propertyPrefix)) {
                properties.put(key.substring(prefixLength),
                    entry.getValue());
                nProperties++;
            } else if (camelCasePrefix != null && key.startsWith(camelCasePrefix)) {
                // assuming all property names start with a lowercase character
                final String propertyName = String.valueOf(Character.toLowerCase(key.charAt(prefixLength - 1)))
                    + key.substring(prefixLength);
                properties.put(propertyName, entry.getValue());
                nProperties++;
            }
        }
        LOG.debug("Found {} properties in exchange", nProperties);
        return properties;
    }

    public void getEndpointProperties(Object endpointConfiguration, Map<String, Object> properties) {

        Set<String> names = null;
        if (IntrospectionSupport.getProperties(endpointConfiguration, properties, null, false)) {
            names = properties.keySet();
            // remove component config properties so we only have endpoint properties
            names.removeAll(componentConfigFields);
        }
        LOG.debug("Found endpoint properties {}", names);
    }

    public Set<String> getEndpointPropertyNames(Object endpointConfiguration) {
        Map<String, Object> properties = new HashMap<String, Object>();
        getEndpointProperties(endpointConfiguration, properties);
        return Collections.unmodifiableSet(properties.keySet());
    }

    public Set<String> getValidEndpointProperties(Object endpointConfiguration) {
        Set<String> fields = new HashSet<String>();
        for (Field field : endpointConfiguration.getClass().getDeclaredFields()) {
            fields.add(field.getName());
        }
        return Collections.unmodifiableSet(fields);
    }

}
