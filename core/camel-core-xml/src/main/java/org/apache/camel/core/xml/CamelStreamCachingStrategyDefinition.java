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
package org.apache.camel.core.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.Metadata;

/**
 * Stream caching configuration.
 */
@Metadata(label = "spring,configuration")
@XmlRootElement(name = "streamCaching")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelStreamCachingStrategyDefinition extends IdentifiedType {

    @XmlAttribute @Metadata(defaultValue = "false")
    private String enabled;
    @XmlAttribute
    private String spoolDirectory;
    @XmlAttribute
    private String spoolCipher;
    @XmlAttribute
    private String spoolThreshold;
    @XmlAttribute
    private String spoolUsedHeapMemoryThreshold;
    @XmlAttribute
    private String spoolUsedHeapMemoryLimit;
    @XmlAttribute
    private String spoolRules;
    @XmlAttribute
    private String bufferSize;
    @XmlAttribute @Metadata(defaultValue = "true")
    private String removeSpoolDirectoryWhenStopping;
    @XmlAttribute
    private String statisticsEnabled;
    @XmlAttribute @Metadata(defaultValue = "false")
    private String anySpoolRules;

    public String getEnabled() {
        return enabled;
    }

    /**
     * Sets whether the stream caching is enabled.
     * <p/>
     * <b>Notice:</b> This cannot be changed at runtime.
     */
    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public String getSpoolDirectory() {
        return spoolDirectory;
    }

    /**
     * Sets the spool (temporary) directory to use for overflow and spooling to disk.
     * <p/>
     * If no spool directory has been explicit configured, then a temporary directory
     * is created in the <tt>java.io.tmpdir</tt> directory.
     */
    public void setSpoolDirectory(String spoolDirectory) {
        this.spoolDirectory = spoolDirectory;
    }

    public String getSpoolCipher() {
        return spoolCipher;
    }

    /**
     * Sets a cipher name to use when spooling to disk to write with encryption.
     * <p/>
     * By default the data is not encrypted.
     */
    public void setSpoolCipher(String spoolCipher) {
        this.spoolCipher = spoolCipher;
    }

    public String getSpoolThreshold() {
        return spoolThreshold;
    }

    /**
     * Threshold in bytes when overflow to disk is activated.
     * <p/>
     * The default threshold is {@link org.apache.camel.StreamCache#DEFAULT_SPOOL_THRESHOLD} bytes (eg 128kb).
     * Use <tt>-1</tt> to disable overflow to disk.
     */
    public void setSpoolThreshold(String spoolThreshold) {
        this.spoolThreshold = spoolThreshold;
    }

    public String getSpoolUsedHeapMemoryThreshold() {
        return spoolUsedHeapMemoryThreshold;
    }

    /**
     * Sets a percentage (1-99) of used heap memory threshold to activate spooling to disk.
     */
    public void setSpoolUsedHeapMemoryThreshold(String spoolUsedHeapMemoryThreshold) {
        this.spoolUsedHeapMemoryThreshold = spoolUsedHeapMemoryThreshold;
    }

    public String getSpoolUsedHeapMemoryLimit() {
        return spoolUsedHeapMemoryLimit;
    }

    /**
     * Sets what the upper bounds should be when spoolUsedHeapMemoryThreshold
     * is in use.
     */
    public void setSpoolUsedHeapMemoryLimit(String spoolUsedHeapMemoryLimit) {
        this.spoolUsedHeapMemoryLimit = spoolUsedHeapMemoryLimit;
    }

    public String getSpoolRules() {
        return spoolRules;
    }

    /**
     * Reference to one or more custom {@link org.apache.camel.spi.StreamCachingStrategy.SpoolRule} to use.
     * Multiple rules can be separated by comma.
     */
    public void setSpoolRules(String spoolRules) {
        this.spoolRules = spoolRules;
    }

    public String getBufferSize() {
        return bufferSize;
    }

    /**
     * Sets the buffer size to use when allocating in-memory buffers used for in-memory stream caches.
     * <p/>
     * The default size is {@link org.apache.camel.util.IOHelper#DEFAULT_BUFFER_SIZE}
     */
    public void setBufferSize(String bufferSize) {
        this.bufferSize = bufferSize;
    }

    public String getRemoveSpoolDirectoryWhenStopping() {
        return removeSpoolDirectoryWhenStopping;
    }

    /**
     * Whether to remove the temporary directory when stopping.
     * <p/>
     * This option is default <tt>true</tt>
     */
    public void setRemoveSpoolDirectoryWhenStopping(String removeSpoolDirectoryWhenStopping) {
        this.removeSpoolDirectoryWhenStopping = removeSpoolDirectoryWhenStopping;
    }

    public String getStatisticsEnabled() {
        return statisticsEnabled;
    }

    /**
     * Sets whether statistics is enabled.
     */
    public void setStatisticsEnabled(String statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    public String getAnySpoolRules() {
        return anySpoolRules;
    }

    /**
     * Sets whether if just any of the {@link org.apache.camel.spi.StreamCachingStrategy.SpoolRule} rules
     * returns <tt>true</tt> then shouldSpoolCache(long) returns <tt>true</tt>.
     * If this option is <tt>false</tt>, then <b>all</b> the {@link org.apache.camel.spi.StreamCachingStrategy.SpoolRule} must
     * return <tt>true</tt>.
     * <p/>
     * The default value is <tt>false</tt> which means that all the rules must return <tt>true</tt>.
     */
    public void setAnySpoolRules(String anySpoolRules) {
        this.anySpoolRules = anySpoolRules;
    }

}
