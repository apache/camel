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

import org.apache.camel.spi.ManagementStrategy;
import org.fusesource.commons.management.Statistic;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "Managed Counter")
public abstract class ManagedCounter {
    protected Statistic exchangesTotal;

    public void init(ManagementStrategy strategy) {
        this.exchangesTotal = strategy.createStatistic("org.apache.camel.exchangesTotal", this, Statistic.UpdateMode.COUNTER);
    }

    @ManagedOperation(description = "Reset counters")
    public synchronized void reset() {
        exchangesTotal.reset();
    }

    @ManagedAttribute(description = "Total number of exchanges")
    public long getExchangesTotal() throws Exception {
        return exchangesTotal.getValue();
    }

    public synchronized void increment() {
        exchangesTotal.increment();
    }
}
