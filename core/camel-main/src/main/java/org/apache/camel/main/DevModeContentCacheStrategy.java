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

import org.apache.camel.Component;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.LifecycleStrategySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle strategy that disables {@code contentCache} on resource-based components when routes-reload is enabled, so
 * users can edit a resource file (e.g. an XSLT stylesheet, FreeMarker template) and see the change applied live.
 *
 * Components are detected via their generated {@link PropertyConfigurer}: any component whose configurer exposes a
 * {@code contentCache} option currently evaluating to {@link Boolean#TRUE} is flipped to {@code false}. User properties
 * applied later (e.g. {@code camel.component.<name>.contentCache=true}) will override this default.
 */
class DevModeContentCacheStrategy extends LifecycleStrategySupport {

    private static final Logger LOG = LoggerFactory.getLogger(DevModeContentCacheStrategy.class);

    private static final String CONTENT_CACHE = "contentCache";

    @Override
    public void onComponentAdd(String name, Component component) {
        PropertyConfigurer configurer = component.getComponentPropertyConfigurer();
        if (!(configurer instanceof PropertyConfigurerGetter getter)) {
            return;
        }
        Object value = getter.getOptionValue(component, CONTENT_CACHE, true);
        if (Boolean.TRUE.equals(value)
                && configurer.configure(component.getCamelContext(), component, CONTENT_CACHE, Boolean.FALSE, true)) {
            LOG.debug("Routes-reload is enabled: disabling contentCache on component '{}' for live resource reload",
                    name);
        }
    }
}
