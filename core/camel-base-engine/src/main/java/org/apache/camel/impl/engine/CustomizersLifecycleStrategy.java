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
package org.apache.camel.impl.engine;

import java.util.Comparator;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Ordered;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatCustomizer;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageCustomizer;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.LifecycleStrategySupport;

class CustomizersLifecycleStrategy extends LifecycleStrategySupport {
    private final CamelContext camelContext;

    public CustomizersLifecycleStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onComponentAdd(String name, Component component) {
        Registry registry = this.camelContext.getRegistry();
        if (registry == null) {
            return;
        }

        Set<ComponentCustomizer.Policy> filters = registry.findByType(ComponentCustomizer.Policy.class);
        if (filters.isEmpty() || filters.stream().allMatch(filter -> filter.test(name, component))) {
            registry.findByType(ComponentCustomizer.class).stream()
                    .sorted(Comparator.comparingInt(Ordered::getOrder))
                    .filter(customizer -> customizer.isEnabled(name, component))
                    .forEach(customizer -> customizer.configure(name, component));
        }
    }

    @Override
    public void onDataFormatCreated(String name, DataFormat dataFormat) {
        Registry registry = this.camelContext.getRegistry();
        if (registry == null) {
            return;
        }

        Set<DataFormatCustomizer.Policy> filters = registry.findByType(DataFormatCustomizer.Policy.class);
        if (filters.isEmpty() || filters.stream().allMatch(filter -> filter.test(name, dataFormat))) {
            registry.findByType(DataFormatCustomizer.class).stream()
                    .sorted(Comparator.comparingInt(Ordered::getOrder))
                    .filter(customizer -> customizer.isEnabled(name, dataFormat))
                    .forEach(customizer -> customizer.configure(name, dataFormat));
        }
    }

    @Override
    public void onLanguageCreated(String name, Language language) {
        Registry registry = this.camelContext.getRegistry();
        if (registry == null) {
            return;
        }

        Set<LanguageCustomizer.Policy> filters = registry.findByType(LanguageCustomizer.Policy.class);
        if (filters.isEmpty() || filters.stream().allMatch(filter -> filter.test(name, language))) {
            registry.findByType(LanguageCustomizer.class).stream()
                    .sorted(Comparator.comparingInt(Ordered::getOrder))
                    .filter(customizer -> customizer.isEnabled(name, language))
                    .forEach(customizer -> customizer.configure(name, language));
        }
    }
}
