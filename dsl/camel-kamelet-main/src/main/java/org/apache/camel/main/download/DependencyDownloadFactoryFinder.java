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
import org.apache.camel.impl.engine.DefaultFactoryFinder;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.tooling.maven.MavenGav;

public class DependencyDownloadFactoryFinder extends DefaultFactoryFinder {

    private final KnownDependenciesResolver knownDependenciesResolver;
    private final DependencyDownloader downloader;

    public DependencyDownloadFactoryFinder(CamelContext camelContext, ClassResolver classResolver, String resourcePath,
                                           KnownDependenciesResolver knownDependenciesResolver) {
        super(classResolver, resourcePath);
        this.knownDependenciesResolver = knownDependenciesResolver;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
    }

    @Override
    public Optional<Class<?>> findClass(String key) {
        // this is not optional so we can auto download the JAR as it's intended to be on the classpath
        MavenGav gav = knownDependenciesResolver.mavenGavForClass(FactoryFinder.DEFAULT_PATH + key);
        if (gav != null) {
            downloadLoader(gav.getGroupId(), gav.getArtifactId(), gav.getVersion());
        }
        return super.findClass(key);
    }

    private void downloadLoader(String groupId, String artifactId, String version) {
        if (!downloader.alreadyOnClasspath(groupId, artifactId, version)) {
            downloader.downloadDependency(groupId, artifactId, version);
        }
    }

}
