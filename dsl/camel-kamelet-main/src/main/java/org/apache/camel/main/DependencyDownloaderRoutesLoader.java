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

import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Auto downloaded needed DSL JARs.
 */
public class DependencyDownloaderRoutesLoader extends DefaultRoutesLoader {

    @Override
    protected RoutesBuilderLoader getRoutesLoader(String extension) throws Exception {
        RoutesBuilderLoader loader = super.getRoutesLoader(extension);
        if (loader == null) {
            loader = resolveService(extension);
        }
        return loader;
    }

    @Override
    protected RoutesBuilderLoader resolveService(String extension) {
        RoutesBuilderLoader loader = super.resolveService(extension);

        if (loader == null) {
            if ("groovy".equals(extension)) {
                downloadLoader("camel-groovy-dsl");
            } else if ("java".equals(extension)) {
                downloadLoader("camel-java-joor-dsl");
            } else if ("js".equals(extension)) {
                downloadLoader("camel-js-dsl");
            } else if ("kts".equals(extension)) {
                downloadLoader("camel-kotlin-dsl");
            } else if ("xml".equals(extension)) {
                downloadLoader("camel-xml-io-dsl");
            } else if ("yaml".equals(extension)) {
                downloadLoader("camel-yaml-dsl");
            }

            // need to use regular factory finder as bootstrap has already marked the loader as a miss
            final ExtendedCamelContext ecc = getCamelContext().adapt(ExtendedCamelContext.class);
            final FactoryFinder finder = ecc.getFactoryFinder(RoutesBuilderLoader.FACTORY_PATH);

            RoutesBuilderLoader answer
                    = ResolverHelper.resolveService(ecc, finder, extension, RoutesBuilderLoader.class).orElse(null);

            if (answer != null) {
                CamelContextAware.trySetCamelContext(answer, getCamelContext());
                ServiceHelper.startService(answer);
            }

            return answer;
        }

        return loader;
    }

    private void downloadLoader(String artifactId) {
        if (!DownloaderHelper.alreadyOnClasspath(getCamelContext(), artifactId)) {
            DownloaderHelper.downloadDependency(getCamelContext(), "org.apache.camel", artifactId,
                    getCamelContext().getVersion());
        }
    }

}
