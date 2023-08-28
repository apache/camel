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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedDumpRoutesStrategyMBean;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.spi.ManagementStrategy;

@ManagedResource(description = "Managed DumpRouteStrategyMBean")
public class ManagedDumpRouteStrategy implements ManagedDumpRoutesStrategyMBean {

    private final CamelContext camelContext;
    private final DumpRoutesStrategy dumpRoutesStrategy;

    public ManagedDumpRouteStrategy(CamelContext camelContext, DumpRoutesStrategy dumpRoutesStrategy) {
        this.camelContext = camelContext;
        this.dumpRoutesStrategy = dumpRoutesStrategy;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return camelContext;
    }

    public DumpRoutesStrategy getDumpRoutesStrategy() {
        return dumpRoutesStrategy;
    }

    @Override
    public void dumpRoutes(String format) {
        dumpRoutesStrategy.dumpRoutes(format);
    }

    @Override
    public String getCamelId() {
        return camelContext.getName();
    }

    @Override
    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    @Override
    public String getInclude() {
        return dumpRoutesStrategy.getInclude();
    }

    @Override
    public void setInclude(String include) {
        dumpRoutesStrategy.setInclude(include);
    }

    @Override
    public boolean isLog() {
        return dumpRoutesStrategy.isLog();
    }

    @Override
    public void setLog(boolean log) {
        dumpRoutesStrategy.setLog(log);
    }

    @Override
    public String getOutput() {
        return dumpRoutesStrategy.getOutput();
    }

    @Override
    public void setOutput(String output) {
        dumpRoutesStrategy.setOutput(output);
    }

    @Override
    public boolean isUriAsParameters() {
        return dumpRoutesStrategy.isUriAsParameters();
    }

    @Override
    public void setUriAsParameters(boolean uriAsParameters) {
        dumpRoutesStrategy.setUriAsParameters(uriAsParameters);
    }

    @Override
    public boolean isResolvePlaceholders() {
        return dumpRoutesStrategy.isResolvePlaceholders();
    }

    @Override
    public void setResolvePlaceholders(boolean resolvePlaceholders) {
        dumpRoutesStrategy.setResolvePlaceholders(resolvePlaceholders);
    }
}
