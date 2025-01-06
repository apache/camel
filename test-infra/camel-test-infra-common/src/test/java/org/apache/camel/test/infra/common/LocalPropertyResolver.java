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
package org.apache.camel.test.infra.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LocalPropertyResolver {

    public static final String CONTAINER_PROPERTIES_FILE_NAME = "container.properties";

    /**
     * Resolves the container to use. Platform-specific containers are resolved automatically
     *
     * @param  propertyName The property name pointing to the container
     * @return
     */
    public static String getProperty(Class<?> clazz, String propertyName) {
        return System.getProperty(propertyName, getPropertyFromContainersPropertiesFile(clazz, propertyName));
    }

    private static String getPropertyFromContainersPropertiesFile(Class<?> clazz, String propertyName) {
        Properties properties = new Properties();

        try (InputStream inputStream = clazz.getResourceAsStream(CONTAINER_PROPERTIES_FILE_NAME)) {
            properties.load(inputStream);
        } catch (IOException e) {
            String errorMessage = "Error when reading file " + CONTAINER_PROPERTIES_FILE_NAME
                                  + " for class " + clazz.getCanonicalName();
            throw new RuntimeException(errorMessage, e);
        }

        return resolveProperty(properties, propertyName);
    }

    private static String resolveProperty(Properties properties, String propertyName) {
        // This should append the 'os.arch' version to the property (typically, one of amd64, aarch64, ppc64le or s390x)
        String platformProperty = String.format("%s.%s", propertyName, System.getProperty("os.arch"));

        // Platform properties take precedence
        String value = properties.getProperty(platformProperty);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        return properties.getProperty(propertyName);
    }

}
