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

public interface ManagedErrorRegistryMBean extends ManagedServiceMBean {

    @ManagedAttribute(description = "Whether the error registry is enabled")
    boolean isEnabled();

    @ManagedAttribute(description = "Whether the error registry is enabled")
    void setEnabled(boolean enabled);

    @ManagedAttribute(description = "Current number of error entries in the registry")
    int getSize();

    @ManagedAttribute(description = "Maximum number of error entries to keep")
    int getMaximumEntries();

    @ManagedAttribute(description = "Maximum number of error entries to keep")
    void setMaximumEntries(int maximumEntries);

    @ManagedAttribute(description = "Time-to-live in seconds for error entries")
    long getTimeToLiveSeconds();

    @ManagedAttribute(description = "Time-to-live in seconds for error entries")
    void setTimeToLiveSeconds(long seconds);

    @ManagedAttribute(description = "Whether stack trace capture is enabled")
    boolean isStackTraceEnabled();

    @ManagedAttribute(description = "Whether stack trace capture is enabled")
    void setStackTraceEnabled(boolean stackTraceEnabled);

    @ManagedOperation(description = "Browse all error entries")
    TabularData browse();

    @ManagedOperation(description = "Browse error entries with a limit")
    TabularData browse(int limit);

    @ManagedOperation(description = "Browse error entries for a specific route")
    TabularData browse(String routeId, int limit);

    @ManagedOperation(description = "Clear all error entries")
    void clear();
}
