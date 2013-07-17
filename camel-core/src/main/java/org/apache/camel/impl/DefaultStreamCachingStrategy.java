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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StreamCache;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link StreamCachingStrategy}
 */
public class DefaultStreamCachingStrategy extends org.apache.camel.support.ServiceSupport implements CamelContextAware, StreamCachingStrategy {

    // TODO: Add JMX management
    // TODO: Maybe use #syntax# for default temp dir so ppl can easily configure this

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
    private File temporaryDirectory;
    private long spoolThreshold = StreamCache.DEFAULT_SPOOL_THRESHOLD;
    private String spoolChiper;
    private int bufferSize = IOHelper.DEFAULT_BUFFER_SIZE;
    private boolean removeTemporaryDirectoryWhenStopping = true;

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void setTemporaryDirectory(String path) {
        this.temporaryDirectory = new File(path);
    }

    public void setTemporaryDirectory(File path) {
        this.temporaryDirectory = path;
    }

    public File getTemporaryDirectory() {
        return temporaryDirectory;
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

    public boolean isRemoveTemporaryDirectoryWhenStopping() {
        return removeTemporaryDirectoryWhenStopping;
    }

    public void setRemoveTemporaryDirectoryWhenStopping(boolean removeTemporaryDirectoryWhenStopping) {
        this.removeTemporaryDirectoryWhenStopping = removeTemporaryDirectoryWhenStopping;
    }

    @Override
    protected void doStart() throws Exception {
        String bufferSize = camelContext.getProperty(BUFFER_SIZE);
        String hold = camelContext.getProperty(THRESHOLD);
        String chiper = camelContext.getProperty(CIPHER_TRANSFORMATION);
        String dir = camelContext.getProperty(TEMP_DIR);

        if (bufferSize != null) {
            this.bufferSize = camelContext.getTypeConverter().convertTo(Integer.class, bufferSize);
        }
        if (hold != null) {
            this.spoolThreshold = camelContext.getTypeConverter().convertTo(Long.class, hold);
        }
        if (chiper != null) {
            this.spoolChiper = chiper;
        }
        if (dir != null) {
            this.temporaryDirectory = camelContext.getTypeConverter().convertTo(File.class, dir);
        }

        LOG.info("StreamCaching in use with {}", this.toString());

        // create random temporary directory if none has been created
        if (temporaryDirectory == null) {
            temporaryDirectory = FileUtil.createNewTempDir();
            LOG.info("Created temporary directory {}", temporaryDirectory);
        } else {
            if (!temporaryDirectory.exists()) {
                boolean created = temporaryDirectory.mkdirs();
                if (!created) {
                    LOG.warn("Cannot create temporary directory {}", temporaryDirectory);
                } else {
                    LOG.info("Created temporary directory {}", temporaryDirectory);
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (temporaryDirectory != null  && isRemoveTemporaryDirectoryWhenStopping()) {
            LOG.info("Removing temporary directory {}", temporaryDirectory);
            FileUtil.removeDir(temporaryDirectory);
        }
    }

    @Override
    public String toString() {
        return "DefaultStreamCachingStrategy["
            + "temporaryDirectory=" + temporaryDirectory
            + ", spoolThreshold=" + spoolThreshold
            + ", spoolChiper=" + spoolChiper
            + ", bufferSize=" + bufferSize + "]";
    }
}
