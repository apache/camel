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
package org.apache.camel.semantic;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ContextServicePlugin;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.service.ServiceHelper;

/** Installs optional XML declaration support and removes definitions from deleted files before route reload. */
public class SemanticReloadPlugin implements ContextServicePlugin {
    private SemanticXmlLoader xmlLoader;
    private LifecycleStrategy lifecycle;

    @Override
    public void load(CamelContext context) {
        installXmlLoader(context);
        lifecycle = new LifecycleStrategySupport() {
            @Override
            public void onContextInitializing(CamelContext camelContext) {
                // Applications can replace the registry after the context's eager build phase.
                installXmlLoader(camelContext);
                if (xmlLoader != null) {
                    xmlLoader.resetLoaderDiscovery();
                }
            }
        };
        context.addLifecycleStrategy(lifecycle);
    }

    private void installXmlLoader(CamelContext context) {
        // Keep the language usable without the optional XML/model dependencies, and preserve application overrides.
        if (context.getClassResolver().resolveClass("org.apache.camel.xml.in.ModelParser") == null
                || context.getRegistry().lookupByName(SemanticXmlLoader.REGISTRY_KEY) != null
                || context.getRegistry().findByType(RoutesBuilderLoader.class).stream()
                        .anyMatch(loader -> loader.isSupportedExtension("xml"))) {
            return;
        }
        if (xmlLoader == null) {
            xmlLoader = new SemanticXmlLoader();
            xmlLoader.setCamelContext(context);
        }
        try {
            ServiceHelper.startService(xmlLoader);
            context.getRegistry().bind(SemanticXmlLoader.REGISTRY_KEY, xmlLoader);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

    @Override
    public void unload(CamelContext context) {
        context.getLifecycleStrategies().remove(lifecycle);
        lifecycle = null;
        if (xmlLoader != null) {
            if (context.getRegistry().lookupByName(SemanticXmlLoader.REGISTRY_KEY) == xmlLoader) {
                context.getRegistry().unbind(SemanticXmlLoader.REGISTRY_KEY);
            }
            try {
                ServiceHelper.stopAndShutdownService(xmlLoader);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            } finally {
                xmlLoader = null;
            }
        }
    }

    @Override
    public void onReload(CamelContext context) {
        if (xmlLoader != null) {
            xmlLoader.resetLoaderDiscovery();
        }
        SemanticQuestions questions = context.getCamelContextExtension().getContextPlugin(SemanticQuestions.class);
        if (questions != null) {
            questions.removeDeletedResources();
        }
    }
}
