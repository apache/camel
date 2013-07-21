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
package org.apache.camel.impl;

import java.io.File;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.util.FilePathResolver;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link StreamCachingStrategy}
 */
public class DefaultStreamCachingStrategy extends org.apache.camel.support.ServiceSupport implements CamelContextAware, StreamCachingStrategy {

    // TODO: logic for spool to disk in this class so we can control this
    // TODO: add memory based watermarks for spool to disk

    @Deprecated
    public static final String THRESHOLD = "CamelCachedOutputStreamThreshold";
    @Deprecated
    public static final String BUFFER_SIZE = "CamelCachedOutputStreamBufferSize";
    @Deprecated
    public static final String TEMP_DIR = "CamelCachedOutputStreamOutputDirectory";
    @Deprecated
    public static final String CIPHER_TRANSFORMATION = "CamelCachedOutputStreamCipherTransformation";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultStreamCachingStrategy.class);

    private CamelContext camelContext;
    private boolean enabled;
    private File spoolDirectory;
    private transient String spoolDirectoryName = "${java.io.tmpdir}camel-tmp-#uuid#";
    private long spoolThreshold = StreamCache.DEFAULT_SPOOL_THRESHOLD;
    private String spoolChiper;
    private int bufferSize = IOHelper.DEFAULT_BUFFER_SIZE;
    private boolean removeSpoolDirectoryWhenStopping = true;
    private final UtilizationStatistics statistics = new UtilizationStatistics();

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSpoolDirectory(String path) {
        this.spoolDirectoryName = path;
    }

    public void setSpoolDirectory(File path) {
        this.spoolDirectory = path;
    }

    public File getSpoolDirectory() {
        return spoolDirectory;
    }

    public long getSpoolThreshold() {
        return spoolThreshold;
    }

    public void setSpoolThreshold(long spoolThreshold) {
        this.spoolThreshold = spoolThreshold;
    }

    public String getSpoolChiper() {
        return spoolChiper;
    }

    public void setSpoolChiper(String spoolChiper) {
        this.spoolChiper = spoolChiper;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public boolean isRemoveSpoolDirectoryWhenStopping() {
        return removeSpoolDirectoryWhenStopping;
    }

    public void setRemoveSpoolDirectoryWhenStopping(boolean removeSpoolDirectoryWhenStopping) {
        this.removeSpoolDirectoryWhenStopping = removeSpoolDirectoryWhenStopping;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public StreamCache cache(Exchange exchange) {
        StreamCache cache = exchange.getIn().getBody(StreamCache.class);
        if (cache != null && statistics.isStatisticsEnabled()) {
            try {
                if (cache.inMemory()) {
                    statistics.updateMemory(cache.length());
                } else {
                    statistics.updateSpool(cache.length());
                }
            } catch (Exception e) {
                LOG.debug("Error updating cache statistics. This exception is ignored.", e);
            }
        }
        return cache;
    }

    @Override
    protected void doStart() throws Exception {
        if (!enabled) {
            LOG.debug("StreamCaching is not enabled");
            return;
        }

        String bufferSize = camelContext.getProperty(BUFFER_SIZE);
        String hold = camelContext.getProperty(THRESHOLD);
        String chiper = camelContext.getProperty(CIPHER_TRANSFORMATION);
        String dir = camelContext.getProperty(TEMP_DIR);

        boolean warn = false;
        if (bufferSize != null) {
            warn = true;
            this.bufferSize = camelContext.getTypeConverter().convertTo(Integer.class, bufferSize);
        }
        if (hold != null) {
            warn = true;
            this.spoolThreshold = camelContext.getTypeConverter().convertTo(Long.class, hold);
        }
        if (chiper != null) {
            warn = true;
            this.spoolChiper = chiper;
        }
        if (dir != null) {
            warn = true;
            this.spoolDirectory = camelContext.getTypeConverter().convertTo(File.class, dir);
        }
        if (warn) {
            LOG.warn("Configuring of StreamCaching using CamelContext properties is deprecated - use StreamCachingStrategy instead.");
        }

        // if we can overflow to disk then make sure directory exists / is created
        if (spoolThreshold > 0) {

            if (spoolDirectory == null && spoolDirectoryName == null) {
                throw new IllegalArgumentException("SpoolDirectory must be configured when using SpoolThreshold > 0");
            }

            if (spoolDirectory == null && spoolDirectoryName != null) {
                String name = resolveSpoolDirectory(spoolDirectoryName);
                if (name != null) {
                    spoolDirectory = new File(name);
                    spoolDirectoryName = null;
                }
            }

            if (spoolDirectory.exists()) {
                if (spoolDirectory.isDirectory()) {
                    LOG.debug("Using spool directory: {}", spoolDirectory);
                } else {
                    LOG.warn("Spool directory: {} is not a directory. This may cause problems spooling to disk for the stream caching!", spoolDirectory);
                }
            } else {
                boolean created = spoolDirectory.mkdirs();
                if (!created) {
                    LOG.warn("Cannot create spool directory: {}. This may cause problems spooling to disk for the stream caching!", spoolDirectory);
                } else {
                    LOG.debug("Created spool directory: {}", spoolDirectory);
                }
            }
        }

        LOG.debug("StreamCaching configuration {}", this.toString());

        if (spoolThreshold > 0) {
            LOG.info("StreamCaching in use and overflow to disk enabled when > {} bytes to directory: {}", spoolThreshold, spoolDirectory);
        } else {
            LOG.info("StreamCaching in use with no overflow to disk (memory only)");
        }
    }

    protected String resolveSpoolDirectory(String path) {
        String name = camelContext.getManagementNameStrategy().resolveManagementName(path, camelContext.getName(), false);
        if (name != null) {
            name = customResolveManagementName(name);
        }
        // and then check again with invalid check to ensure all ## is resolved
        if (name != null) {
            name = camelContext.getManagementNameStrategy().resolveManagementName(name, camelContext.getName(), true);
        }
        return name;
    }

    protected String customResolveManagementName(String pattern) {
        if (pattern.contains("#uuid#")) {
            String uuid = UUID.randomUUID().toString();
            pattern = pattern.replaceFirst("#uuid#", uuid);
        }
        return FilePathResolver.resolvePath(pattern);
    }

    @Override
    protected void doStop() throws Exception {
        if (spoolThreshold > 0 & spoolDirectory != null  && isRemoveSpoolDirectoryWhenStopping()) {
            LOG.debug("Removing spool directory: {}", spoolDirectory);
            FileUtil.removeDir(spoolDirectory);
        }

        if (LOG.isDebugEnabled() && statistics.isStatisticsEnabled()) {
            LOG.debug("Stopping StreamCachingStrategy with statistics: {}", statistics.toString());
        }

        statistics.reset();
    }

    @Override
    public String toString() {
        return "DefaultStreamCachingStrategy["
            + "spoolDirectory=" + spoolDirectory
            + ", spoolThreshold=" + spoolThreshold
            + ", spoolChiper=" + spoolChiper
            + ", bufferSize=" + bufferSize + "]";
    }

    /**
     * Represents utilization statistics
     */
    private final class UtilizationStatistics implements Statistics {

        private boolean statisticsEnabled;
        private volatile long memoryCounter;
        private volatile long memorySize;
        private volatile long memoryAverageSize;
        private volatile long spoolCounter;
        private volatile long spoolSize;
        private volatile long spoolAverageSize;

        void updateMemory(long size) {
            memoryCounter++;
            memorySize += size;
            memoryAverageSize = memorySize / memoryCounter;
        }

        void updateSpool(long size) {
            spoolCounter++;
            spoolSize += size;
            spoolAverageSize = spoolSize / spoolCounter;
        }

        public long getCacheMemoryCounter() {
            return memoryCounter;
        }

        public long getCacheMemorySize() {
            return memorySize;
        }

        public long getCacheMemoryAverageSize() {
            return memoryAverageSize;
        }

        public long getCacheSpoolCounter() {
            return spoolCounter;
        }

        public long getCacheSpoolSize() {
            return spoolSize;
        }

        public long getCacheSpoolAverageSize() {
            return spoolAverageSize;
        }

        public void reset() {
            memoryCounter = 0;
            memorySize = 0;
            memoryAverageSize = 0;
            spoolCounter = 0;
            spoolSize = 0;
            spoolAverageSize = 0;
        }

        public boolean isStatisticsEnabled() {
            return statisticsEnabled;
        }

        public void setStatisticsEnabled(boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
        }

        public String toString() {
            return String.format("[memoryCounter=%s, memorySize=%s, memoryAverageSize=%s, spoolCounter=%s, spoolSize=%s, spoolAverageSize=%s]",
                    memoryCounter, memorySize, memoryAverageSize, spoolCounter, spoolSize, spoolAverageSize);
        }
    }

}
