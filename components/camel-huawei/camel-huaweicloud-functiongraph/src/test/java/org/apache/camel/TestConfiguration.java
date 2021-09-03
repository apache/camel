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
package org.apache.camel;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(TestConfiguration.class.getName());
    private static Map<String, String> propertyMap;

    public TestConfiguration() {
        initPropertyMap();
    }

    public void initPropertyMap() {
        Properties properties = null;
        if (propertyMap == null) {
            propertyMap = new HashMap<>();
            String fileName = "testconfiguration.properties";
            try {
                properties = new Properties();
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
                if (inputStream != null) {
                    properties.load(inputStream);
                } else {
                    throw new FileNotFoundException("property file '" + fileName + "' not found in the classpath");
                }

                for (String key : properties.stringPropertyNames()) {
                    propertyMap.put(key, properties.getProperty(key));
                }
            } catch (Exception e) {
                LOG.error("Cannot load property file {}, reason {}", fileName, e.getMessage());
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
