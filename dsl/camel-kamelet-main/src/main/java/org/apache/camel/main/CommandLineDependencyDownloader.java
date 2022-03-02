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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.support.service.ServiceSupport;

public class CommandLineDependencyDownloader extends ServiceSupport implements CamelContextAware {

    private CamelContext camelContext;
    private final String dependencies;

    public CommandLineDependencyDownloader(String dependencies) {
        this.dependencies = dependencies;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doInit() throws Exception {
        downloadDependencies();
    }

    private void downloadDependencies() {
        final List<String> gavs = new ArrayList<>();
        for (String dep : dependencies.split(",")) {
            String gav = dep;
            if (dep.startsWith("camel:")) {
                // it's a known camel component
                gav = "org.apache.camel:camel-" + dep.substring(6) + ":" + camelContext.getVersion();
            }
            if (isValidGav(gav)) {
                gavs.add(gav);
            }
        }

        if (!gavs.isEmpty()) {
            for (String gav : gavs) {
                MavenGav mg = MavenGav.parseGav(camelContext, gav);
                DownloaderHelper.downloadDependency(camelContext, mg.getGroupId(), mg.getArtifactId(), mg.getVersion());
            }
        }
    }

    private boolean isValidGav(String gav) {
        MavenGav mg = MavenGav.parseGav(camelContext, gav);
        boolean exists = DownloaderHelper.alreadyOnClasspath(camelContext, mg.getArtifactId(), mg.getVersion());
        // valid if not already on classpath
        return !exists;
    }

}
