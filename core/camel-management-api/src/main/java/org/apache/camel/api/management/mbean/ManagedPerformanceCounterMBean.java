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

import java.util.Date;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedPerformanceCounterMBean extends ManagedCounterMBean {

    @ManagedAttribute(description = "Number of completed exchanges")
    long getExchangesCompleted() throws Exception;

    @ManagedAttribute(description = "Number of failed exchanges")
    long getExchangesFailed() throws Exception;

    @ManagedAttribute(description = "Number of inflight exchanges")
    long getExchangesInflight() throws Exception;

    @ManagedAttribute(description = "Number of failures handled")
    long getFailuresHandled() throws Exception;

    @ManagedAttribute(description = "Number of redeliveries (internal only)")
    long getRedeliveries() throws Exception;

    @ManagedAttribute(description = "Number of external initiated redeliveries (such as from JMS broker)")
    long getExternalRedeliveries() throws Exception;

    @ManagedAttribute(description = "Min Processing Time [milliseconds]")
    long getMinProcessingTime() throws Exception;

    @ManagedAttribute(description = "Mean Processing Time [milliseconds]")
    long getMeanProcessingTime() throws Exception;

    @ManagedAttribute(description = "Max Processing Time [milliseconds]")
    long getMaxProcessingTime() throws Exception;

    @ManagedAttribute(description = "Total Processing Time [milliseconds]")
    long getTotalProcessingTime() throws Exception;

    @ManagedAttribute(description = "Last Processing Time [milliseconds]")
    long getLastProcessingTime() throws Exception;

    @ManagedAttribute(description = "Delta Processing Time [milliseconds]")
    long getDeltaProcessingTime() throws Exception;

    @ManagedAttribute(description = "Last Exchange Completed Timestamp")
    Date getLastExchangeCompletedTimestamp();

    @ManagedAttribute(description = "Last Exchange Completed ExchangeId")
    String getLastExchangeCompletedExchangeId();

    @ManagedAttribute(description = "First Exchange Completed Timestamp")
    Date getFirstExchangeCompletedTimestamp();

    @ManagedAttribute(description = "First Exchange Completed ExchangeId")
    String getFirstExchangeCompletedExchangeId();

    @ManagedAttribute(description = "Last Exchange Failed Timestamp")
    Date getLastExchangeFailureTimestamp();

    @ManagedAttribute(description = "Last Exchange Failed ExchangeId")
    String getLastExchangeFailureExchangeId();

    @ManagedAttribute(description = "First Exchange Failed Timestamp")
    Date getFirstExchangeFailureTimestamp();

    @ManagedAttribute(description = "First Exchange Failed ExchangeId")
    String getFirstExchangeFailureExchangeId();

    @ManagedAttribute(description = "Statistics enabled")
    boolean isStatisticsEnabled();

    @ManagedAttribute(description = "Statistics enabled")
    void setStatisticsEnabled(boolean statisticsEnabled);

    @ManagedOperation(description = "Dumps the statistics as XML")
    String dumpStatsAsXml(boolean fullStats);

}
