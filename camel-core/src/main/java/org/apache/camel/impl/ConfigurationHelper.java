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

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.URIField;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some helper methods for working with {@link EndpointConfiguration} instances
 *
 */
@Deprecated
public final class ConfigurationHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationHelper.class);

    private ConfigurationHelper() {
        //Utility Class
    }

    public interface ParameterSetter {

        /**
         * Sets the parameter on the configuration.
         *
         * @param camelContext  the camel context
         * @param config        the configuration
         * @param name          the name of the parameter
         * @param value         the value to set
         * @throws RuntimeCamelException is thrown if error setting the parameter
         */
        <T> void set(CamelContext camelContext, EndpointConfiguration config, String name, T value) throws RuntimeCamelException;
    }

    public static EndpointConfiguration createConfiguration(String uri, CamelContext context) throws Exception {
        int schemeSeparator = uri.indexOf(':');
        if (schemeSeparator == -1) {
            // not an URIConfiguration
            return null;
        }
        String scheme = uri.substring(0, schemeSeparator);
        
        Component component = context.getComponent(scheme);
        if (LOG.isTraceEnabled()) {
            LOG.trace("Lookup for Component handling \"{}:\" configuration returned {}",
                new Object[]{scheme, component != null ? component.getClass().getName() : "<null>"});
        }
        if (component != null) {
            EndpointConfiguration config = component.createConfiguration(scheme);
            if (config instanceof DefaultEndpointConfiguration) {
                ((DefaultEndpointConfiguration) config).setURI(uri);
            }
            return config;
        } else {
            // no component to create the configuration
            return null;
        }
    }
    
    public static void populateFromURI(CamelContext camelContext, EndpointConfiguration config, ParameterSetter setter) {
        URI uri = config.getURI();
        
        setter.set(camelContext, config, EndpointConfiguration.URI_SCHEME, uri.getScheme());
        setter.set(camelContext, config, EndpointConfiguration.URI_SCHEME_SPECIFIC_PART, uri.getSchemeSpecificPart());
        setter.set(camelContext, config, EndpointConfiguration.URI_AUTHORITY, uri.getAuthority());
        setter.set(camelContext, config, EndpointConfiguration.URI_USER_INFO, uri.getUserInfo());
        setter.set(camelContext, config, EndpointConfiguration.URI_HOST, uri.getHost());
        setter.set(camelContext, config, EndpointConfiguration.URI_PORT, Integer.toString(uri.getPort()));
        setter.set(camelContext, config, EndpointConfiguration.URI_PATH, uri.getPath());
        setter.set(camelContext, config, EndpointConfiguration.URI_QUERY, uri.getQuery());
        setter.set(camelContext, config, EndpointConfiguration.URI_FRAGMENT, uri.getFragment());
        
        // now parse query and set custom parameters
        Map<String, Object> parameters;
        try {
            parameters = URISupport.parseParameters(uri);
            for (Map.Entry<String, Object> pair : parameters.entrySet()) {
                setter.set(camelContext, config, pair.getKey(), pair.getValue());
            }
        } catch (URISyntaxException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public static Field findConfigurationField(EndpointConfiguration config, String name) {
        if (config != null && name != null) {
            Class<?> clazz = config.getClass();
            Field[] fields = clazz.getDeclaredFields();
    
            Field found;
            URIField anno;
            for (final Field field : fields) {
                anno = field.getAnnotation(URIField.class);
                if (anno == null ? field.getName().equals(name) : anno.component().equals(name) 
                    || (anno.component().equals(EndpointConfiguration.URI_QUERY) && anno.parameter().equals(name))) { 
    
                    found = field;
                    LOG.trace("Found field {}.{} as candidate for parameter {}", new Object[]{clazz.getName(), found.getName(), name});
                    return found;
                }
            }
        }            
        return null;
    }

    public static Object getConfigurationParameter(EndpointConfiguration config, String name) {
        Field field = findConfigurationField(config, name);
        return getConfigurationParameter(config, field);
    }

    public static Object getConfigurationParameter(EndpointConfiguration config, Field field) {
        if (field != null) {
            try {
                return IntrospectionSupport.getProperty(config, field.getName());
            } catch (Exception e) {
                throw new RuntimeCamelException("Failed to get property '" + field.getName() + "' on " + config + " due " + e.getMessage(), e);
            }
        }
        return null;
    }

    public static <T> void setConfigurationField(CamelContext camelContext, EndpointConfiguration config, String name, T value) {
        Field field = findConfigurationField(config, name);
        if (field == null) {
            return;
        }

        try {
            IntrospectionSupport.setProperty(camelContext.getTypeConverter(), config, name, value);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to set property '" + name + "' on " + config + " due " + e.getMessage(), e);
        }
    }
    
    public static class FieldParameterSetter implements ParameterSetter {
        @Override
        public <T> void set(CamelContext camelContext, EndpointConfiguration config, String name, T value) {
            setConfigurationField(camelContext, config, name, value);
        }
    }
}
