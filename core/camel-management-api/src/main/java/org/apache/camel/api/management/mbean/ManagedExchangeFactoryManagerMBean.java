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

import javax.management.openmbean.TabularData;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedExchangeFactoryManagerMBean extends ManagedServiceMBean {

    @ManagedAttribute(description = "Number of consumers managed")
    Integer getConsumerCounter();

    @ManagedAttribute(description = "Max capacity per consumer for exchange pooling")
    Integer getCapacity();

    @ManagedAttribute(description = "Whether statistics is enabled")
    Boolean getStatisticsEnabled();

    @ManagedAttribute(description = "Whether statistics is enabled")
    void setStatisticsEnabled(Boolean statisticsEnabled);

    @ManagedOperation(description = "Reset statistics")
    void resetStatistics();

    @ManagedOperation(description = "Purges the pool")
    void purge();

    @ManagedAttribute(description = "Total number of currently pooled exchanges (if pooling is in use)")
    Integer getTotalPooled();

    @ManagedAttribute(description = "Total number of new exchanges created")
    Long getTotalCreated();

    @ManagedAttribute(description = "Total number of exchanges reused (if pooling is in use)")
    Long getTotalAcquired();

    @ManagedAttribute(description = "Total number of exchanges released back to the pool")
    Long getTotalReleased();

    @ManagedAttribute(description = "Total number of exchanges discarded (such as when capacity is full)")
    Long getTotalDiscarded();

    @ManagedOperation(description = "Lists all the statistics in tabular form")
    TabularData listStatistics();

}
