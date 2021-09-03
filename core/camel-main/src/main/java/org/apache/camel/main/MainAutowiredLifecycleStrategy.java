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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.AutowiredLifecycleStrategy;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.LifecycleStrategySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainAutowiredLifecycleStrategy extends LifecycleStrategySupport implements AutowiredLifecycleStrategy, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(MainAutowiredLifecycleStrategy.class);

    // provisional maps to hold components, dataformats, languages that are created during
    // starting camel, but need to defer autowiring until later in case additional configuration
    // would turn off autowired for some components
    private final Map<String, Component> autowrieComponents = new HashMap<>();
    private final Map<String, DataFormat> autowrieDataFormats = new HashMap<>();
    private final Map<String, Language> autowrieLanguages = new HashMap<>();
    private final ExtendedCamelContext camelContext;
    private volatile boolean initializing;

    public MainAutowiredLifecycleStrategy(CamelContext camelContext) {
        this.camelContext = (ExtendedCamelContext) camelContext;
    }

    @Override
    public int getOrder() {
        // we should be last
        return Ordered.LOWEST;
    }

    @Override
    public void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
        // we have parsed configuration (such as via camel-main) and are now initializing
        // so lets do autowiring on what we have collected so far
        // we also need to eager autowire components as when they create endpoints they must be configured with
        // those autowired options
        autowrieComponents.forEach(this::autowireComponent);
        autowrieDataFormats.forEach(this::autowireDataFormat);
        autowrieLanguages.forEach(this::autowireLanguage);
        autowrieComponents.clear();
        autowrieDataFormats.clear();
        autowrieLanguages.clear();
        initializing = true;
    }

    @Override
    public void onContextStopped(CamelContext context) {
        initializing = false;
    }

    @Override
    public void onComponentAdd(String name, Component component) {
        if (initializing) {
            autowireComponent(name, component);
        } else {
            autowrieComponents.put(name, component);
        }
    }

    @Override
    public void onDataFormatCreated(String name, DataFormat dataFormat) {
        if (initializing) {
            autowireDataFormat(name, dataFormat);
        } else {
            autowrieDataFormats.put(name, dataFormat);
        }
    }

    @Override
    public void onLanguageCreated(String name, Language language) {
        if (initializing) {
            autowireLanguage(name, language);
        } else {
            autowrieLanguages.put(name, language);
        }
    }

    private void autowireComponent(String name, Component component) {
        // autowiring can be turned off on context level and per component
        boolean enabled = camelContext.isAutowiredEnabled() && component.isAutowiredEnabled();
        if (enabled) {
            autwire(name, "component", component);
        }
    }

    private void autowireDataFormat(String name, DataFormat dataFormat) {
        // autowiring can be turned off on context level
        boolean enabled = camelContext.isAutowiredEnabled();
        if (enabled) {
            autwire(name, "dataformat", dataFormat);
        }
    }

    private void autowireLanguage(String name, Language language) {
        // autowiring can be turned off on context level
        boolean enabled = camelContext.isAutowiredEnabled();
        if (enabled) {
            autwire(name, "language", language);
        }
    }

    private void autwire(String name, String kind, Object target) {
        PropertyConfigurer pc = camelContext.getConfigurerResolver().resolvePropertyConfigurer(name + "-" + kind, camelContext);
        if (pc instanceof PropertyConfigurerGetter) {
            PropertyConfigurerGetter getter = (PropertyConfigurerGetter) pc;
            String[] names = getter.getAutowiredNames();
            if (names != null) {
                for (String option : names) {
                    // is there already a configured value?
                    Object value = getter.getOptionValue(target, option, true);
                    if (value == null) {
                        Class<?> type = getter.getOptionType(option, true);
                        if (type != null) {
                            Set<?> set = camelContext.getRegistry().findByType(type);
                            if (set.size() == 1) {
                                value = set.iterator().next();
                            }
                        }
                        if (value != null) {
                            boolean hit = pc.configure(camelContext, target, option, value, true);
                            if (hit) {
                                LOG.info(
                                        "Autowired property: {} on {}: {} as exactly one instance of type: {} ({}) found in the registry",
                                        option, kind, name, type.getName(), value.getClass().getName());
                            }
                        }
                    }
                }
            }
        }
    }

}
