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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.component.platform.http.PlatformHttpComponent;
import org.apache.camel.impl.engine.DefaultComponentResolver;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * Auto downloaded needed JARs when resolving components.
 */
final class DependencyDownloaderComponentResolver extends DefaultComponentResolver implements CamelContextAware {

    private static final String ACCEPTED_STUB_NAMES = "stub,bean,class,kamelet,rest,rest-api,platform-http,vertx-http";

    private final CamelCatalog catalog = new DefaultCamelCatalog();
    private CamelContext camelContext;
    private final String repos;
    private final boolean fresh;
    private boolean stub;

    public DependencyDownloaderComponentResolver(CamelContext camelContext, String repos, boolean fresh, boolean stub) {
        this.camelContext = camelContext;
        this.repos = repos;
        this.fresh = fresh;
        this.stub = stub;
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
    public Component resolveComponent(String name, CamelContext context) {
        ComponentModel model = catalog.componentModel(name);
        if (model != null && !DownloaderHelper.alreadyOnClasspath(camelContext, model.getGroupId(), model.getArtifactId(),
                model.getVersion())) {
            DownloaderHelper.downloadDependency(camelContext, repos, fresh, model.getGroupId(), model.getArtifactId(),
                    model.getVersion());
        }

        Component answer;
        boolean accept = accept(name);
        if (accept) {
            answer = super.resolveComponent(name, context);
        } else {
            answer = super.resolveComponent("stub", context);
        }

        if (answer instanceof PlatformHttpComponent) {
            // setup a default http server on port 8080 if not already done
            VertxHttpServer.phc = (PlatformHttpComponent) answer;
            VertxHttpServer.registerServer(camelContext, stub);
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
