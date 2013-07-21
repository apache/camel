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
import org.apache.camel.api.management.mbean.ManagedStreamCachingStrategyMBean;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.StreamCachingStrategy;

@ManagedResource(description = "Managed StreamCachingStrategy")
public class ManagedStreamCachingStrategy extends ManagedService implements ManagedStreamCachingStrategyMBean {

    private final CamelContext camelContext;
    private final StreamCachingStrategy streamCachingStrategy;

    public ManagedStreamCachingStrategy(CamelContext camelContext, StreamCachingStrategy streamCachingStrategy) {
        super(camelContext, streamCachingStrategy);
        this.camelContext = camelContext;
        this.streamCachingStrategy = streamCachingStrategy;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public StreamCachingStrategy getStreamCachingStrategy() {
        return streamCachingStrategy;
    }

    public boolean isEnabled() {
        return streamCachingStrategy.isEnabled();
    }

    public String getSpoolDirectory() {
        return streamCachingStrategy.getSpoolDirectory().getPath();
    }

    public String getSpoolChiper() {
        return streamCachingStrategy.getSpoolChiper();
    }

    public void setSpoolThreshold(long threshold) {
        streamCachingStrategy.setSpoolThreshold(threshold);
    }

    public long getSpoolThreshold() {
        return streamCachingStrategy.getSpoolThreshold();
    }

    public void setBufferSize(int bufferSize) {
        streamCachingStrategy.setBufferSize(bufferSize);
    }

    public int getBufferSize() {
        return streamCachingStrategy.getBufferSize();
    }

    public void setRemoveSpoolDirectoryWhenStopping(boolean remove) {
        streamCachingStrategy.setRemoveSpoolDirectoryWhenStopping(remove);
    }

    public boolean isRemoveSpoolDirectoryWhenStopping() {
        return streamCachingStrategy.isRemoveSpoolDirectoryWhenStopping();
    }

    public long getCacheMemoryCounter() {
        return streamCachingStrategy.getStatistics().getCacheMemoryCounter();
    }

    public long getCacheSpoolCounter() {
        return streamCachingStrategy.getStatistics().getCacheSpoolCounter();
    }

    public long getCacheMemorySize() {
        return streamCachingStrategy.getStatistics().getCacheMemorySize();
    }

    public long getCacheSpoolSize() {
        return streamCachingStrategy.getStatistics().getCacheSpoolSize();
    }

    public boolean isStatisticsEnabled() {
        return streamCachingStrategy.getStatistics().isStatisticsEnabled();
    }

    public void setStatisticsEnabled(boolean enabled) {
        streamCachingStrategy.getStatistics().setStatisticsEnabled(enabled);
    }

    public void resetStatistics() {
        streamCachingStrategy.getStatistics().reset();
    }

}
