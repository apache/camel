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

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultPeriodTaskResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.PeriodTaskResolver;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.util.ObjectHelper;

public class DependencyDownloaderPeriodTaskResolver extends DefaultPeriodTaskResolver {

    private final DependencyDownloader downloader;
    private final CamelContext camelContext;
    private final String camelVersion;

    public DependencyDownloaderPeriodTaskResolver(FactoryFinder finder, CamelContext camelContext, String camelVersion) {
        super(finder);
        this.camelContext = camelContext;
        this.camelVersion = camelVersion;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
    }

    @Override
    public Optional<Object> newInstance(String key) {
        maybeDownload(key);

        Optional<Object> answer = super.newInstance(key);
        if (answer.isEmpty()) {
            // need to use regular factory finder as bootstrap has already marked the loader as a miss
            final FactoryFinder finder
                    = camelContext.getCamelContextExtension().getFactoryFinder(PeriodTaskResolver.RESOURCE_PATH);
            Object obj = ResolverHelper.resolveService(camelContext, finder, key, PeriodTaskResolver.class).orElse(null);
            return Optional.ofNullable(obj);
        }
        return answer;
    }

    @Override
    public <T> Optional<T> newInstance(String key, Class<T> type) {
        maybeDownload(key);

        Optional<T> answer = super.newInstance(key, type);
        if (answer.isEmpty()) {
            // need to use regular factory finder as bootstrap has already marked the loader as a miss
            final FactoryFinder finder
                    = camelContext.getCamelContextExtension().getFactoryFinder(PeriodTaskResolver.RESOURCE_PATH);
            T obj = ResolverHelper.resolveService(camelContext, finder, key, type).orElse(null);
            return Optional.ofNullable(obj);
        }
        return answer;
    }

    private void maybeDownload(String key) {
        if ("aws-secret-refresh".equals(key)) {
            downloadLoader("camel-aws-secrets-manager");
        } else if ("gcp-secret-refresh".equals(key)) {
            downloadLoader("camel-google-secret-manager");
        } else if ("azure-secret-refresh".equals(key)) {
            downloadLoader("camel-azure-key-vault");
        } else if ("kubernetes-secret-refresh".equals(key)) {
            downloadLoader("camel-kubernetes");
        } else if ("kubernetes-configmaps-refresh".equals(key)) {
            downloadLoader("camel-kubernetes");
        }
    }

    private void downloadLoader(String artifactId) {
        String resolvedCamelVersion = camelContext.getVersion();
        if (ObjectHelper.isEmpty(resolvedCamelVersion)) {
            resolvedCamelVersion = camelVersion;
        }

        if (!downloader.alreadyOnClasspath("org.apache.camel", artifactId, resolvedCamelVersion)) {
            downloader.downloadDependency("org.apache.camel", artifactId, resolvedCamelVersion);
        }
    }

}
