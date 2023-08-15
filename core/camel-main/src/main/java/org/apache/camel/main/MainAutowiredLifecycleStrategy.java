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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Ordered;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.AutowiredLifecycleStrategy;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.camel.support.LifecycleStrategySupport;

public class MainAutowiredLifecycleStrategy extends LifecycleStrategySupport implements AutowiredLifecycleStrategy, Ordered {

    // provisional maps to hold components, dataformats, languages that are created during
    // starting camel, but need to defer autowiring until later in case additional configuration
    // would turn off autowired for some components
    private final Map<String, Component> autowrieComponents = new HashMap<>();
    private final Map<String, DataFormat> autowrieDataFormats = new HashMap<>();
    private final Map<String, Language> autowrieLanguages = new HashMap<>();
    private final CamelContext camelContext;
    private volatile boolean initializing;

    public MainAutowiredLifecycleStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
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

    protected boolean isEnabled(String name, Component component) {
        return camelContext.isAutowiredEnabled() && component.isAutowiredEnabled();
    }

    protected boolean isEnabled(String name, Language language) {
        // autowiring can be turned off on context level
        return camelContext.isAutowiredEnabled();
    }

    protected boolean isEnabled(String name, DataFormat dataFormat) {
        // autowiring can be turned off on context level
        return camelContext.isAutowiredEnabled();
    }

    private void autowireComponent(String name, Component component) {
        if (isEnabled(name, component)) {
            autowire(name, "component", component);
        }
    }

    private void autowireDataFormat(String name, DataFormat dataFormat) {
        if (isEnabled(name, dataFormat)) {
            autowire(name, "dataformat", dataFormat);
        }
    }

    private void autowireLanguage(String name, Language language) {
        if (isEnabled(name, language)) {
            autowire(name, "language", language);
        }
    }

    private void autowire(String name, String kind, Object target) {
        doAutoWire(name, kind, target, camelContext);
    }

}
