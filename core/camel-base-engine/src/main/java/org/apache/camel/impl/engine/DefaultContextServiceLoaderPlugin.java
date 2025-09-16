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

import java.util.ServiceLoader;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.ContextServiceLoaderPluginResolver;
import org.apache.camel.spi.ContextServicePlugin;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Default implementation that automatically discovers and loads {@link ContextServicePlugin} implementations using the
 * Java ServiceLoader mechanism.
 * <p>
 * This service is responsible for scanning the classpath for implementations of {@code ContextServicePlugin} and
 * invoking their {@code load} method during CamelContext startup. Plugin implementations are discovered through service
 * provider configuration files located at: {@code META-INF/services/org.apache.camel.spi.ContextServicePlugin}
 * <p>
 * The loading process occurs during the {@link #doStart()} phase, ensuring that all plugins are initialized before
 * routes are started but after the CamelContext has been created and configured.
 * <p>
 * This class extends {@link ServiceSupport} to participate in the Camel service lifecycle and implements
 * {@link CamelContextAware} to receive the CamelContext instance that plugins will operate on.
 *
 * @see ContextServicePlugin
 * @see ServiceLoader
 */
public class DefaultContextServiceLoaderPlugin extends ServiceSupport implements ContextServiceLoaderPluginResolver {
    private CamelContext camelContext;

    /**
     * Discovers and loads all {@link ContextServicePlugin} implementations found on the classpath.
     * <p>
     * This method is called during service startup and uses {@link ServiceLoader} to automatically discover plugin
     * implementations. Each discovered plugin's {@code load} method is invoked with the current CamelContext, allowing
     * plugins to perform their initialization logic.
     * <p>
     * The plugins are loaded in the order they are discovered by the ServiceLoader, which may vary between JVM
     * implementations and is generally not guaranteed to be deterministic.
     *
     * @throws Exception if any plugin fails to load or throws an exception during initialization
     */
    @Override
    protected void doStart() throws Exception {
        ServiceLoader<ContextServicePlugin> contextServicePlugins = ServiceLoader.load(ContextServicePlugin.class,
                camelContext.getApplicationContextClassLoader());
        for (ContextServicePlugin plugin : contextServicePlugins) {
            plugin.load(camelContext);
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
