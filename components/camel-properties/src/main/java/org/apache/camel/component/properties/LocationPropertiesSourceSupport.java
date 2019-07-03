/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.properties;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.OrderedProperties;

public abstract class LocationPropertiesSourceSupport extends ServiceSupport implements LoadablePropertiesSource, LocationPropertiesSource {

    private final Properties properties = new OrderedProperties();
    private final PropertiesComponent propertiesComponent;
    private final PropertiesLocation location;
    private final boolean ignoreMissingLocation;

    protected LocationPropertiesSourceSupport(PropertiesComponent propertiesComponent, PropertiesLocation location, boolean ignoreMissingLocation) {
        this.propertiesComponent = propertiesComponent;
        this.location = location;
        this.ignoreMissingLocation = ignoreMissingLocation;
    }

    abstract Properties loadPropertiesFromLocation(PropertiesComponent propertiesComponent, boolean ignoreMissingLocation, PropertiesLocation location);

    public PropertiesLocation getLocation() {
        return location;
    }

    @Override
    public Properties loadProperties() {
        return properties;
    }

    @Override
    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        Properties prop = loadPropertiesFromLocation(propertiesComponent, ignoreMissingLocation, location);
        prop = prepareLoadedProperties(prop);
        properties.putAll(prop);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
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
