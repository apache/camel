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
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedStreamCachingStrategyMBean;
import org.apache.camel.spi.StreamCachingStrategy;

@ManagedResource(description = "Managed StreamCachingStrategy")
public class ManagedStreamCachingStrategy extends ManagedService implements ManagedStreamCachingStrategyMBean {

    private final CamelContext camelContext;
    private final StreamCachingStrategy streamCachingStrategy;
    private final String[] allowClasses;
    private final String[] denyClasses;

    public ManagedStreamCachingStrategy(CamelContext camelContext, StreamCachingStrategy streamCachingStrategy) {
        super(camelContext, streamCachingStrategy);
        this.camelContext = camelContext;
        this.streamCachingStrategy = streamCachingStrategy;
        if (streamCachingStrategy.getAllowClasses() != null) {
            this.allowClasses = streamCachingStrategy.getAllowClasses().toArray(new String[0]);
        } else {
            this.allowClasses = null;
        }
        if (streamCachingStrategy.getDenyClasses() != null) {
            this.denyClasses = streamCachingStrategy.getDenyClasses().toArray(new String[0]);
        } else {
            this.denyClasses = null;
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public StreamCachingStrategy getStreamCachingStrategy() {
        return streamCachingStrategy;
    }

    @Override
    public boolean isEnabled() {
        return streamCachingStrategy.isEnabled();
    }

    @Override
    public String[] getAllowClasses() {
        return allowClasses;
    }

    @Override
    public String[] getDenyClasses() {
        return denyClasses;
    }

    @Override
    public boolean isSpoolEnabled() {
        return streamCachingStrategy.isSpoolEnabled();
    }

    @Override
    public String getSpoolDirectory() {
        if (streamCachingStrategy.getSpoolDirectory() != null) {
            return streamCachingStrategy.getSpoolDirectory().getPath();
        } else {
            return null;
        }
    }

    @Override
    public String getSpoolCipher() {
        return streamCachingStrategy.getSpoolCipher();
    }

    @Override
    public void setSpoolThreshold(long threshold) {
        streamCachingStrategy.setSpoolThreshold(threshold);
    }

    @Override
    public long getSpoolThreshold() {
        return streamCachingStrategy.getSpoolThreshold();
    }

    @Override
    public void setSpoolUsedHeapMemoryThreshold(int percentage) {
        streamCachingStrategy.setSpoolUsedHeapMemoryThreshold(percentage);
    }

    @Override
    public int getSpoolUsedHeapMemoryThreshold() {
        return streamCachingStrategy.getSpoolUsedHeapMemoryThreshold();
    }

    @Override
    public void setSpoolUsedHeapMemoryLimit(SpoolUsedHeapMemoryLimit limit) {
        StreamCachingStrategy.SpoolUsedHeapMemoryLimit l;
        if (limit == null) {
            l = null;
        } else {
            switch (limit) {
                case Committed:
                    l = StreamCachingStrategy.SpoolUsedHeapMemoryLimit.Committed;
                    break;
                case Max:
                    l = StreamCachingStrategy.SpoolUsedHeapMemoryLimit.Max;
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
        streamCachingStrategy.setSpoolUsedHeapMemoryLimit(l);
    }

    @Override
    public SpoolUsedHeapMemoryLimit getSpoolUsedHeapMemoryLimit() {
        StreamCachingStrategy.SpoolUsedHeapMemoryLimit l = streamCachingStrategy.getSpoolUsedHeapMemoryLimit();
        if (l == null) {
            return null;
        } else {
            switch (l) {
                case Committed:
                    return SpoolUsedHeapMemoryLimit.Committed;
                case Max:
                    return SpoolUsedHeapMemoryLimit.Max;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    @Override
    public void setBufferSize(int bufferSize) {
        streamCachingStrategy.setBufferSize(bufferSize);
    }

    @Override
    public int getBufferSize() {
        return streamCachingStrategy.getBufferSize();
    }

    @Override
    public void setRemoveSpoolDirectoryWhenStopping(boolean remove) {
        streamCachingStrategy.setRemoveSpoolDirectoryWhenStopping(remove);
    }

    @Override
    public boolean isRemoveSpoolDirectoryWhenStopping() {
        return streamCachingStrategy.isRemoveSpoolDirectoryWhenStopping();
    }

    @Override
    public void setAnySpoolRules(boolean any) {
        streamCachingStrategy.setAnySpoolRules(any);
    }

    @Override
    public boolean isAnySpoolRules() {
        return streamCachingStrategy.isAnySpoolRules();
    }

    @Override
    public long getCacheMemoryCounter() {
        return streamCachingStrategy.getStatistics().getCacheMemoryCounter();
    }

    @Override
    public long getCacheMemorySize() {
        return streamCachingStrategy.getStatistics().getCacheMemorySize();
    }

    @Override
    public long getCacheMemoryAverageSize() {
        return streamCachingStrategy.getStatistics().getCacheMemoryAverageSize();
    }

    @Override
    public long getCacheSpoolCounter() {
        return streamCachingStrategy.getStatistics().getCacheSpoolCounter();
    }

    @Override
    public long getCacheSpoolSize() {
        return streamCachingStrategy.getStatistics().getCacheSpoolSize();
    }

    @Override
    public long getCacheSpoolAverageSize() {
        return streamCachingStrategy.getStatistics().getCacheSpoolAverageSize();
    }

    @Override
    public boolean isStatisticsEnabled() {
        return streamCachingStrategy.getStatistics().isStatisticsEnabled();
    }

    @Override
    public void setStatisticsEnabled(boolean enabled) {
        streamCachingStrategy.getStatistics().setStatisticsEnabled(enabled);
    }

    @Override
    public void resetStatistics() {
        streamCachingStrategy.getStatistics().reset();
    }

}
