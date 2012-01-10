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
package org.apache.camel.management;

/**
 * Delegates to another {@link org.apache.camel.management.PerformanceCounter}.
 * <p/>
 * This is used to allow Camel to pre initialize these delegate performance counters
 * when Camel creates the actual route from the model. Then later as the various
 * processors, routes etc. is created and registered in the {@link org.apache.camel.spi.LifecycleStrategy}
 * then we link this to the real {@link org.apache.camel.management.mbean.ManagedPerformanceCounter} mbean
 * so the mbean can gather statistics.
 * <p/>
 * This delegation is needed as how Camel is designed to register services in the
 * {@link org.apache.camel.spi.LifecycleStrategy} in various stages.
 *
 * @version 
 */
public class DelegatePerformanceCounter implements PerformanceCounter {

    private PerformanceCounter counter;
    private boolean statisticsEnabled;

    public DelegatePerformanceCounter() {
    }

    public void setCounter(PerformanceCounter counter) {
        this.counter = counter;
        // init statistics based on the real counter based on how we got initialized
        this.counter.setStatisticsEnabled(statisticsEnabled);
    }

    public void completedExchange(long time) {
        counter.completedExchange(time);
    }

    public void failedExchange() {
        counter.failedExchange();
    }

    public boolean isStatisticsEnabled() {
        // statistics is only considered enabled if we have a counter to delegate to
        // otherwise we do not want to gather statistics (we are just a delegate with noone to delegate to)
        return counter != null && counter.isStatisticsEnabled();
    }

    public void setStatisticsEnabled(boolean statisticsEnabled) {
        if (counter != null) {
            counter.setStatisticsEnabled(statisticsEnabled);
        } else {
            this.statisticsEnabled = statisticsEnabled;
        }
    }
}
