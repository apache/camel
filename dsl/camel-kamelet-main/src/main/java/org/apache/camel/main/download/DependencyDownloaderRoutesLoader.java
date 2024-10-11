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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.dsl.yaml.KameletRoutesBuilderLoader;
import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Auto downloaded needed DSL JARs.
 */
public class DependencyDownloaderRoutesLoader extends DefaultRoutesLoader {

    private final DependencyDownloader downloader;
    private final String camelVersion;
    private final String kameletsVersion;
    private boolean ignoreUnknownExtensions;

    public DependencyDownloaderRoutesLoader(CamelContext camelContext) {
        this(camelContext, null, null);
    }

    public DependencyDownloaderRoutesLoader(CamelContext camelContext, String camelVersion, String kameletsVersion) {
        setCamelContext(camelContext);
        this.downloader = camelContext.hasService(DependencyDownloader.class);
        this.camelVersion = camelVersion;
        this.kameletsVersion = kameletsVersion;
    }

    public boolean isIgnoreUnknownExtensions() {
        return ignoreUnknownExtensions;
    }

    public void setIgnoreUnknownExtensions(boolean ignoreUnknownExtensions) {
        this.ignoreUnknownExtensions = ignoreUnknownExtensions;
    }

    @Override
    protected RoutesBuilderLoader resolveService(String extension) {
        // we need to eager capture that we use this route loader extension so lets
        // attempt to download it even if its already on classpath
        if ("java".equals(extension)) {
            downloadLoader("camel-java-joor-dsl");
            downloadLoader("camel-endpointdsl");
        } else if ("xml".equals(extension)
                || "camel.xml".equals(extension)) {
            downloadLoader("camel-xml-io-dsl");
        } else if ("yaml".equals(extension)
                || "kamelet.yaml".equals(extension)
                || "camel.yaml".equals(extension)
                || "camelk.yaml".equals(extension)) {
            downloadLoader("camel-yaml-dsl");
        }

        // special for kamelet as we want to track loading kamelets
        RoutesBuilderLoader loader;
        if (KameletRoutesBuilderLoader.EXTENSION.equals(extension)) {
            loader = new KnownKameletRoutesBuilderLoader(kameletsVersion);
            CamelContextAware.trySetCamelContext(loader, getCamelContext());
            // allows for custom initialization
            initRoutesBuilderLoader(loader);
            ServiceHelper.startService(loader);
        } else {
            loader = super.resolveService(extension);
        }
        if (loader == null) {
            // need to use regular factory finder as bootstrap has already marked the loader as a miss
            final CamelContext ecc = getCamelContext();
            final FactoryFinder finder = ecc.getCamelContextExtension().getFactoryFinder(RoutesBuilderLoader.FACTORY_PATH);
            loader = ResolverHelper.resolveService(ecc, finder, extension, RoutesBuilderLoader.class).orElse(null);
            if (loader != null) {
                CamelContextAware.trySetCamelContext(loader, getCamelContext());
                // allows for custom initialization
                initRoutesBuilderLoader(loader);
                ServiceHelper.startService(loader);
            } else if (ignoreUnknownExtensions) {
                // use a dummy loader to avoid camel to fail
                loader = new NoopRoutesBuilderLoader(extension);
            }
        }
        return loader;
    }

    private void downloadLoader(String artifactId) {
        String resolvedCamelVersion = getCamelContext().getVersion();
        if (ObjectHelper.isEmpty(resolvedCamelVersion)) {
            resolvedCamelVersion = camelVersion;
        }

        if (!downloader.alreadyOnClasspath("org.apache.camel", artifactId, resolvedCamelVersion)) {
            downloader.downloadDependency("org.apache.camel", artifactId, resolvedCamelVersion);
        }
    }

}
