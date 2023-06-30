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
import org.apache.camel.spi.ResourceReload;
import org.apache.camel.spi.ResourceReloadStrategy;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Base class for implementing custom {@link ResourceReloadStrategy} SPI plugins.
 */
public abstract class ResourceReloadStrategySupport extends ServiceSupport implements ResourceReloadStrategy {

    private ResourceReload resourceReload;
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
    public ResourceReload getResourceReload() {
        return resourceReload;
    }

    @Override
    public void setResourceReload(ResourceReload resourceReload) {
        this.resourceReload = resourceReload;
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
