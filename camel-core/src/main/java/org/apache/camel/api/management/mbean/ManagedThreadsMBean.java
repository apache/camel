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

public interface ManagedThreadsMBean extends ManagedProcessorMBean {

    @ManagedAttribute(description = "Whether or not the caller should run the task when it was rejected by the thread pool")
    Boolean isCallerRunsWhenRejected();

    @ManagedAttribute(description = "How to handle tasks which cannot be accepted by the thread pool")
    String getRejectedPolicy();

    @ManagedAttribute(description = "Core pool size")
    int getCorePoolSize();

    @ManagedAttribute(description = "Pool size")
    int getPoolSize();

    @ManagedAttribute(description = "Maximum pool size")
    int getMaximumPoolSize();

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

    @ManagedAttribute(description = "Keep alive time in seconds")
    long getKeepAliveTime();

    @ManagedAttribute(description = "Whether core threads is allowed to timeout if no tasks in queue to process")
    boolean isAllowCoreThreadTimeout();

}