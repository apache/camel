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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedReloadStrategyMBean;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ReloadStrategy;

/**
 * @version 
 */
@ManagedResource(description = "Managed ReloadStrategy")
public class ManagedReloadStrategy extends ManagedService implements ManagedReloadStrategyMBean {

    private final CamelContext camelContext;
    private final ReloadStrategy reloadStrategy;

    public ManagedReloadStrategy(CamelContext camelContext, ReloadStrategy reloadStrategy) {
        super(camelContext, reloadStrategy);
        this.camelContext = camelContext;
        this.reloadStrategy = reloadStrategy;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public ReloadStrategy getReloadStrategy() {
        return reloadStrategy;
    }

    @Override
    public void forceReloadCamelContext() {
        reloadStrategy.onReloadCamelContext(getContext());
    }

    @Override
    public String getStrategy() {
        return reloadStrategy.getClass().getSimpleName();
    }

    @Override
    public int getReloadCounter() {
        return reloadStrategy.getReloadCounter();
    }

    @Override
    public int getFailedCounter() {
        return reloadStrategy.getFailedCounter();
    }

    @Override
    public void resetCounters() {
        reloadStrategy.resetCounters();
    }
}
