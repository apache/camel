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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;

public interface ManagedThrottlerMBean extends ManagedProcessorMBean {

    @ManagedAttribute(description = "Maximum requests per period")
    long getMaximumRequestsPerPeriod();

    @ManagedAttribute(description = "Maximum requests per period")
    void setMaximumRequestsPerPeriod(long maximumRequestsPerPeriod);

    @ManagedAttribute(description = "Time period in millis")
    long getTimePeriodMillis();

    @ManagedAttribute(description = "Time period in millis")
    void setTimePeriodMillis(long timePeriodMillis);

    @ManagedAttribute(description = "Enables asynchronous delay which means the thread will not block while delaying")
    Boolean isAsyncDelayed();

    @ManagedAttribute(description = "Whether or not the caller should run the task when it was rejected by the thread pool")
    Boolean isCallerRunsWhenRejected();

    @ManagedAttribute(description = "Whether or not throttler throws the ThrottlerRejectedExecutionException when the exchange exceeds the request limit")
    Boolean isRejectExecution();

}