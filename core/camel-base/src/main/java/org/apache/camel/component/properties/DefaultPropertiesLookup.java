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

import java.util.Properties;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.PropertiesLookupListener;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.util.OrderedLocationProperties;

/**
 * Default {@link PropertiesLookup} which lookup properties from a {@link java.util.Properties} with all existing
 * properties.
 */
public class DefaultPropertiesLookup implements PropertiesLookup {

    private final PropertiesComponent component;

    public DefaultPropertiesLookup(PropertiesComponent component) {
        this.component = component;
    }

    @Override
    public String lookup(String name, String defaultValue) {
        try {
            return doLookup(name, defaultValue);
        } catch (NoTypeConversionAvailableException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    private String doLookup(String name, String defaultValue) throws NoTypeConversionAvailableException {
        String answer = null;

        // local takes precedence
        Properties local = component.getLocalProperties();
        if (local != null) {
            // use get as the value can potentially be stored as a non string value
            Object value = local.get(name);
            if (value != null) {
                answer = component.getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, value);
                String loc = location(local, name, "LocalProperties");
                String localDefaultValue = null;
                if (local instanceof OrderedLocationProperties locationProperties) {
                    Object val = locationProperties.getDefaultValue(name);
                    if (val != null) {
                        localDefaultValue
                                = component.getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, val);
                    }
                }
                onLookup(name, answer, localDefaultValue, loc);
            }
        }

        // override takes precedence
        if (answer == null && component.getOverrideProperties() != null) {
            // use get as the value can potentially be stored as a non string value
            Object value = component.getOverrideProperties().get(name);
            if (value != null) {
                answer = component.getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, value);
                String loc = location(local, name, "OverrideProperties");
                onLookup(name, answer, defaultValue, loc);
            }
        }
        if (answer == null) {
            // try till first found source
            for (PropertiesSource ps : component.getPropertiesSources()) {
                answer = ps.getProperty(name, defaultValue);
                if (answer != null) {
                    String source = ps.getName();
                    if (ps instanceof ClasspathPropertiesSource propSource) {
                        source = "classpath:" + propSource.getLocation().getPath();
                    } else if (ps instanceof FilePropertiesSource propSource) {
                        source = "file:" + propSource.getLocation().getPath();
                    } else if (ps instanceof RefPropertiesSource propSource) {
                        source = "ref:" + propSource.getLocation().getPath();
                    } else if (ps instanceof LocationPropertiesSource propSource) {
                        source = propSource.getLocation().getPath();
                    } else if (ps instanceof LoadablePropertiesSource propSource) {
                        Properties prop = propSource.loadProperties();
                        if (prop instanceof OrderedLocationProperties olp) {
                            source = olp.getLocation(name);
                        }
                    }
                    onLookup(name, answer, defaultValue, source);
                    break;
                }
            }
        }
        // initial properties are last
        if (answer == null && component.getInitialProperties() != null) {
            // use get as the value can potentially be stored as a non string value
            Object value = component.getInitialProperties().get(name);
            if (value != null) {
                answer = component.getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, value);
                String loc = location(local, name, "InitialProperties");
                onLookup(name, answer, defaultValue, loc);
            }
        }

        return answer;
    }

    private void onLookup(String name, String value, String defaultValue, String source) {
        for (PropertiesLookupListener listener : component.getPropertiesLookupListeners()) {
            try {
                listener.onLookup(name, value, defaultValue, source);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private static String location(Properties prop, String name, String defaultLocation) {
        String loc = null;
        if (prop instanceof OrderedLocationProperties value) {
            loc = value.getLocation(name);
        }

        if (loc == null) {
            loc = defaultLocation;
        }
        return loc;
    }
}
