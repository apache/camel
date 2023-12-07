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
package org.apache.camel.model.config;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Configures batch-processing resequence eip.
 */
@Metadata(label = "configuration,eip")
@XmlRootElement(name = "batchConfig")
@XmlAccessorType(XmlAccessType.FIELD)
public class BatchResequencerConfig extends ResequencerConfig {

    @XmlAttribute
    @Metadata(defaultValue = "100", javaType = "java.lang.Integer")
    private String batchSize;
    @XmlAttribute
    @Metadata(defaultValue = "1000", javaType = "java.time.Duration")
    private String batchTimeout;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String allowDuplicates;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String reverse;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String ignoreInvalidExchanges;

    /**
     * Creates a new {@link BatchResequencerConfig} instance using default values for <code>batchSize</code> (100) and
     * <code>batchTimeout</code> (1000L).
     */
    public BatchResequencerConfig() {
        this(100, 1000L);
    }

    /**
     * Creates a new {@link BatchResequencerConfig} instance using the given values for <code>batchSize</code> and
     * <code>batchTimeout</code>.
     *
     * @param batchSize    size of the batch to be re-ordered.
     * @param batchTimeout timeout for collecting elements to be re-ordered.
     */
    public BatchResequencerConfig(int batchSize, long batchTimeout) {
        this.batchSize = Integer.toString(batchSize);
        this.batchTimeout = Long.toString(batchTimeout);
    }

    /**
     * Returns a new {@link BatchResequencerConfig} instance using default values for <code>batchSize</code> (100) and
     * <code>batchTimeout</code> (1000L).
     *
     * @return a default {@link BatchResequencerConfig}.
     */
    public static BatchResequencerConfig getDefault() {
        return new BatchResequencerConfig();
    }

    public String getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the size of the batch to be re-ordered. The default size is 100.
     */
    public void setBatchSize(String batchSize) {
        this.batchSize = batchSize;
    }

    public String getBatchTimeout() {
        return batchTimeout;
    }

    /**
     * Sets the timeout for collecting elements to be re-ordered. The default timeout is 1000 msec.
     */
    public void setBatchTimeout(String batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    public String getAllowDuplicates() {
        return allowDuplicates;
    }

    /**
     * Whether to allow duplicates.
     */
    public void setAllowDuplicates(String allowDuplicates) {
        this.allowDuplicates = allowDuplicates;
    }

    public String getReverse() {
        return reverse;
    }

    /**
     * Whether to reverse the ordering.
     */
    public void setReverse(String reverse) {
        this.reverse = reverse;
    }

    public String getIgnoreInvalidExchanges() {
        return ignoreInvalidExchanges;
    }

    /**
     * Whether to ignore invalid exchanges
     */
    public void setIgnoreInvalidExchanges(String ignoreInvalidExchanges) {
        this.ignoreInvalidExchanges = ignoreInvalidExchanges;
    }
}
