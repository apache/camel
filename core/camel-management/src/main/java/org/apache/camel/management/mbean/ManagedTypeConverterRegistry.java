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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
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

    @Override
    public long getNoopCounter() {
        return registry.getStatistics().getNoopCounter();
    }

    @Override
    public long getAttemptCounter() {
        return registry.getStatistics().getAttemptCounter();
    }

    @Override
    public long getHitCounter() {
        return registry.getStatistics().getHitCounter();
    }

    @Override
    public long getMissCounter() {
        return registry.getStatistics().getMissCounter();
    }

    @Override
    public long getFailedCounter() {
        return registry.getStatistics().getFailedCounter();
    }

    @Override
    public void resetTypeConversionCounters() {
        registry.getStatistics().reset();
    }

    @Override
    public boolean isStatisticsEnabled() {
        return registry.getStatistics().isStatisticsEnabled();
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        registry.getStatistics().setStatisticsEnabled(statisticsEnabled);
    }

    @Override
    public int getNumberOfTypeConverters() {
        return registry.size();
    }

    @Override
    public String getTypeConverterExistsLoggingLevel() {
        return registry.getTypeConverterExistsLoggingLevel().name();
    }

    @Override
    public String getTypeConverterExists() {
        return registry.getTypeConverterExists().name();
    }

    @Override
    public boolean hasTypeConverter(String fromType, String toType) {
        try {
            Class<?> from = getContext().getClassResolver().resolveMandatoryClass(fromType);
            Class<?> to = getContext().getClassResolver().resolveMandatoryClass(toType);
            return registry.lookup(to, from) != null;
        } catch (ClassNotFoundException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
