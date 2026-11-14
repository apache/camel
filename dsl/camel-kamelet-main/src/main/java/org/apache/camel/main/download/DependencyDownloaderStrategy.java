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
import org.apache.camel.spi.DependencyStrategy;
import org.apache.camel.tooling.maven.MavenGav;

/**
 * Downloads the dependencies declared in the sources, such as a //DEPS line, or the dependencies of a Kamelet.
 */
public class DependencyDownloaderStrategy implements DependencyStrategy {

    private final CamelContext camelContext;
    private final DependencyDownloader downloader;
    private final boolean required;

    public DependencyDownloaderStrategy(CamelContext camelContext) {
        this(camelContext, false);
    }

    /**
     * @param required whether a dependency that cannot be downloaded fails (with a {@link DownloadException}), instead
     *                 of logging the error: when running, the application needs the dependencies it declares
     */
    public DependencyDownloaderStrategy(CamelContext camelContext, boolean required) {
        this.camelContext = camelContext;
        this.required = required;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
    }

    @Override
    public void onDependency(String dependency) {
        MavenGav gav = CatalogDependencyResolver.resolve(dependency, camelContext.getVersion());
        if (!downloader.alreadyOnClasspath(gav.getGroupId(), gav.getArtifactId(), gav.getVersion())) {
            if (required) {
                downloader.downloadRequiredDependency(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
            } else {
                downloader.downloadDependency(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
            }
        }
    }

}
