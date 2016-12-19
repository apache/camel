/**
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
package org.apache.camel.support;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for implementing custom {@link ReloadStrategy} SPI plugins.
 */
public abstract class ReloadStrategySupport extends ServiceSupport implements ReloadStrategy {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private CamelContext camelContext;

    private int succeeded;
    private int failed;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onReloadCamelContext(CamelContext camelContext) {
        log.debug("Reloading CamelContext: {}", camelContext.getName());
        try {
            ServiceHelper.stopService(camelContext);
            ServiceHelper.startService(camelContext);
        } catch (Exception e) {
            failed++;
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        log.info("Reloaded CamelContext: {}", camelContext.getName());

        succeeded++;
    }

    @Override
    public void onReloadRoutes(CamelContext camelContext, String name, InputStream resource) {

        // load the stream in as DOM and find out if its <routes> <route> or <camelContext>
        // and if its <blueprint> <beans> etc and then find inside the <camelContext> and grab what we support re-loading

        log.debug("Reloading CamelContext: {} routes from resource: {}", camelContext.getName(), name);
        // assume the resource is XML routes
        try {
            RoutesDefinition routes = camelContext.loadRoutesDefinition(resource);
            camelContext.addRouteDefinitions(routes.getRoutes());
        } catch (Exception e) {
            failed++;
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        log.info("Reloaded CamelContext: {} routes from resource: {}", camelContext.getName(), name);

        succeeded++;
    }

    @Override
    public int getReloadCounter() {
        return succeeded;
    }

    @Override
    public int getFailedCounter() {
        return failed;
    }
}
