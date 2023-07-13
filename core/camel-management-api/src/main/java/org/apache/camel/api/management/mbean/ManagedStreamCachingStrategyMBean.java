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

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedStreamCachingStrategyMBean extends ManagedServiceMBean {

    /**
     * Used for selecting if the memory limit is <tt>committed</tt> or <tt>maximum</tt> heap memory setting.
     */
    enum SpoolUsedHeapMemoryLimit {
        Committed,
        Max
    }

    @ManagedAttribute(description = "Whether stream caching is enabled")
    boolean isEnabled();

    @ManagedAttribute(description = "To filter stream caching of a given set of allowed/denied classes.")
    String[] getAllowClasses();

    @ManagedAttribute(description = "To filter stream caching of a given set of allowed/denied classes.")
    String[] getDenyClasses();

    @ManagedAttribute(description = "Whether spooling to disk enabled")
    boolean isSpoolEnabled();

    @ManagedAttribute(description = "Directory used when overflow and spooling to disk")
    String getSpoolDirectory();

    @ManagedAttribute(description = "Cipher used if writing with encryption")
    String getSpoolCipher();

    @ManagedAttribute(description = "Threshold in bytes when overflow and spooling to disk instead of keeping in memory")
    void setSpoolThreshold(long threshold);

    @ManagedAttribute(description = "Threshold in bytes when overflow and spooling to disk instead of keeping in memory")
    long getSpoolThreshold();

    @ManagedAttribute(description = "Percentage (1-99) of used heap memory threshold to activate spooling to disk")
    void setSpoolUsedHeapMemoryThreshold(int percentage);

    @ManagedAttribute(description = "Percentage (1-99) of used heap memory threshold to activate spooling to disk")
    int getSpoolUsedHeapMemoryThreshold();

    @ManagedAttribute(description = "Whether used heap memory limit is committed or maximum")
    void setSpoolUsedHeapMemoryLimit(SpoolUsedHeapMemoryLimit limit);

    @ManagedAttribute(description = "Whether used heap memory limit is committed or maximum")
    SpoolUsedHeapMemoryLimit getSpoolUsedHeapMemoryLimit();

    @ManagedAttribute(description = "Buffer size in bytes to use when coping between buffers")
    void setBufferSize(int bufferSize);

    @ManagedAttribute(description = "Buffer size in bytes to use when coping between buffers")
    int getBufferSize();

    @ManagedAttribute(description = "Whether to remove spool directory when stopping")
    void setRemoveSpoolDirectoryWhenStopping(boolean remove);

    @ManagedAttribute(description = "Whether to remove spool directory when stopping")
    boolean isRemoveSpoolDirectoryWhenStopping();

    @ManagedAttribute(description = "Whether any or all spool rules determines whether to spool")
    void setAnySpoolRules(boolean any);

    @ManagedAttribute(description = "Whether any or all spool rules determines whether to spool")
    boolean isAnySpoolRules();

    @ManagedAttribute(description = "Number of in-memory StreamCache created")
    long getCacheMemoryCounter();

    @ManagedAttribute(description = "Total accumulated number of bytes which has been stream cached for in-memory StreamCache")
    long getCacheMemorySize();

    @ManagedAttribute(description = "Average number of bytes per cached stream for in-memory stream caches.")
    long getCacheMemoryAverageSize();

    @ManagedAttribute(description = "Number of spooled (not in-memory) StreamCache created")
    long getCacheSpoolCounter();

    @ManagedAttribute(description = "Total accumulated number of bytes which has been stream cached for spooled StreamCache")
    long getCacheSpoolSize();

    @ManagedAttribute(description = "Average number of bytes per cached stream for spooled (not in-memory) stream caches.")
    long getCacheSpoolAverageSize();

    @ManagedAttribute(description = "Whether utilization statistics is enabled")
    boolean isStatisticsEnabled();

    @ManagedAttribute(description = "Whether utilization statistics is enabled")
    void setStatisticsEnabled(boolean enabled);

    @ManagedOperation(description = "Reset the utilization statistics")
    void resetStatistics();

}
