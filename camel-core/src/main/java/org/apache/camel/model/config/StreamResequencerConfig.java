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
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.processor.resequencer.DefaultExchangeComparator;
import org.apache.camel.processor.resequencer.ExpressionResultComparator;

/**
 * Defines the configuration parameters for the {@link org.apache.camel.processor.StreamResequencer}.
 *
 * @version 
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class StreamResequencerConfig extends ResequencerConfig {
    @XmlAttribute
    private Integer capacity;
    @XmlAttribute
    private Long timeout;
    @XmlAttribute
    private Boolean ignoreInvalidExchanges;
    @XmlTransient
    private ExpressionResultComparator comparator;
    @XmlAttribute
    private Boolean rejectOld;

    /**
     * Creates a new {@link StreamResequencerConfig} instance using default
     * values for <code>capacity</code> (1000) and <code>timeout</code>
     * (1000L). Elements of the sequence are compared using the
     * {@link DefaultExchangeComparator}.
     */
    public StreamResequencerConfig() {
        this(1000, 1000L);
    }

    /**
     * Creates a new {@link StreamResequencerConfig} instance using the given
     * values for <code>capacity</code> and <code>timeout</code>. Elements
     * of the sequence are compared using the {@link DefaultExchangeComparator}.
     * 
     * @param capacity   capacity of the resequencer's inbound queue.
     * @param timeout    minimum time to wait for missing elements (messages).
     */
    public StreamResequencerConfig(int capacity, long timeout) {
        this(capacity, timeout, new DefaultExchangeComparator());
    }

    /**
     * Creates a new {@link StreamResequencerConfig} instance using the given
     * values for <code>capacity</code> and <code>timeout</code>. Elements
     * of the sequence are compared with the given
     * {@link ExpressionResultComparator}.
     * 
     * @param capacity   capacity of the resequencer's inbound queue.
     * @param timeout    minimum time to wait for missing elements (messages).
     * @param comparator comparator for sequence comparision
     */
    public StreamResequencerConfig(int capacity, long timeout, ExpressionResultComparator comparator) {
        this.capacity = capacity;
        this.timeout = timeout;
        this.comparator = comparator;
    }

    /**
     * Creates a new {@link StreamResequencerConfig} instance using the given
     * values for <code>capacity</code> and <code>timeout</code>. Elements
     * of the sequence are compared using the {@link DefaultExchangeComparator}.
     *
     * @param capacity   capacity of the resequencer's inbound queue.
     * @param timeout    minimum time to wait for missing elements (messages).
     * @param rejectOld  if true, throws an error when messages older than the last delivered message are processed
     */
    public StreamResequencerConfig(int capacity, long timeout, Boolean rejectOld) {
        this(capacity, timeout, rejectOld, new DefaultExchangeComparator());
    }

    /**
     * Creates a new {@link StreamResequencerConfig} instance using the given
     * values for <code>capacity</code> and <code>timeout</code>. Elements
     * of the sequence are compared with the given {@link ExpressionResultComparator}.
     *
     * @param capacity   capacity of the resequencer's inbound queue.
     * @param timeout    minimum time to wait for missing elements (messages).
     * @param rejectOld  if true, throws an error when messages older than the last delivered message are processed
     * @param comparator comparator for sequence comparision
     */
    public StreamResequencerConfig(int capacity, long timeout, Boolean rejectOld, ExpressionResultComparator comparator) {
        this.capacity = capacity;
        this.timeout = timeout;
        this.rejectOld = rejectOld;
        this.comparator = comparator;
    }

    /**
     * Returns a new {@link StreamResequencerConfig} instance using default
     * values for <code>capacity</code> (1000) and <code>timeout</code>
     * (1000L). Elements of the sequence are compared using the
     * {@link DefaultExchangeComparator}.
     * 
     * @return a default {@link StreamResequencerConfig}.
     */
    public static StreamResequencerConfig getDefault() {
        return new StreamResequencerConfig();
    }
    
    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Boolean getIgnoreInvalidExchanges() {
        return ignoreInvalidExchanges;
    }

    public void setIgnoreInvalidExchanges(Boolean ignoreInvalidExchanges) {
        this.ignoreInvalidExchanges = ignoreInvalidExchanges;
    }

    public ExpressionResultComparator getComparator() {
        return comparator;
    }

    public void setComparator(ExpressionResultComparator comparator) {
        this.comparator = comparator;
    }

    public void setRejectOld(boolean value) {
        this.rejectOld = value;
    }

    public Boolean getRejectOld() {
        return rejectOld;
    }

}
