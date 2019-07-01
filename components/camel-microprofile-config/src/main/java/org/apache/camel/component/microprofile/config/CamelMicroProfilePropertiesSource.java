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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesSource;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.OrderedProperties;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To use Camel's {@link PropertiesComponent} as an Eclipse {@link ConfigSource}.
 */
public class CamelMicroProfilePropertiesSource extends ServiceSupport implements CamelContextAware, PropertiesSource {

    private static final Logger LOG = LoggerFactory.getLogger(CamelMicroProfilePropertiesSource.class);

    private CamelContext camelContext;
    private final Properties properties = new OrderedProperties();

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
        return "CamelMicroProfilePropertiesSource";
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
