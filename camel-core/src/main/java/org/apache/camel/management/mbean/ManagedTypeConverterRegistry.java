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

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedTypeConverterRegistryMBean;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 *
 */
@ManagedResource(description = "Managed TypeConverterRegistry")
public class ManagedTypeConverterRegistry extends ManagedService implements ManagedTypeConverterRegistryMBean {

    private final TypeConverterRegistry registry;

    public ManagedTypeConverterRegistry(CamelContext context, TypeConverterRegistry registry) {
        super(context, registry);
        this.registry = registry;
    }

    public TypeConverterRegistry getRegistry() {
        return registry;
    }

    public long getAttemptCounter() {
        return registry.getStatistics().getAttemptCounter();
    }

    public long getHitCounter() {
        return registry.getStatistics().getHitCounter();
    }

    public long getMissCounter() {
        return registry.getStatistics().getMissCounter();
    }

    public long getFailedCounter() {
        return registry.getStatistics().getFailedCounter();
    }

    public void resetTypeConversionCounters() {
        registry.getStatistics().reset();
    }

    public boolean isStatisticsEnabled() {
        return registry.getStatistics().isStatisticsEnabled();
    }

    public void setStatisticsEnabled(boolean statisticsEnabled) {
        registry.getStatistics().setStatisticsEnabled(statisticsEnabled);
    }
}
