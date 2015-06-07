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

/**
 * Hazelcast SEDA Component configuration.
 */
public class HazelcastSedaConfiguration {

    private int concurrentConsumers = 1;
    private int pollTimeout = 1000;
    private String queueName;
    private boolean transferExchange;
    private boolean transacted;

    public HazelcastSedaConfiguration() {
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(final int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(final String queueName) {
        this.queueName = queueName;
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

    public void setPollTimeout(int pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    public boolean isTransferExchange() {
        return transferExchange;
    }

    public void setTransferExchange(boolean transferExchange) {
        this.transferExchange = transferExchange;
    }

    public boolean isTransacted() {
        return transacted;
    }

    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
    }

}
