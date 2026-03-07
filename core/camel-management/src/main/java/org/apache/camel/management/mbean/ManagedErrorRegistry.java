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

import java.time.Duration;
import java.util.Collection;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedErrorRegistryMBean;
import org.apache.camel.spi.ErrorRegistry;
import org.apache.camel.spi.ErrorRegistryEntry;

@ManagedResource(description = "Managed ErrorRegistry")
public class ManagedErrorRegistry extends ManagedService implements ManagedErrorRegistryMBean {

    private final ErrorRegistry errorRegistry;

    public ManagedErrorRegistry(CamelContext context, ErrorRegistry errorRegistry) {
        super(context, errorRegistry);
        this.errorRegistry = errorRegistry;
    }

    public ErrorRegistry getErrorRegistry() {
        return errorRegistry;
    }

    @Override
    public boolean isEnabled() {
        return errorRegistry.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        errorRegistry.setEnabled(enabled);
    }

    @Override
    public int getSize() {
        return errorRegistry.size();
    }

    @Override
    public int getMaximumEntries() {
        return errorRegistry.getMaximumEntries();
    }

    @Override
    public void setMaximumEntries(int maximumEntries) {
        errorRegistry.setMaximumEntries(maximumEntries);
    }

    @Override
    public long getTimeToLiveSeconds() {
        return errorRegistry.getTimeToLive().toSeconds();
    }

    @Override
    public void setTimeToLiveSeconds(long seconds) {
        errorRegistry.setTimeToLive(Duration.ofSeconds(seconds));
    }

    @Override
    public boolean isStackTraceEnabled() {
        return errorRegistry.isStackTraceEnabled();
    }

    @Override
    public void setStackTraceEnabled(boolean stackTraceEnabled) {
        errorRegistry.setStackTraceEnabled(stackTraceEnabled);
    }

    @Override
    public TabularData browse() {
        return browseEntries(errorRegistry.browse());
    }

    @Override
    public TabularData browse(int limit) {
        return browseEntries(errorRegistry.browse(limit));
    }

    @Override
    public TabularData browse(String routeId, int limit) {
        return browseEntries(errorRegistry.forRoute(routeId).browse(limit));
    }

    @Override
    public void clear() {
        errorRegistry.clear();
    }

    private static TabularData browseEntries(Collection<ErrorRegistryEntry> entries) {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listErrorRegistryTabularType());
            for (ErrorRegistryEntry entry : entries) {
                CompositeType ct = CamelOpenMBeanTypes.listErrorRegistryCompositeType();
                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[] {
                                "exchangeId", "routeId", "endpointUri", "timestamp",
                                "handled", "exceptionType", "exceptionMessage" },
                        new Object[] {
                                entry.exchangeId(),
                                entry.routeId(),
                                entry.endpointUri(),
                                entry.timestamp().toString(),
                                entry.handled(),
                                entry.exceptionType(),
                                entry.exceptionMessage() });
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
