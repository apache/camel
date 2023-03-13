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
import org.apache.camel.support.PropertyBindingListener;
import org.apache.camel.tooling.maven.MavenGav;

public class DependencyDownloaderPropertyBindingListener implements PropertyBindingListener {

    private final CamelContext camelContext;
    private final KnownDependenciesResolver knownDependenciesResolver;
    private final DependencyDownloader downloader;

    public DependencyDownloaderPropertyBindingListener(CamelContext camelContext,
                                                       KnownDependenciesResolver knownDependenciesResolver) {
        this.camelContext = camelContext;
        this.knownDependenciesResolver = knownDependenciesResolver;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
    }

    @Override
    public void bindProperty(Object target, String key, Object value) {
        if (value instanceof String) {
            String s = (String) value;
            MavenGav gav = knownDependenciesResolver.mavenGavForClass(s);
            if (gav != null) {
                if (!downloader.alreadyOnClasspath(gav.getGroupId(), gav.getArtifactId(),
                        gav.getVersion())) {
                    downloader.downloadDependency(gav.getGroupId(), gav.getArtifactId(),
                            gav.getVersion());
                }
            }
        }
    }

}
