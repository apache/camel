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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.ObjectHelper;

public final class DependencyDownloaderClassResolver extends DefaultClassResolver {

    private final List<ResourceResolverListener> resourceResolverListeners = new ArrayList<>();
    private final KnownDependenciesResolver knownDependenciesResolver;
    private final DependencyDownloader downloader;
    private final boolean silent;

    public DependencyDownloaderClassResolver(CamelContext camelContext,
                                             KnownDependenciesResolver knownDependenciesResolver,
                                             boolean silent) {
        super(camelContext);
        this.downloader = camelContext.hasService(DependencyDownloader.class);
        this.knownDependenciesResolver = knownDependenciesResolver;
        this.silent = silent;
        this.resourceResolverListeners.add(new KNativeHttpServerFactory());
    }

    @Override
    public InputStream loadResourceAsStream(String uri) {
        resourceResolverListeners.forEach(l -> l.onLoadResourceAsStream(uri));

        InputStream answer = null;
        try {
            answer = super.loadResourceAsStream(uri);
        } catch (Exception e) {
            // uri cannot be loaded, likely need maven GAV
        }

        if (answer == null) {
            // okay maybe the class is from a known GAV that we can download first and then load the class
            MavenGav gav = knownDependenciesResolver.mavenGavForClass(uri);
            if (gav != null) {
                if (!downloader.alreadyOnClasspath(gav.getGroupId(), gav.getArtifactId(),
                        gav.getVersion())) {
                    downloader.downloadDependency(gav.getGroupId(), gav.getArtifactId(),
                            gav.getVersion());
                }
                try {
                    answer = super.loadResourceAsStream(uri);
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        return answer;
    }

    @Override
    protected Class<?> loadClass(String name, ClassLoader loader) {
        Class<?> answer = null;
        try {
            answer = ObjectHelper.loadClass(name, loader);
        } catch (Exception e) {
            // class cannot be loaded, likely need maven GAV
        }

        if (answer == null) {
            // okay maybe the class is from a known GAV that we can download first and then load the class
            MavenGav gav = knownDependenciesResolver.mavenGavForClass(name);
            if (gav != null) {
                if (!downloader.alreadyOnClasspath(gav.getGroupId(), gav.getArtifactId(),
                        gav.getVersion())) {
                    downloader.downloadDependency(gav.getGroupId(), gav.getArtifactId(),
                            gav.getVersion());
                }
                try {
                    answer = ObjectHelper.loadClass(name, loader);
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        return answer;
    }

    private class KNativeHttpServerFactory implements ResourceResolverListener {

        @Override
        public void onLoadResourceAsStream(String uri) {
            try {
                if ("META-INF/services/org/apache/camel/knative/transport/http-consumer".equals(uri)) {
                    MainHttpServerFactory.setupHttpServer(getCamelContext(), silent);
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

}
