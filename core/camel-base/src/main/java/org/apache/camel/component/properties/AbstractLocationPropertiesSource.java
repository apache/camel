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
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.OrderedProperties;

/**
 * Base class for {@link LoadablePropertiesSource} which can load properties from a source such as classpath or file system.
 */
public abstract class AbstractLocationPropertiesSource extends ServiceSupport implements LoadablePropertiesSource, LocationPropertiesSource {

    private final Properties properties = new OrderedProperties();
    private final PropertiesComponent propertiesComponent;
    private final PropertiesLocation location;

    protected AbstractLocationPropertiesSource(PropertiesComponent propertiesComponent, PropertiesLocation location) {
        this.propertiesComponent = propertiesComponent;
        this.location = location;
    }

    abstract Properties loadPropertiesFromLocation(PropertiesComponent propertiesComponent, PropertiesLocation location);

    @Override
    public PropertiesLocation getLocation() {
        return location;
    }

    @Override
    public Properties loadProperties() {
        return properties;
    }

    @Override
    public Properties loadProperties(Predicate<String> filter) {
        Properties answer = new Properties();

        for (String name: properties.stringPropertyNames()) {
            if (filter.test(name)) {
                answer.put(name, properties.get(name));
            }
        }

        return answer;
    }

    @Override
    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        Properties prop = loadPropertiesFromLocation(propertiesComponent, location);
        if (prop != null) {
            prop = prepareLoadedProperties(prop);
            properties.putAll(prop);
        }
    }

    /**
     * Strategy to prepare loaded properties before being used by Camel.
     * <p/>
     * This implementation will ensure values are trimmed, as loading properties from
     * a file with values having trailing spaces is not automatic trimmed by the Properties API
     * from the JDK.
     *
     * @param properties  the properties
     * @return the prepared properties
     */
    protected static Properties prepareLoadedProperties(Properties properties) {
        Properties answer = new OrderedProperties();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;

                // trim any trailing spaces which can be a problem when loading from
                // a properties file, note that java.util.Properties does already this
                // for any potential leading spaces so there's nothing to do there
                value = trimTrailingWhitespaces(s);
            }
            answer.put(key, value);
        }
        return answer;
    }

    private static String trimTrailingWhitespaces(String s) {
        int endIndex = s.length();
        for (int index = s.length() - 1; index >= 0; index--) {
            if (s.charAt(index) == ' ') {
                endIndex = index;
            } else {
                break;
            }
        }
        String answer = s.substring(0, endIndex);
        return answer;
    }

}
