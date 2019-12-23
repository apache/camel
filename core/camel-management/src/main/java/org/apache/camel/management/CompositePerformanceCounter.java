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
package org.apache.camel.management;

import org.apache.camel.Exchange;

/**
 * A composite {@link PerformanceCounter} is used for tracking performance statistics on both a per
 * context and route level, by issuing callbacks on both when an event happens.
 * <p/>
 * This implementation is used so the {@link org.apache.camel.management.mbean.ManagedCamelContext}
 * can aggregate all stats from the routes.
 */
public class CompositePerformanceCounter implements PerformanceCounter {

    private final PerformanceCounter counter1;
    private final PerformanceCounter counter2;

    public CompositePerformanceCounter(PerformanceCounter counter1, PerformanceCounter counter2) {
        this.counter1 = counter1;
        this.counter2 = counter2;
    }

    @Override
    public void processExchange(Exchange exchange) {
        if (counter1.isStatisticsEnabled()) {
            counter1.processExchange(exchange);
        }
        if (counter2.isStatisticsEnabled()) {
            counter2.processExchange(exchange);
        }
    }

    @Override
    public void completedExchange(Exchange exchange, long time) {
        if (counter1.isStatisticsEnabled()) {
            counter1.completedExchange(exchange, time);
        }
        if (counter2.isStatisticsEnabled()) {
            counter2.completedExchange(exchange, time);
        }
    }

    @Override
    public void failedExchange(Exchange exchange) {
        if (counter1.isStatisticsEnabled()) {
            counter1.failedExchange(exchange);
        }
        if (counter2.isStatisticsEnabled()) {
            counter2.failedExchange(exchange);
        }
    }

    @Override
    public boolean isStatisticsEnabled() {
        // this method is not used
        return true;
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        // this method is not used
    }
}
