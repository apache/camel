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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedThreadPoolMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Thread Pool ID")
    String getId();

    @ManagedAttribute(description = "ID of source for creating Thread Pool")
    String getSourceId();

    @ManagedAttribute(description = "Route ID for the source, which created the Thread Pool")
    String getRouteId();

    @ManagedAttribute(description = "ID of the thread pool profile which this pool is based upon")
    String getThreadPoolProfileId();

    @ManagedAttribute(description = "Core pool size")
    int getCorePoolSize();

    @ManagedAttribute(description = "Core pool size")
    void setCorePoolSize(int corePoolSize);

    @ManagedAttribute(description = "Pool size")
    int getPoolSize();

    @ManagedAttribute(description = "Maximum pool size")
    int getMaximumPoolSize();

    @ManagedAttribute(description = "Maximum pool size")
    void setMaximumPoolSize(int maximumPoolSize);

    @ManagedAttribute(description = "Largest pool size")
    int getLargestPoolSize();

    @ManagedAttribute(description = "Active count")
    int getActiveCount();

    @ManagedAttribute(description = "Task count")
    long getTaskCount();

    @ManagedAttribute(description = "Completed task count")
    long getCompletedTaskCount();

    @ManagedAttribute(description = "Task queue size")
    long getTaskQueueSize();

    @ManagedAttribute(description = "Is task queue empty")
    boolean isTaskQueueEmpty();

    @ManagedAttribute(description = "Keep alive time in seconds")
    long getKeepAliveTime();

    @ManagedAttribute(description = "Keep alive time in seconds")
    void setKeepAliveTime(long keepAliveTimeInSeconds);

    @ManagedAttribute(description = "Whether core threads is allowed to timeout if no tasks in queue to process")
    boolean isAllowCoreThreadTimeout();

    @ManagedAttribute(description = "Whether core threads is allowed to timeout if no tasks in queue to process")
    void setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout);

    @ManagedAttribute(description = "Is shutdown")
    boolean isShutdown();

    @ManagedOperation(description = "Purges the pool")
    void purge();

    @ManagedOperation(description = "Returns the number of additional elements that the Task queue can"
            + " ideally (in the absence of memory or resource constraints) accept")
    int getTaskQueueRemainingCapacity();
}