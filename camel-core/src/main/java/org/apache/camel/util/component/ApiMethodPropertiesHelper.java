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
 * Helper class to work with ApiMethod arguments.
 */
public final class ApiMethodPropertiesHelper<C> {

    private final Logger LOG = LoggerFactory.getLogger(ApiMethodPropertiesHelper.class);

    // set of field names which are specific to the api, to be excluded from method argument considerations
    private final Set<String> COMPONENT_CONFIG_FIELDS = new HashSet<String>();

    private final Class<?> componentConfigClass;
    private final String propertyPrefix;

    public ApiMethodPropertiesHelper(Class<C> componentConfiguration, String propertyPrefix) {

        this.componentConfigClass = componentConfiguration;
        this.propertyPrefix = propertyPrefix;

        for (Field field : componentConfiguration.getDeclaredFields()) {
            COMPONENT_CONFIG_FIELDS.add(field.getName());
        }
    }

    /**
     * Gets exchange header properties that start with propertyPrefix.
     *
     * @param exchange Camel exchange
     * @param properties map to collect properties with required prefix
     */
    public Map<String, Object> getExchangeProperties(Exchange exchange, Map<String, Object> properties) {
        final int prefixLength = propertyPrefix.length();
        int nProperties = 0;
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            if (entry.getKey().startsWith(propertyPrefix)) {
                properties.put(entry.getKey().substring(prefixLength),
                    entry.getValue());
                nProperties++;
            }
        }
        LOG.debug("Found {} properties in exchange", nProperties);
        return properties;
    }

    public void getEndpointProperties(Object endpointConfiguration,
                                             Map<String, Object> properties) {

        if (IntrospectionSupport.getProperties(endpointConfiguration, properties, null, false)) {
            final Set<String> names = properties.keySet();
            // remove component config properties so we only have endpoint properties
            names.removeAll(COMPONENT_CONFIG_FIELDS);
        }
        if (LOG.isDebugEnabled()) {
            final Set<String> names = properties.keySet();
            LOG.debug("Found endpoint properties {}",
                    names.retainAll(getValidEndpointProperties(endpointConfiguration)));
        }
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
