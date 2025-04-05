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
package org.apache.camel.component.spring.cloud.config;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.service.ServiceSupport;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.core.env.PropertySource;

/**
 * A periodic task that monitors Spring Cloud Config properties for changes and triggers a Camel context reload when
 * property values have changed.
 * <p>
 * It compares the current property values in Spring Cloud Config with cached values stored in
 * {@link SpringConfigRemaindersCache}. When a property value has changed, it triggers the reload of the Camel context
 * using the {@link ContextReloadStrategy} service.
 * </p>
 *
 * @see ContextReloadStrategy
 * @see SpringConfigRemaindersCache
 * @see SpringConfigProvider
 */
@PeriodicTask("spring-config-refresh")
public class SpringCloudConfigReloadTriggerTask extends ServiceSupport implements CamelContextAware, Runnable {

    private CamelContext camelContext;

    @Override
    public void run() {
        if (SpringConfigRemaindersCache.get().isEmpty()) {
            return;
        }

        ConfigData configData = new SpringConfigProvider().getConfigData(getCamelContext());

        for (Map.Entry<String, String> entry : SpringConfigRemaindersCache.get().entrySet()) {
            for (PropertySource propertySource : configData.getPropertySources()) {
                if (propertySource.containsProperty(entry.getKey())
                        && !propertySource.getProperty(entry.getKey()).equals(entry.getValue())) {
                    ContextReloadStrategy reload = camelContext.hasService(ContextReloadStrategy.class);
                    if (reload != null) {
                        // trigger reload
                        reload.onReload(this);
                    }
                }
            }
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }
}
