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

import java.util.List;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedTypeConverterRegistryMBean;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.ObjectHelper;

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

    public long getNoopCounter() {
        return registry.getStatistics().getNoopCounter();
    }

    public long getAttemptCounter() {
        return registry.getStatistics().getAttemptCounter();
    }

    public long getHitCounter() {
        return registry.getStatistics().getHitCounter();
    }

    public long getBaseHitCounter() {
        return registry.getStatistics().getBaseHitCounter();
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

    public int getNumberOfTypeConverters() {
        return registry.size();
    }

    public String getTypeConverterExistsLoggingLevel() {
        return registry.getTypeConverterExistsLoggingLevel().name();
    }

    public String getTypeConverterExists() {
        return registry.getTypeConverterExists().name();
    }

    public boolean hasTypeConverter(String fromType, String toType) {
        try {
            Class<?> from = getContext().getClassResolver().resolveMandatoryClass(fromType);
            Class<?> to = getContext().getClassResolver().resolveMandatoryClass(toType);
            return registry.lookup(to, from) != null;
        } catch (ClassNotFoundException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public TabularData listTypeConverters() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listTypeConvertersTabularType());
            List<Class<?>[]> converters = registry.listAllTypeConvertersFromTo();
            for (Class<?>[] entry : converters) {
                CompositeType ct = CamelOpenMBeanTypes.listTypeConvertersCompositeType();
                String from = entry[0].getCanonicalName();
                String to = entry[1].getCanonicalName();
                CompositeData data = new CompositeDataSupport(ct, new String[]{"from", "to"}, new Object[]{from, to});
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }
}
