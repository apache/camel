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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.kamelet.KameletComponent;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RouteTemplateLoaderListener;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;

/**
 * To automatic downloaded dependencies that Kamelets requires.
 */
final class DependencyDownloaderKamelet extends ServiceSupport implements CamelContextAware, RouteTemplateLoaderListener {

    private final KameletYamlRoutes downloader = new KameletYamlRoutes("yaml");
    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doBuild() throws Exception {
        KameletComponent kc = camelContext.getComponent("kamelet", KameletComponent.class);
        kc.setRouteTemplateLoaderListener(this);

        downloader.setCamelContext(camelContext);
        ServiceHelper.buildService(downloader);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(downloader);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(downloader);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(downloader);
    }

    @Override
    public void loadRouteTemplate(Resource resource) {
        if (resource.getLocation().endsWith(".yaml")) {
            try {
                downloader.doLoadRouteBuilder(resource);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
    }
}
