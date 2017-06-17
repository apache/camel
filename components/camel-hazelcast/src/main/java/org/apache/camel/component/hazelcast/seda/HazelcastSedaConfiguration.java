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
package org.apache.camel.component.hazelcast.seda;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Hazelcast SEDA Component configuration.
 */
@UriParams
public class HazelcastSedaConfiguration {

    // is the cache name
    private transient String queueName;

    @UriParam(label = "seda", defaultValue = "1")
    private int concurrentConsumers = 1;
    @UriParam(label = "seda", defaultValue = "1000")
    private int pollTimeout = 1000;
    @UriParam(label = "seda", defaultValue = "1000")
    private int onErrorDelay = 1000;
    @UriParam(label = "seda")
    private boolean transferExchange;
    @UriParam(label = "seda")
    private boolean transacted;

    public HazelcastSedaConfiguration() {
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    /**
     * To use concurrent consumers polling from the SEDA queue.
     */
    public void setConcurrentConsumers(final int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    /**
     * @deprecated use pollTimeout instead
     */
    @Deprecated
    public int getPollInterval() {
        return pollTimeout;
    }

    /**
     * @deprecated use pollTimeout instead
     */
    @Deprecated
    public void setPollInterval(int pollInterval) {
        this.pollTimeout = pollInterval;
    }

    public int getPollTimeout() {
        return pollTimeout;
    }

    /**
     * The timeout used when consuming from the SEDA queue. When a timeout occurs, the consumer can check whether
     * it is allowed to continue running. Setting a lower value allows the consumer to react more quickly upon shutdown.
     */
    public void setPollTimeout(int pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    public boolean isTransferExchange() {
        return transferExchange;
    }

    /**
     * Milliseconds before consumer continues polling after an error has occurred.
     */
    public void setOnErrorDelay(int onErrorDelay) {
        if (onErrorDelay < 0) {
            throw new IllegalArgumentException("Property onErrorDelay must be a positive number, was " + onErrorDelay);
        }
        this.onErrorDelay = onErrorDelay;
    }

    public int getOnErrorDelay() {
        return onErrorDelay;
    }

    /**
     * If set to true the whole Exchange will be transfered. If header or body contains not serializable objects, they will be skipped.
     */
    public void setTransferExchange(boolean transferExchange) {
        this.transferExchange = transferExchange;
    }

    public boolean isTransacted() {
        return transacted;
    }

    /**
     * If set to true then the consumer runs in transaction mode, where the messages in the seda queue will only be removed
     * if the transaction commits, which happens when the processing is complete.
     */
    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

}
