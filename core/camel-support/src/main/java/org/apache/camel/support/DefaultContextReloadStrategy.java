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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ContextReloadStrategy}.
 */
public class DefaultContextReloadStrategy extends ServiceSupport implements ContextReloadStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultContextReloadStrategy.class);

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

    @ManagedOperation(description = "Trigger on-demand reloading")
    public void onReload() {
        onReload("JMX Management");
    }

    @Override
    public void onReload(Object source) {
        LOG.info("Reloading CamelContext ({}) triggered by: {}", camelContext.getName(), source);
        try {
            EventHelper.notifyContextReloading(getCamelContext(), source);
            reloadProperties(source);
            reloadRoutes(source);
            incSucceededCounter();
            EventHelper.notifyContextReloaded(getCamelContext(), source);
        } catch (Exception e) {
            incFailedCounter();
            LOG.warn("Error reloading CamelContext ({}) due to: {}", camelContext.getName(), e.getMessage(), e);
            EventHelper.notifyContextReloadFailure(getCamelContext(), source, e);
        }
    }

    protected void reloadRoutes(Object source) throws Exception {
        getCamelContext().getRouteController().reloadAllRoutes();
    }

    protected void reloadProperties(Object source) throws Exception {
        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        for (PropertiesSource ps : pc.getPropertiesSources()) {
            // reload by restarting
            ServiceHelper.stopAndShutdownService(ps);
            ServiceHelper.startService(ps);
        }
    }

    @ManagedAttribute(description = "Number of reloads succeeded")
    public int getReloadCounter() {
        return succeeded;
    }

    @ManagedAttribute(description = "Number of reloads failed")
    public int getFailedCounter() {
        return failed;
    }

    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    @ManagedOperation(description = "Reset counters")
    public void resetCounters() {
        succeeded = 0;
        failed = 0;
    }

    protected void incSucceededCounter() {
        succeeded++;
    }

    protected void incFailedCounter() {
        failed++;
    }

}
