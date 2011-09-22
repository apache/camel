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
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.URIField;
import org.apache.camel.impl.converter.PropertyEditorTypeConverter;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some helper methods for working with {@link EndpointConfiguration} instances
 *
 */
public final class ConfigurationHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationHelper.class);
    private static final TypeConverter TC = new PropertyEditorTypeConverter();

    private ConfigurationHelper() {
        //Utility Class
    }

    public interface ParameterSetter {
        <T> void set(EndpointConfiguration config, String name, T value);
    }

    public static EndpointConfiguration createConfiguration(String uri, CamelContext context) throws Exception {
        int schemeSeparator = uri.indexOf(':');
        if (schemeSeparator == -1) {
            // not an URIConfiguration
            return null;
        }
        String scheme = uri.substring(0, schemeSeparator);
        
        /* Temporary (maybe) workaround for unclear differentiation between URLs and URNs in Camel
        String schemeSpecificPart = uri.substring(schemeSeparator + 1);
        if (!schemeSpecificPart.startsWith("//")) {
            uri = scheme + "://" + schemeSpecificPart;
        } */

        Component component = context.getComponent(scheme);
        LOG.trace("Lookup for Component handling \"{}:\" configuration returned {}", 
            scheme, component != null ? component.getClass().getName() : "<null>");
        DefaultEndpointConfiguration cfg = (DefaultEndpointConfiguration)component.createConfiguration(scheme);
        // Should we be ok with URIs not properly encoded? (that method may need a bit of refactoring too)
        cfg.setURI(new URI(UnsafeUriCharactersEncoder.encode(uri)));
        return cfg;
    }
    
    public static String formatConfigurationUri(EndpointConfiguration config, EndpointConfiguration.UriFormat format) {
        return "TBD";
    }
    
    public static void populateFromURI(EndpointConfiguration config, ParameterSetter setter) {
        URI uri = config.getURI();
        
        setter.set(config, EndpointConfiguration.URI_SCHEME, uri.getScheme());
        setter.set(config, EndpointConfiguration.URI_SCHEME_SPECIFIC_PART, uri.getSchemeSpecificPart());
        setter.set(config, EndpointConfiguration.URI_AUTHORITY, uri.getAuthority());
        setter.set(config, EndpointConfiguration.URI_USER_INFO, uri.getUserInfo());
        setter.set(config, EndpointConfiguration.URI_HOST, uri.getHost());
        setter.set(config, EndpointConfiguration.URI_PORT, Integer.toString(uri.getPort()));
        setter.set(config, EndpointConfiguration.URI_PATH, uri.getPath());
        setter.set(config, EndpointConfiguration.URI_QUERY, uri.getQuery());
        setter.set(config, EndpointConfiguration.URI_FRAGMENT, uri.getFragment());
        
        // now parse query and set custom parameters
        Map<String, Object> parameters;
        try {
            parameters = URISupport.parseParameters(uri);
            for (Map.Entry<String, Object> pair : parameters.entrySet()) {
                setter.set(config, pair.getKey(), pair.getValue());
            }
        } catch (URISyntaxException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public static Field findConfigurationField(EndpointConfiguration config, String name) {
        if (config != null && name != null) {
            Class<?> clazz = config.getClass();
            Field[] fields = clazz.getDeclaredFields();
    
            Field found = null;
            URIField anno = null;
            for (final Field field : fields) {
                anno = field.getAnnotation(URIField.class);
                if (anno == null ? field.getName().equals(name) : anno.component().equals(name) 
                    || (anno.component().equals(EndpointConfiguration.URI_QUERY) && anno.parameter().equals(name))) { 
    
                    found = field;
                    LOG.trace("Found field {}.{} as candidate for parameter {}", 
                        new Object[] {clazz.getName(), found != null ? found.getName() : "<null>", name});
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
                LOG.trace("Filed to invokd setter for field '{}'. Reason: {}", field.getName(), e);
            }
        }
        // We cannot distinguish between a null returned as the actually value of a parameter
        // or the configuration parameter not being present, but it doesn't make much of a difference.
        // Use findConfigurationParameter(EndpointConfiguration, String) to find that out.
        return null;
    }

    public static <T> void setConfigurationField(EndpointConfiguration config, 
        String name, T value) {

        Field field = findConfigurationField(config, name);
        if (field == null) {
            return;
        }

        // now try to set the field value
        try {
            String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
            Method setter = config.getClass().getMethod(setterName, field.getType());
            setter.invoke(config, TC.convertTo(field.getType(), value));
        } catch (Exception e) {
            LOG.trace("Filed to invokd setter for field '{}'. Reason: {}", name, e);
            return;
        }
    }
    
    public static class FieldParameterSetter implements ParameterSetter {
        @Override
        public <T> void set(EndpointConfiguration config, String name, T value) {
            setConfigurationField(config, name, value);
        }
    }
}
