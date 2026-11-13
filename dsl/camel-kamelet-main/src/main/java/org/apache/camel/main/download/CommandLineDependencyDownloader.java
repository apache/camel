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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.maven.MavenGav;

public class CommandLineDependencyDownloader extends ServiceSupport {

    private final CamelContext camelContext;
    private final DependencyDownloader downloader;
    private final String dependencies;
    private final boolean required;

    public CommandLineDependencyDownloader(CamelContext camelContext, String dependencies) {
        this(camelContext, dependencies, false);
    }

    /**
     * @param required whether a dependency that cannot be downloaded fails (with a {@link DownloadException}), instead
     *                 of logging the error: when running, the application needs the dependencies the user asked for
     */
    public CommandLineDependencyDownloader(CamelContext camelContext, String dependencies, boolean required) {
        this.camelContext = camelContext;
        this.dependencies = dependencies;
        this.required = required;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
    }

    @Override
    protected void doInit() throws Exception {
        downloadDependencies();
    }

    private void downloadDependencies() {
        final List<MavenGav> gavs = new ArrayList<>();
        for (String dep : dependencies.split(",")) {
            dep = dep.trim();
            MavenGav gav = CatalogDependencyResolver.resolve(dep, camelContext.getVersion());
            if (isValidGav(gav)) {
                gavs.add(gav);
            }
        }

        if (!gavs.isEmpty()) {
            for (MavenGav gav : gavs) {
                if (required) {
                    downloader.downloadRequiredDependency(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
                } else {
                    downloader.downloadDependency(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
                }
            }
        }
    }

    private boolean isValidGav(MavenGav gav) {
        boolean exists
                = downloader.alreadyOnClasspath(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
        // valid if not already on classpath
        return !exists;
    }

}
