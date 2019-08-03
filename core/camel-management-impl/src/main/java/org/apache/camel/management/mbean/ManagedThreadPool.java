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

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedThreadPoolMBean;
import org.apache.camel.spi.ManagementStrategy;

@ManagedResource(description = "Managed ThreadPool")
public class ManagedThreadPool implements ManagedThreadPoolMBean {

    private final CamelContext camelContext;
    private final ThreadPoolExecutor threadPool;
    private final String id;
    private final String sourceId;
    private final String routeId;
    private final String threadPoolProfileId;

    public ManagedThreadPool(CamelContext camelContext, ThreadPoolExecutor threadPool, String id,
                             String sourceId, String routeId, String threadPoolProfileId) {
        this.camelContext = camelContext;
        this.threadPool = threadPool;
        this.sourceId = sourceId;
        this.id = id;
        this.routeId = routeId;
        this.threadPoolProfileId = threadPoolProfileId;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return camelContext;
    }

    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
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
    public String getId() {
        return id;
    }

    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public String getThreadPoolProfileId() {
        return threadPoolProfileId;
    }

    @Override
    public int getCorePoolSize() {
        return threadPool.getCorePoolSize();
    }

    @Override
    public void setCorePoolSize(int corePoolSize) {
        threadPool.setCorePoolSize(corePoolSize);
    }

    @Override
    public int getPoolSize() {
        return threadPool.getPoolSize();
    }

    @Override
    public int getMaximumPoolSize() {
        return threadPool.getMaximumPoolSize();
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
        threadPool.setMaximumPoolSize(maximumPoolSize);
    }

    @Override
    public int getLargestPoolSize() {
        return threadPool.getLargestPoolSize();
    }

    @Override
    public int getActiveCount() {
        return threadPool.getActiveCount();
    }

    @Override
    public long getTaskCount() {
        return threadPool.getTaskCount();
    }

    @Override
    public long getCompletedTaskCount() {
        return threadPool.getCompletedTaskCount();
    }

    @Override
    public long getTaskQueueSize() {
        if (threadPool.getQueue() != null) {
            return threadPool.getQueue().size();
        } else {
            return 0;
        }
    }

    @Override
    public boolean isTaskQueueEmpty() {
        if (threadPool.getQueue() != null) {
            return threadPool.getQueue().isEmpty();
        } else {
            return true;
        }
    }

    @Override
    public long getKeepAliveTime() {
        return threadPool.getKeepAliveTime(TimeUnit.SECONDS);
    }

    @Override
    public void setKeepAliveTime(long keepAliveTimeInSeconds) {
        threadPool.setKeepAliveTime(keepAliveTimeInSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean isAllowCoreThreadTimeout() {
        return threadPool.allowsCoreThreadTimeOut();
    }

    @Override
    public void setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
        threadPool.allowCoreThreadTimeOut(allowCoreThreadTimeout);
    }

    @Override
    public boolean isShutdown() {
        return threadPool.isShutdown();
    }

    @Override
    public void purge() {
        threadPool.purge();
    }

    @Override
    public int getTaskQueueRemainingCapacity() {
        if (threadPool.getQueue() != null) {
            return threadPool.getQueue().remainingCapacity();
        } else {
            // no queue found, so no capacity
            return 0;
        }
    }

}
