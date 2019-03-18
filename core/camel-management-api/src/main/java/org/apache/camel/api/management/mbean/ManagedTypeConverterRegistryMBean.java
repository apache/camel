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

public interface ManagedTypeConverterRegistryMBean extends ManagedServiceMBean {

    @ManagedAttribute(description = "Number of noop attempts (no type conversion was needed)")
    long getNoopCounter();

    @ManagedAttribute(description = "Number of type conversion attempts")
    long getAttemptCounter();

    @ManagedAttribute(description = "Number of type conversion hits (successful conversions)")
    long getHitCounter();

    @ManagedAttribute(description = "Number of type conversion misses (no suitable type converter)")
    long getMissCounter();

    @ManagedAttribute(description = "Number of type conversion failures (failed conversions)")
    long getFailedCounter();

    @ManagedOperation(description = "Resets the type conversion counters")
    void resetTypeConversionCounters();

    @ManagedAttribute(description = "Utilization statistics enabled")
    boolean isStatisticsEnabled();

    @ManagedAttribute(description = "Utilization statistics enabled")
    void setStatisticsEnabled(boolean statisticsEnabled);

    @ManagedAttribute(description = "Number of type converters in the registry")
    int getNumberOfTypeConverters();

    @ManagedAttribute(description = "Logging level to use if attempting to add a duplicate type converter")
    String getTypeConverterExistsLoggingLevel();

    @ManagedAttribute(description = "What to do if attempting to add a duplicate type converter (Override, Ignore or Fail)")
    String getTypeConverterExists();

    @ManagedOperation(description = "Checks whether a type converter exists for converting (from -> to)")
    boolean hasTypeConverter(String fromType, String toType);

    @ManagedOperation(description = "Lists all the type converters in the registry (from -> to)")
    TabularData listTypeConverters();

}
