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

import java.util.Collection;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedAsyncProcessorAwaitManagerMBean;
import org.apache.camel.spi.AsyncProcessorAwaitManager;

/**
 *
 */
@ManagedResource(description = "Managed AsyncProcessorAwaitManager")
public class ManagedAsyncProcessorAwaitManager extends ManagedService implements ManagedAsyncProcessorAwaitManagerMBean {

    private final AsyncProcessorAwaitManager manager;

    public ManagedAsyncProcessorAwaitManager(CamelContext context, AsyncProcessorAwaitManager manager) {
        super(context, manager);
        this.manager = manager;
    }

    public AsyncProcessorAwaitManager getAsyncProcessorAwaitManager() {
        return manager;
    }

    @Override
    public boolean isInterruptThreadsWhileStopping() {
        return manager.isInterruptThreadsWhileStopping();
    }

    @Override
    public void setInterruptThreadsWhileStopping(boolean interruptThreadsWhileStopping) {
        manager.setInterruptThreadsWhileStopping(interruptThreadsWhileStopping);
    }

    @Override
    public int getSize() {
        return manager.size();
    }

    @Override
    public TabularData browse() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listAwaitThreadsTabularType());
            Collection<AsyncProcessorAwaitManager.AwaitThread> threads = manager.browse();
            for (AsyncProcessorAwaitManager.AwaitThread entry : threads) {
                CompositeType ct = CamelOpenMBeanTypes.listAwaitThreadsCompositeType();
                String id = Long.toString(entry.getBlockedThread().getId());
                String name = entry.getBlockedThread().getName();
                String exchangeId = entry.getExchange().getExchangeId();
                String routeId = entry.getRouteId();
                String nodeId = entry.getNodeId();
                String duration = Long.toString(entry.getWaitDuration());

                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[] { "id", "name", "exchangeId", "routeId", "nodeId", "duration" },
                        new Object[] { id, name, exchangeId, routeId, nodeId, duration });
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void interrupt(String exchangeId) {
        manager.interrupt(exchangeId);
    }

    @Override
    public long getThreadsBlocked() {
        return manager.getStatistics().getThreadsBlocked();
    }

    @Override
    public long getThreadsInterrupted() {
        return manager.getStatistics().getThreadsInterrupted();
    }

    @Override
    public long getTotalDuration() {
        return manager.getStatistics().getTotalDuration();
    }

    @Override
    public long getMinDuration() {
        return manager.getStatistics().getMinDuration();
    }

    @Override
    public long getMaxDuration() {
        return manager.getStatistics().getMaxDuration();
    }

    @Override
    public long getMeanDuration() {
        return manager.getStatistics().getMeanDuration();
    }

    @Override
    public void resetStatistics() {
        manager.getStatistics().reset();
    }

    @Override
    public boolean isStatisticsEnabled() {
        return manager.getStatistics().isStatisticsEnabled();
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        manager.getStatistics().setStatisticsEnabled(statisticsEnabled);
    }

}
