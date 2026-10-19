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
package org.apache.camel.main;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.spi.PropertiesReload;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.OrderedLocationProperties;

/**
 * Reloading of application.properties when using Camel Main in reload mode, such as when using camel-jbang.
 */
public class MainPropertiesReload extends ServiceSupport
        implements StaticService, NonManagedService, PropertiesReload, CamelContextAware {

    private final BaseMainSupport main;
    private CamelContext camelContext;

    public MainPropertiesReload(BaseMainSupport main) {
        this.main = main;
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
    public void onReload(String name, Properties properties) throws Exception {
        // such as a context reload, which applies the options with a property placeholder again (secret rotation)
        if (properties instanceof OrderedLocationProperties prop) {
            main.autoConfigurationFromReloadedProperties(camelContext, prop, false);
            main.autowireWildcardProperties(camelContext);
        }
    }

    @Override
    public void onChanged(String name, Properties properties) throws Exception {
        // a properties file that changed, such as application.properties in dev mode: the properties that changed
        if (properties instanceof OrderedLocationProperties prop) {
            main.autoConfigurationFromReloadedProperties(camelContext, prop, true);
            main.autowireWildcardProperties(camelContext);
        }
    }
}
