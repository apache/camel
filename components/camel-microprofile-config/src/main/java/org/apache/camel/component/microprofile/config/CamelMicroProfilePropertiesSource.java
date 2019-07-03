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

import java.util.Optional;
import java.util.Properties;

import org.apache.camel.component.properties.LoadablePropertiesSource;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.OrderedProperties;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The microprofile-config component is used for bridging the Eclipse MicroProfile Config with Camels
 * properties component. This allows to use configuration management from Eclipse MicroProfile with Camel.
 */
public class CamelMicroProfilePropertiesSource extends ServiceSupport implements LoadablePropertiesSource {

    // TODO: Should not be loadable but regular source so we lookup on demand

    private static final Logger LOG = LoggerFactory.getLogger(CamelMicroProfilePropertiesSource.class);

    private final Properties properties = new OrderedProperties();

    @Override
    public String getName() {
        return "CamelMicroProfilePropertiesSource";
    }

    @Override
    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    @Override
    public Properties loadProperties() {
        return properties;
    }

    @Override
    protected void doInit() throws Exception {
        Config config = ConfigProvider.getConfig();

        for (String name : config.getPropertyNames()) {
            Optional<String> value = config.getOptionalValue(name, String.class);
            value.ifPresent(s -> properties.put(name, s));
        }

        LOG.info("Initialized CamelMicroProfilePropertiesSource with {} properties loaded", properties.size());
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
