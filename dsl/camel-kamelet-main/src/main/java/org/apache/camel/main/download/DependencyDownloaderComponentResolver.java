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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.component.stub.StubComponent;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.main.util.SuggestSimilarHelper;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * Auto downloaded needed JARs when resolving components.
 */
public final class DependencyDownloaderComponentResolver extends DefaultComponentResolver {

    private static final String ACCEPTED_STUB_NAMES
            = "stub,bean,class,direct,kamelet,log,platform-http,rest,rest-api,seda,vertx-http";

    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private final CamelContext camelContext;
    private final DependencyDownloader downloader;
    private final String stubPattern;
    private final boolean silent;

    public DependencyDownloaderComponentResolver(CamelContext camelContext, String stubPattern, boolean silent) {
        this.camelContext = camelContext;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
        this.stubPattern = stubPattern;
        this.silent = silent;
    }

    @Override
    public Component resolveComponent(String name, CamelContext context) {
        ComponentModel model = catalog.componentModel(name);
        if (model != null) {
            downloadLoader(model.getGroupId(), model.getArtifactId(), model.getVersion());
        }

        Component answer;
        boolean accept = accept(name);
        if (accept) {
            answer = super.resolveComponent(name, context);
        } else {
            answer = super.resolveComponent("stub", context);
        }
        if ((silent || stubPattern != null) && answer instanceof StubComponent) {
            StubComponent sc = (StubComponent) answer;
            // enable shadow mode on stub component
            sc.setShadow(true);
            sc.setShadowPattern(stubPattern);
        }
        if (answer instanceof PlatformHttpComponent) {
            MainHttpServerFactory.setupHttpServer(camelContext, silent);
        }
        if (answer == null) {
            List<String> suggestion = SuggestSimilarHelper.didYouMean(catalog.findComponentNames(), name);
            if (suggestion != null && !suggestion.isEmpty()) {
                String s = String.join(", ", suggestion);
                throw new IllegalArgumentException("Cannot find component with name: " + name + ". Did you mean: " + s);
            }
        }
        return answer;
    }

    private void downloadLoader(String groupId, String artifactId, String version) {
        if (!downloader.alreadyOnClasspath(groupId, artifactId, version)) {
            downloader.downloadDependency(groupId, artifactId, version);
        }
    }

    private boolean accept(String name) {
        if (stubPattern == null) {
            return true;
        }

        // we are stubbing but need to accept the following
        return ACCEPTED_STUB_NAMES.contains(name);
    }

}
