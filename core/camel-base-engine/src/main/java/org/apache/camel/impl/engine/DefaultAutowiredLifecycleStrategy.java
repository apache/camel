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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Ordered;
import org.apache.camel.spi.AutowiredLifecycleStrategy;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.camel.support.LifecycleStrategySupport;

class DefaultAutowiredLifecycleStrategy extends LifecycleStrategySupport implements AutowiredLifecycleStrategy, Ordered {
    private final CamelContext camelContext;

    public DefaultAutowiredLifecycleStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public int getOrder() {
        // we should be last
        return Ordered.LOWEST;
    }

    @Override
    public void onComponentAdd(String name, Component component) {
        autowireComponent(name, component);
    }

    @Override
    public void onDataFormatCreated(String name, DataFormat dataFormat) {
        autowireDataFormat(name, dataFormat);
    }

    @Override
    public void onLanguageCreated(String name, Language language) {
        autowireLanguage(name, language);
    }

    private void autowireComponent(String name, Component component) {
        // autowiring can be turned off on context level and per component
        boolean enabled = camelContext.isAutowiredEnabled() && component.isAutowiredEnabled();
        if (enabled) {
            autowire(name, "component", component);
        }
    }

    private void autowireDataFormat(String name, DataFormat dataFormat) {
        // autowiring can be turned off on context level
        boolean enabled = camelContext.isAutowiredEnabled();
        if (enabled) {
            autowire(name, "dataformat", dataFormat);
        }
    }

    private void autowireLanguage(String name, Language language) {
        // autowiring can be turned off on context level
        boolean enabled = camelContext.isAutowiredEnabled();
        if (enabled) {
            autowire(name, "language", language);
        }
    }

    private void autowire(String name, String kind, Object target) {
        doAutoWire(name, kind, target, camelContext);
    }

}
