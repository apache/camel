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
package org.apache.camel.main.download;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Configuration;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.support.PluginHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasePackageScanDownloadListener implements ArtifactDownloadListener, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(BasePackageScanDownloadListener.class);

    private CamelContext camelContext;

    private final Set<String> scanned = new HashSet<>();

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onDownloadedFile(File file) {
        String basePackage = camelContext.getCamelContextExtension().getBasePackageScan();
        if (basePackage != null) {
            try {
                basePackageScanConfiguration(basePackage, file);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    protected void basePackageScanConfiguration(String basePackage, File file) throws Exception {
        Collection<CamelConfiguration> configs = new ArrayList<>();
        // we only want to scan via isolated classloader
        PackageScanClassResolver pscr = PluginHelper.getPackageScanClassResolver(camelContext);
        Set<Class<?>> found1 = pscr.findImplementations(CamelConfiguration.class, basePackage);
        Set<Class<?>> found2 = pscr.findAnnotated(Configuration.class, basePackage);
        Set<Class<?>> found = new LinkedHashSet<>();
        found.addAll(found1);
        found.addAll(found2);
        for (Class<?> clazz : found) {
            // avoid duplicate if we scan other JARs that can same class from previous downloads
            String fqn = clazz.getName();
            if (scanned.contains(fqn)) {
                continue;
            } else {
                scanned.add(fqn);
            }

            // lets use Camel's injector so the class has some support for dependency injection
            Object config = camelContext.getInjector().newInstance(clazz);
            if (config instanceof CamelConfiguration) {
                LOG.debug("Discovered CamelConfiguration class: {}", clazz);
                CamelConfiguration cc = (CamelConfiguration) config;
                configs.add(cc);
            }
        }

        CamelBeanPostProcessor postProcessor = PluginHelper.getBeanPostProcessor(camelContext);
        // prepare the directly configured instances
        for (Object configuration : configs) {
            postProcessor.postProcessBeforeInitialization(configuration, configuration.getClass().getName());
            postProcessor.postProcessAfterInitialization(configuration, configuration.getClass().getName());
        }
        // invoke configure on configurations
        for (CamelConfiguration config : configs) {
            config.configure(camelContext);
        }
    }

}
