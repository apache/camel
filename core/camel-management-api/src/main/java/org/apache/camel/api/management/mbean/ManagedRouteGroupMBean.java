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
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedRouteGroupMBean extends ManagedPerformanceCounterMBean {

    @ManagedAttribute(description = "Route ID")
    String getRouteGroup();

    @ManagedAttribute(description = "Number of routes in this group")
    int getGroupSize();

    @ManagedAttribute(description = "The route IDs within this group")
    String[] getGroupIds();

    @ManagedAttribute(description = "Route State")
    String getState();

    @ManagedAttribute(description = "Route Uptime [human readable text]")
    String getUptime();

    @ManagedAttribute(description = "Route Uptime [milliseconds]")
    long getUptimeMillis();

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Number of completed exchanges")
    long getExchangesCompleted();

    @ManagedAttribute(description = "Number of failed exchanges")
    long getExchangesFailed();

    @ManagedAttribute(description = "Number of inflight exchanges")
    long getExchangesInflight();

    @ManagedAttribute(description = "Number of failures handled")
    long getFailuresHandled();

    @ManagedAttribute(description = "Number of redeliveries (internal only)")
    long getRedeliveries();

    @ManagedAttribute(description = "Number of external initiated redeliveries (such as from JMS broker)")
    long getExternalRedeliveries();

    @ManagedAttribute(description = "Average load (inflight messages, not cpu) over the last minute")
    String getLoad01();

    @ManagedAttribute(description = "Average load (inflight messages, not cpu) over the last five minutes")
    String getLoad05();

    @ManagedAttribute(description = "Average load (inflight messages, not cpu) over the last fifteen minutes")
    String getLoad15();

    @ManagedAttribute(description = "Throughput message/second")
    String getThroughput();

    @ManagedOperation(description = "Start all routes")
    void start() throws Exception;

    @ManagedOperation(description = "Stop all routes")
    void stop() throws Exception;
}
