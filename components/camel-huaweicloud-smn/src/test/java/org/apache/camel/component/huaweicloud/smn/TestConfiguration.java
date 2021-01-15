package org.apache.camel.component.huaweicloud.smn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class TestConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestConfiguration.class.getName());
    private static Map<String, String> propertyMap;

    public TestConfiguration() {
        initPropertyMap();
    }

    public void initPropertyMap() {
        Properties properties = null;
        if(propertyMap == null) {
            propertyMap = new HashMap<>();
            String propertyFileName = "testconfiguration.properties";
            try {
                properties = new Properties();
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propertyFileName);
                if (inputStream != null) {
                    properties.load(inputStream);
                } else {
                    throw new FileNotFoundException("property file '" + propertyFileName + "' not found in the classpath");
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
        if(propertyMap == null) {
            initPropertyMap();
        }
        return propertyMap.get(key);
    }
}
