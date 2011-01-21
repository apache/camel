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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Default {@link org.apache.camel.component.properties.PropertiesResolver} which can resolve properties
 * from file and classpath.
 * <p/>
 * You can denote <tt>classpath:</tt> or <tt>file:</tt> as prefix in the uri to select whether the file
 * is located in the classpath or on the file system.
 *
 * @version $Revision$
 */
public class DefaultPropertiesResolver implements PropertiesResolver {

    public Properties resolveProperties(CamelContext context, String... uri) throws Exception {
        Properties answer = new Properties();

        for (String path : uri) {
            if (path.startsWith("ref:")) {
                Properties prop = loadPropertiesFromRegistry(context, path);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
            } else if (path.startsWith("file:")) {
                Properties prop = loadPropertiesFromFilePath(context, path);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
            } else {
                // default to classpath
                Properties prop = loadPropertiesFromClasspath(context, path);
                prop = prepareLoadedProperties(prop);
                answer.putAll(prop);
            }
        }

        return answer;
    }

    protected Properties loadPropertiesFromFilePath(CamelContext context, String path) throws IOException {
        if (path.startsWith("file:")) {
            path = ObjectHelper.after(path, "file:");
        }
        InputStream is = new FileInputStream(path);
        Properties answer = new Properties();
        answer.load(is);
        return answer;
    }

    protected Properties loadPropertiesFromClasspath(CamelContext context, String path) throws IOException {
        if (path.startsWith("classpath:")) {
            path = ObjectHelper.after(path, "classpath:");
        }
        InputStream is = context.getClassResolver().loadResourceAsStream(path);
        if (is == null) {
            throw new FileNotFoundException("Properties file " + path + " not found in classpath");
        }
        Properties answer = new Properties();
        answer.load(is);
        return answer;
    }

    protected Properties loadPropertiesFromRegistry(CamelContext context, String path) throws IOException {
        if (path.startsWith("ref:")) {
            path = ObjectHelper.after(path, "ref:");
        }
        Properties answer = context.getRegistry().lookup(path, Properties.class);
        if (answer == null) {
            throw new FileNotFoundException("Properties " + path + " not found in registry");
        }
        return answer;
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
            // trim string values which can be a problem when loading from a properties file and there
            // is leading or trailing spaces in the value
            if (value instanceof String) {
                String s = (String) value;
                s = s.trim();
                value = s;
            }
            answer.put(key, value);
        }
        return answer;
    }

}
