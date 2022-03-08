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
package org.apache.camel.dsl.yaml;

import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;

/**
 * {@link PropertiesSource} for camel-k integration spec/configuration values.
 */
public class IntegrationConfigurationPropertiesSource implements PropertiesSource, CamelContextAware {

    private final String name;
    private final Properties properties = new OrderedProperties();
    private CamelContext camelContext;

    public IntegrationConfigurationPropertiesSource(String name) {
        this.name = name;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    public void parseConfigurationValue(String line) {
        if (line.contains("=")) {
            String key = StringHelper.before(line, "=").trim();
            String value = StringHelper.after(line, "=").trim();
            properties.setProperty(key, value);
        } else {
            if (ResourceHelper.hasScheme(line)) {
                // it is a properties file so load resource
                try (InputStream is = ResourceHelper.resolveResourceAsInputStream(camelContext, line)) {
                    Properties prop = new Properties();
                    prop.load(is);
                    for (String k : prop.stringPropertyNames()) {
                        String v = prop.getProperty(k);
                        String key = k.trim();
                        String value = v.trim();
                        properties.setProperty(key, value);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public String toString() {
        return "camel-yaml-dsl";
    }
}
