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
package org.apache.camel.processor.aggregate;

/**
 * Various statistics of the aggregator
 */
public interface AggregateProcessorStatistics {

    /**
     * Total number of exchanges arrived into the aggregator
     */
    long getTotalIn();

    /**
     * Total number of exchanges completed and outgoing from the aggregator
     */
    long getTotalCompleted();

    /**
     * Total number of exchanged completed by completion size trigger
     */
    long getCompletedBySize();

    /**
     * Total number of exchanged completed by completion strategy trigger
     */
    long getCompletedByStrategy();

    /**
     * Total number of exchanged completed by completion interval trigger
     */
    long getCompletedByInterval();

    /**
     * Total number of exchanged completed by completion timeout trigger
     */
    long getCompletedByTimeout();

    /**
     * Total number of exchanged completed by completion predicate trigger
     */
    long getCompletedByPredicate();

    /**
     * Total number of exchanged completed by completion batch consumer trigger
     */
    long getCompletedByBatchConsumer();

    /**
     * Total number of exchanged completed by completion force trigger
     */
    long getCompletedByForce();

    /**
     * Total number of exchanged discarded
     */
    long getDiscarded();

    /**
     * Reset the counters
     */
    void reset();

    /**
     * Whether statistics is enabled.
     */
    boolean isStatisticsEnabled();

    /**
     * Sets whether statistics is enabled.
     *
     * @param statisticsEnabled <tt>true</tt> to enable
     */
    void setStatisticsEnabled(boolean statisticsEnabled);

}
