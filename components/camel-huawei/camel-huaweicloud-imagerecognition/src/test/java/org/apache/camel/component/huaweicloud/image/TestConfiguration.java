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

package org.apache.camel.component.huaweicloud.image;

import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestConfiguration.class.getName());

    private static Map<String, String> propertyMap;

    public TestConfiguration() {
        initPropertyMap();
    }

    public void initPropertyMap() {
        Properties properties = null;
        if (propertyMap == null) {
            propertyMap = new HashMap<>();
            String propertyFileName = "test_configuration.properties";
            try {
                properties = new Properties();
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propertyFileName);
                if (inputStream != null) {
                    properties.load(inputStream);
                } else {
                    throw new FileNotFoundException(
                            "property file '" + propertyFileName + "' not found in the classpath");
                }

                for (String key : properties.stringPropertyNames()) {
                    propertyMap.put(key, properties.getProperty(key));
                }
            } catch (Exception e) {
                LOGGER.error("Cannot load property file {}, reason {}", propertyFileName, e.getMessage());
            }

        }
    }

    public String getProperty(String key) {
        if (propertyMap == null) {
            initPropertyMap();
        }
        return propertyMap.get(key);
    }
}
