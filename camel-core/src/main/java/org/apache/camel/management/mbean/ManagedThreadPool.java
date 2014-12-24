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

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedThreadPoolMBean;
import org.apache.camel.spi.ManagementStrategy;

/**
 * @version 
 */
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

    public String getCamelId() {
        return camelContext.getName();
    }

    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    public String getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getThreadPoolProfileId() {
        return threadPoolProfileId;
    }

    public int getCorePoolSize() {
        return threadPool.getCorePoolSize();
    }

    public void setCorePoolSize(int corePoolSize) {
        threadPool.setCorePoolSize(corePoolSize);
    }

    public int getPoolSize() {
        return threadPool.getPoolSize();
    }

    public int getMaximumPoolSize() {
        return threadPool.getMaximumPoolSize();
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        threadPool.setMaximumPoolSize(maximumPoolSize);
    }

    public int getLargestPoolSize() {
        return threadPool.getLargestPoolSize();
    }

    public int getActiveCount() {
        return threadPool.getActiveCount();
    }

    public long getTaskCount() {
        return threadPool.getTaskCount();
    }

    public long getCompletedTaskCount() {
        return threadPool.getCompletedTaskCount();
    }

    public long getTaskQueueSize() {
        if (threadPool.getQueue() != null) {
            return threadPool.getQueue().size();
        } else {
            return 0;
        }
    }

    public boolean isTaskQueueEmpty() {
        if (threadPool.getQueue() != null) {
            return threadPool.getQueue().isEmpty();
        } else {
            return true;
        }
    }

    public long getKeepAliveTime() {
        return threadPool.getKeepAliveTime(TimeUnit.SECONDS);
    }

    public void setKeepAliveTime(long keepAliveTimeInSeconds) {
        threadPool.setKeepAliveTime(keepAliveTimeInSeconds, TimeUnit.SECONDS);
    }

    public boolean isAllowCoreThreadTimeout() {
        return threadPool.allowsCoreThreadTimeOut();
    }

    public void setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
        threadPool.allowCoreThreadTimeOut(allowCoreThreadTimeout);
    }

    public boolean isShutdown() {
        return threadPool.isShutdown();
    }

    public void purge() {
        threadPool.purge();
    }

    public int getTaskQueueRemainingCapacity() {
        if (threadPool.getQueue() != null) {
            return threadPool.getQueue().remainingCapacity();
        } else {
            // no queue found, so no capacity
            return 0;
        }
    }

}
