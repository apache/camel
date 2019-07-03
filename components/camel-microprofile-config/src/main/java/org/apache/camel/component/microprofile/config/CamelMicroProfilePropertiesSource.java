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

import org.apache.camel.component.properties.PropertiesSource;
import org.apache.camel.support.service.ServiceSupport;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The microprofile-config component is used for bridging the Eclipse MicroProfile Config with Camels
 * properties component. This allows to use configuration management from Eclipse MicroProfile with Camel.
 */
public class CamelMicroProfilePropertiesSource extends ServiceSupport implements PropertiesSource {

    private Config config;

    @Override
    public String getName() {
        return "CamelMicroProfilePropertiesSource";
    }

    @Override
    public String getProperty(String name) {
        if (config == null) {
            config = ConfigProvider.getConfig();
        }
        Optional<String> value = config.getOptionalValue(name, String.class);
        return value.orElse(null);
    }

    @Override
    protected void doStart() throws Exception {
        config = ConfigProvider.getConfig();
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
