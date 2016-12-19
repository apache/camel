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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.model.ModelHelper;
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
        log.debug("Reloading CamelContext: {} routes from resource: {}", camelContext.getName(), name);

        Document dom = camelContext.getTypeConverter().tryConvertTo(Document.class, resource);
        if (dom == null) {
            failed++;
            log.warn("Cannot load the resource " + name + " as XML");
            return;
        }

        // find the <routes> root
        NodeList list = dom.getElementsByTagName("routes");
        if (list == null || list.getLength() == 0) {
            // fallback to <route>
            list = dom.getElementsByTagName("route");
        }

        if (list != null && list.getLength() > 0) {
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);
                try {
                    RoutesDefinition routes = ModelHelper.loadRoutesDefinition(camelContext, node);
                    camelContext.addRouteDefinitions(routes.getRoutes());
                } catch (Exception e) {
                    failed++;
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }

            }
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

    @Override
    public void resetCounters() {
        succeeded = 0;
        failed = 0;
    }
}
