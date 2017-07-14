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
package org.apache.camel.catalog.maven;

import java.io.InputStream;
import java.util.Properties;

import static org.apache.camel.catalog.CatalogHelper.loadText;

/**
 * Helper methods for loading content from Camel components that the {@link org.apache.camel.catalog.CamelCatalog} requires.
 */
public final class ComponentArtifactHelper {

    private ComponentArtifactHelper() {
    }

    public static Properties loadComponentProperties(boolean log, ClassLoader classLoader) {
        Properties answer = new Properties();
        try {
            // load the component files using the recommended way by a component.properties file
            InputStream is = classLoader.getResourceAsStream("META-INF/services/org/apache/camel/component.properties");
            if (is != null) {
                answer.load(is);
            }
        } catch (Throwable e) {
            if (log) {
                System.out.println("WARN: Error loading META-INF/services/org/apache/camel/component.properties file due " + e.getMessage());
            }
        }
        return answer;
    }

    public static String extractComponentJavaType(boolean log, ClassLoader classLoader, String scheme) {
        try {
            InputStream is = classLoader.getResourceAsStream("META-INF/services/org/apache/camel/component/" + scheme);
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return (String) props.get("class");
            }
        } catch (Throwable e) {
            if (log) {
                System.out.println("WARN: Error loading META-INF/services/org/apache/camel/component/" + scheme + " file due " + e.getMessage());
            }
        }

        return null;
    }

    public static String loadComponentJSonSchema(boolean log, ClassLoader classLoader, String scheme) {
        String answer = null;

        String path = null;
        String javaType = extractComponentJavaType(log, classLoader, scheme);
        if (javaType != null) {
            int pos = javaType.lastIndexOf(".");
            path = javaType.substring(0, pos);
            path = path.replace('.', '/');
            path = path + "/" + scheme + ".json";
        }

        if (path != null) {
            try {
                InputStream is = classLoader.getResourceAsStream(path);
                if (is != null) {
                    answer = loadText(is);
                }
            } catch (Throwable e) {
                if (log) {
                    System.out.println("WARN: Error loading " + path + " file due " + e.getMessage());
                }
            }
        }

        return answer;
    }
}
