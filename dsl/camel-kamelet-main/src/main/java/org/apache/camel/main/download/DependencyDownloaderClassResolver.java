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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.impl.engine.DefaultClassResolver;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.PojoBeanModel;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

public final class DependencyDownloaderClassResolver extends DefaultClassResolver {

    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private final List<ResourceResolverListener> resourceResolverListeners = new ArrayList<>();
    private final KnownDependenciesResolver knownDependenciesResolver;
    private final DependencyDownloader downloader;
    private final boolean silent;
    // the known dependencies that could not be downloaded, which are not attempted again
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

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
            if (downloadKnownDependencies(uri)) {
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
            if (downloadKnownDependencies(name)) {
                try {
                    answer = ObjectHelper.loadClass(name, loader);
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        return answer;
    }

    /**
     * Downloads the known dependencies of the class (or resource), such as the serializer of a Kafka schema registry
     * that camel-kafka resolves by name, or a known pojo-bean of the catalog.
     *
     * @return true if the class has known dependencies (which are now on the classpath, unless the download failed)
     */
    private boolean downloadKnownDependencies(String name) {
        List<MavenGav> gavs = knownDependenciesResolver.mavenGavsForClass(name);
        if (gavs.isEmpty()) {
            // okay maybe its a known pojo-bean from the catalog
            // lookup via class name without package
            String last = StringHelper.afterLast(name, ".", name);
            PojoBeanModel model = catalog.pojoBeanModel(last);
            if (model != null && name.equals(model.getJavaType())) {
                gavs = List.of(
                        MavenGav.fromCoordinates(model.getGroupId(), model.getArtifactId(), model.getVersion(), null, null));
            }
        }
        if (gavs.isEmpty()) {
            return false;
        }
        MavenGav first = gavs.get(0);
        String key = first.toString();
        if (failed.contains(key)) {
            // the class is looked up through several class loaders: report a failed download once, not for each
            return false;
        }
        KnownDependenciesResolver.download(downloader, gavs);
        if (!downloader.alreadyOnClasspath(first.getGroupId(), first.getArtifactId(), first.getVersion())) {
            failed.add(key);
        }
        return true;
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
