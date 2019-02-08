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
package org.apache.camel.model.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Configures batch-processing resequence eip.
 */
@Metadata(label = "eip,routing,resequence")
@XmlRootElement(name = "batch-config")
@XmlAccessorType(XmlAccessType.FIELD)
public class BatchResequencerConfig extends ResequencerConfig {
    @XmlAttribute @Metadata(defaultValue = "100")
    private Integer batchSize;
    @XmlAttribute @Metadata(defaultValue = "1000")
    private Long batchTimeout;
    @XmlAttribute
    private Boolean allowDuplicates;
    @XmlAttribute
    private Boolean reverse;
    @XmlAttribute
    private Boolean ignoreInvalidExchanges;

    /**
     * Creates a new {@link BatchResequencerConfig} instance using default
     * values for <code>batchSize</code> (100) and <code>batchTimeout</code>
     * (1000L).
     */
    public BatchResequencerConfig() {
        this(100, 1000L);
    }

    /**
     * Creates a new {@link BatchResequencerConfig} instance using the given
     * values for <code>batchSize</code> and <code>batchTimeout</code>.
     *
     * @param batchSize    size of the batch to be re-ordered.
     * @param batchTimeout timeout for collecting elements to be re-ordered.
     */
    public BatchResequencerConfig(int batchSize, long batchTimeout) {
        this.batchSize = batchSize;
        this.batchTimeout = batchTimeout;
    }

    /**
     * Returns a new {@link BatchResequencerConfig} instance using default
     * values for <code>batchSize</code> (100) and <code>batchTimeout</code>
     * (1000L).
     *
     * @return a default {@link BatchResequencerConfig}.
     */
    public static BatchResequencerConfig getDefault() {
        return new BatchResequencerConfig();
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the size of the batch to be re-ordered. The default size is 100.
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    /**
     * Sets the timeout for collecting elements to be re-ordered. The default timeout is 1000 msec.
     */
    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    public Boolean getAllowDuplicates() {
        return allowDuplicates;
    }

    /**
     * Whether to allow duplicates.
     */
    public void setAllowDuplicates(Boolean allowDuplicates) {
        this.allowDuplicates = allowDuplicates;
    }

    public Boolean getReverse() {
        return reverse;
    }

    /**
     * Whether to reverse the ordering.
     */
    public void setReverse(Boolean reverse) {
        this.reverse = reverse;
    }

    public Boolean getIgnoreInvalidExchanges() {
        return ignoreInvalidExchanges;
    }

    /**
     * Whether to ignore invalid exchanges
     */
    public void setIgnoreInvalidExchanges(Boolean ignoreInvalidExchanges) {
        this.ignoreInvalidExchanges = ignoreInvalidExchanges;
    }
}
