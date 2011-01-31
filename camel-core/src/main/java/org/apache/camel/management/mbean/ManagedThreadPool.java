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
import org.apache.camel.spi.ManagementStrategy;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version $Revision$
 */
@ManagedResource(description = "Managed ThreadPool")
public class ManagedThreadPool {

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

    @ManagedAttribute(description = "Thread Pool id")
    public String getId() {
        return id;
    }

    @ManagedAttribute(description = "Id of source for creating Thread Pool")
    public String getSourceId() {
        return sourceId;
    }

    @ManagedAttribute(description = "Route id for the source, which created the Thread Pool")
    public String getRouteId() {
        return routeId;
    }

    @ManagedAttribute(description = "Id of the thread pool profile which this pool is based upon")
    public String getThreadPoolProfileId() {
        return threadPoolProfileId;
    }

    @ManagedAttribute(description = "Core pool size")
    public int getCorePoolSize() {
        return threadPool.getCorePoolSize();
    }

    @ManagedAttribute(description = "Core pool size")
    public void setCorePoolSize(int corePoolSize) {
        threadPool.setCorePoolSize(corePoolSize);
    }

    @ManagedAttribute(description = "Pool size")
    public int getPoolSize() {
        return threadPool.getPoolSize();
    }

    @ManagedAttribute(description = "Maximum pool size")
    public int getMaximumPoolSize() {
        return threadPool.getMaximumPoolSize();
    }

    @ManagedAttribute(description = "Maximum pool size")
    public void setMaximumPoolSize(int maximumPoolSize) {
        threadPool.setMaximumPoolSize(maximumPoolSize);
    }

    @ManagedAttribute(description = "Largest pool size")
    public int getLargestPoolSize() {
        return threadPool.getLargestPoolSize();
    }

    @ManagedAttribute(description = "Active count")
    public int getActiveCount() {
        return threadPool.getActiveCount();
    }

    @ManagedAttribute(description = "Task count")
    public long getTaskCount() {
        return threadPool.getTaskCount();
    }

    @ManagedAttribute(description = "Completed task count")
    public long getCompletedTaskCount() {
        return threadPool.getCompletedTaskCount();
    }

    @ManagedAttribute(description = "Task queue size")
    public long getTaskQueueSize() {
        if (threadPool.getQueue() != null) {
            return threadPool.getQueue().size();
        } else {
            return 0;
        }
    }

    @ManagedAttribute(description = "Is task queue empty")
    public boolean isTaskQueueEmpty() {
        if (threadPool.getQueue() != null) {
            return threadPool.getQueue().isEmpty();
        } else {
            return true;
        }
    }

    @ManagedAttribute(description = "Keep alive time in seconds")
    public long getKeepAliveTime() {
        return threadPool.getKeepAliveTime(TimeUnit.SECONDS);
    }

    @ManagedAttribute(description = "Keep alive time in seconds")
    public void setKeepAliveTime(int keepAliveTimeInSeconds) {
        threadPool.setKeepAliveTime(keepAliveTimeInSeconds, TimeUnit.SECONDS);
    }

    @ManagedAttribute(description = "Is shutdown")
    public boolean isShutdown() {
        return threadPool.isShutdown();
    }

}
