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
import org.apache.camel.main.http.VertxHttpServer;
import org.apache.camel.main.util.SuggestSimilarHelper;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * Auto downloaded needed JARs when resolving components.
 */
public final class DependencyDownloaderComponentResolver extends DefaultComponentResolver {

    private static final String ACCEPTED_STUB_NAMES = "stub,bean,class,kamelet,rest,rest-api,platform-http,vertx-http";

    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private final CamelContext camelContext;
    private final DependencyDownloader downloader;
    private final boolean stub;

    public DependencyDownloaderComponentResolver(CamelContext camelContext, boolean stub) {
        this.camelContext = camelContext;
        this.downloader = camelContext.hasService(DependencyDownloader.class);
        this.stub = stub;
    }

    @Override
    public Component resolveComponent(String name, CamelContext context) {
        ComponentModel model = catalog.componentModel(name);
        if (model != null && !downloader.alreadyOnClasspath(model.getGroupId(), model.getArtifactId(),
                model.getVersion())) {
            downloader.downloadDependency(model.getGroupId(), model.getArtifactId(),
                    model.getVersion());
        }

        Component answer;
        boolean accept = accept(name);
        if (accept) {
            answer = super.resolveComponent(name, context);
        } else {
            answer = super.resolveComponent("stub", context);
        }
        if (stub && answer instanceof StubComponent) {
            StubComponent sc = (StubComponent) answer;
            // enable shadow mode on stub component
            sc.setShadow(true);
        }
        if (answer instanceof PlatformHttpComponent) {
            // setup a default http server on port 8080 if not already done
            VertxHttpServer.setPlatformHttpComponent((PlatformHttpComponent) answer);
            VertxHttpServer.registerServer(camelContext, stub);
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

    private boolean accept(String name) {
        // kamelet component must not be stubbed
        if (!stub) {
            return true;
        }

        // we are stubbing but need to accept the following
        return ACCEPTED_STUB_NAMES.contains(name);
    }

}
