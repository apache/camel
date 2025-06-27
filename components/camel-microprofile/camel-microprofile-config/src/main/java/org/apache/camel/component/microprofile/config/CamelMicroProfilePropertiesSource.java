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
package org.apache.camel.component.microprofile.config;

import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.OrderedProperties;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The microprofile-config component is used for bridging the Eclipse MicroProfile Config with the Properties Component.
 * This allows using configuration management from MicroProfile with Camel.
 */
@JdkService("properties-source-factory")
public class CamelMicroProfilePropertiesSource implements LoadablePropertiesSource {

    @Override
    public String getName() {
        return "CamelMicroProfilePropertiesSource";
    }

    @Override
    public String getProperty(String name) {
        return ConfigProvider.getConfig().getOptionalValue(name, String.class).orElse(null);
    }

    @Override
    public Properties loadProperties() {
        return loadProperties(s -> true);
    }

    @Override
    public Properties loadProperties(Predicate<String> filter) {
        Properties answer = new OrderedProperties();

        for (String name : ConfigProvider.getConfig().getPropertyNames()) {
            if (filter.test(name)) {
                var value = getProperty(name);
                if (value != null) {
                    answer.put(name, getProperty(name));
                }
            }
        }

        return answer;
    }

    @Override
    public void reloadProperties(String location) {
        // noop
    }

    @Override
    public String toString() {
        return "camel-microprofile-config";
    }

}
