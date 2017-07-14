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
package org.apache.camel.component.properties;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;

/**
 * Default {@link org.apache.camel.component.properties.PropertiesResolver} which can resolve properties
 * from file and classpath.
 * <p/>
 * You can denote <tt>classpath:</tt> or <tt>file:</tt> as prefix in the uri to select whether the file
 * is located in the classpath or on the file system.
 *
 * @version 
 */
public class DefaultPropertiesResolver implements PropertiesResolver {

    private final PropertiesComponent propertiesComponent;

    public DefaultPropertiesResolver(PropertiesComponent propertiesComponent) {
        this.propertiesComponent = propertiesComponent;
    }

    public Properties resolveProperties(CamelContext context, boolean ignoreMissingLocation, List<PropertiesLocation> locations) throws Exception {
        Properties answer = new Properties();
        Properties prop;

        for (PropertiesLocation location : locations) {
            switch(location.getResolver()) {
            case "ref":
                prop = loadPropertiesFromRegistry(context, ignoreMissingLocation, location);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
                break;
            case "file":
                prop = loadPropertiesFromFilePath(context, ignoreMissingLocation, location);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
                break;
            case "classpath":
            default:
                // default to classpath
                prop = loadPropertiesFromClasspath(context, ignoreMissingLocation, location);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
                break;
            }
        }

        return answer;
    }

    protected Properties loadPropertiesFromFilePath(CamelContext context, boolean ignoreMissingLocation, PropertiesLocation location) throws IOException {
        Properties answer = new Properties();
        String path = location.getPath();

        InputStream is = null;
        Reader reader = null;
        try {
            is = new FileInputStream(path);
            if (propertiesComponent.getEncoding() != null) {
                reader = new BufferedReader(new InputStreamReader(is, propertiesComponent.getEncoding()));
                answer.load(reader);
            } else {
                answer.load(is);
            }
        } catch (FileNotFoundException e) {
            if (!ignoreMissingLocation && !location.isOptional()) {
                throw e;
            }
        } finally {
            IOHelper.close(reader, is);
        }

        return answer;
    }

    protected Properties loadPropertiesFromClasspath(CamelContext context, boolean ignoreMissingLocation, PropertiesLocation location) throws IOException {
        Properties answer = new Properties();
        String path = location.getPath();

        InputStream is = context.getClassResolver().loadResourceAsStream(path);
        Reader reader = null;
        if (is == null) {
            if (!ignoreMissingLocation && !location.isOptional()) {
                throw new FileNotFoundException("Properties file " + path + " not found in classpath");
            }
        } else {
            try {
                if (propertiesComponent.getEncoding() != null) {
                    reader = new BufferedReader(new InputStreamReader(is, propertiesComponent.getEncoding()));
                    answer.load(reader);
                } else {
                    answer.load(is);
                }
            } finally {
                IOHelper.close(reader, is);
            }
        }
        return answer;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Properties loadPropertiesFromRegistry(CamelContext context, boolean ignoreMissingLocation, PropertiesLocation location) throws IOException {
        String path = location.getPath();
        Properties answer;
        try {
            answer = context.getRegistry().lookupByNameAndType(path, Properties.class);
        } catch (Exception ex) {
            // just look up the Map as a fault back
            Map map = context.getRegistry().lookupByNameAndType(path, Map.class);
            answer = new Properties();
            answer.putAll(map);
        }
        if (answer == null && (!ignoreMissingLocation && !location.isOptional())) {
            throw new FileNotFoundException("Properties " + path + " not found in registry");
        }
        return answer != null ? answer : new Properties();
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
    protected Properties prepareLoadedProperties(Properties properties) {
        Properties answer = new Properties();
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
