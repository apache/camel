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
package org.apache.camel.component.facebook.data;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import facebook4j.Reading;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.facebook.FacebookConstants;
import org.apache.camel.component.facebook.config.FacebookConfiguration;
import org.apache.camel.component.facebook.config.FacebookEndpointConfiguration;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to work with Facebook component properties.
 */
public final class FacebookPropertiesHelper {

    // set of field names which are specific to Facebook4J api, to be excluded from method argument considerations
    private static final Set<String> COMPONENT_CONFIG_FIELDS = new HashSet<>();

    private static final Logger LOG = LoggerFactory.getLogger(FacebookPropertiesHelper.class);

    private static final Set<String> ENDPOINT_CONFIG_FIELDS = new HashSet<>();

    static {
        for (Field field : FacebookConfiguration.class.getDeclaredFields()) {
            COMPONENT_CONFIG_FIELDS.add(field.getName());
        }
        for (Field field : FacebookEndpointConfiguration.class.getDeclaredFields()) {
            ENDPOINT_CONFIG_FIELDS.add(field.getName());
        }
    }

    private FacebookPropertiesHelper() {
        // utility
    }

    /**
     * Apply properties for {@link Reading} type to the supplied {@link FacebookEndpointConfiguration}.
     *
     * @param configuration endpoint configuration to update
     * @param options       properties to apply to the reading field in configuration
     */
    public static void configureReadingProperties(
            FacebookEndpointConfiguration configuration,
            Map<String, Object> options) {
        final Map<String, Object> readingProperties = PropertiesHelper.extractProperties(
                options, FacebookConstants.READING_PREFIX);
        if (!readingProperties.isEmpty()) {
            try {
                // add to an existing reading reference?
                // NOTE Reading class does not support overwriting properties!!!
                Reading reading = configuration.getReading();

                if (reading != null) {
                    Reading readingUpdate = new Reading();
                    ReadingBuilder.setProperties(readingUpdate, readingProperties);
                    reading = ReadingBuilder.merge(reading, readingUpdate);
                } else {
                    reading = new Reading();
                    ReadingBuilder.setProperties(reading, readingProperties);
                }
                // set properties
                ReadingBuilder.setProperties(reading, readingProperties);

                // update reading in configuration
                configuration.setReading(reading);
            } catch (Exception e) {
                throw new IllegalArgumentException(readingProperties.toString(), e);
            }

            // add any unknown properties back to options to throw an error later
            for (Map.Entry<String, Object> entry : readingProperties.entrySet()) {
                options.put(FacebookConstants.READING_PREFIX + entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Gets exchange header properties that start with {@link FacebookConstants}.FACEBOOK_PROPERTY_PREFIX.
     *
     * @param exchange   Camel exchange
     * @param properties map to collect properties with required prefix
     */
    public static Map<String, Object> getExchangeProperties(Exchange exchange, Map<String, Object> properties) {
        int nProperties = 0;
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            if (entry.getKey().startsWith(FacebookConstants.FACEBOOK_PROPERTY_PREFIX)) {
                properties.put(entry.getKey().substring(FacebookConstants.FACEBOOK_PROPERTY_PREFIX.length()), entry.getValue());
                nProperties++;
            }
        }
        LOG.debug("Found {} properties in exchange", nProperties);
        return properties;
    }

    public static void getEndpointProperties(
            CamelContext camelContext, FacebookEndpointConfiguration configuration,
            Map<String, Object> properties) {
        BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(camelContext);
        if (beanIntrospection.getProperties(configuration, properties, null, false)) {
            final Set<String> names = properties.keySet();
            // remove component config properties so we only have endpoint properties
            names.removeAll(COMPONENT_CONFIG_FIELDS);
        }
        if (LOG.isDebugEnabled()) {
            final Set<String> names = properties.keySet();
            LOG.debug("Found endpoint properties {}", names.retainAll(ENDPOINT_CONFIG_FIELDS));
        }
    }

    public static Set<String> getEndpointPropertyNames(CamelContext camelContext, FacebookEndpointConfiguration configuration) {
        Map<String, Object> properties = new HashMap<>();
        getEndpointProperties(camelContext, configuration, properties);
        return Collections.unmodifiableSet(properties.keySet());
    }

    public static Set<String> getValidEndpointProperties() {
        return Collections.unmodifiableSet(ENDPOINT_CONFIG_FIELDS);
    }

}
