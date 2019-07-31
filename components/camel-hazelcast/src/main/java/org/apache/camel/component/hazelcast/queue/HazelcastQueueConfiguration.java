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
package org.apache.camel.component.hazelcast.queue;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Hazelcast Queue Component configuration.
 */
@UriParams
public class HazelcastQueueConfiguration {

    @UriParam(label = "consumer", defaultValue = "10000")
    private long pollingTimeout = 10000L;
    @UriParam(label = "consumer", defaultValue = "Listen")
    private HazelcastQueueConsumerMode queueConsumerMode = HazelcastQueueConsumerMode.LISTEN;
    @UriParam(label = "consumer", defaultValue = "1")
    private int poolSize = 1;


    /**
     * Define the polling timeout of the Queue consumer in Poll mode
     */
    public long getPollingTimeout() {
        return pollingTimeout;
    }

    public void setPollingTimeout(long pollingTimeout) {
        this.pollingTimeout = pollingTimeout;
    }

    /**
     * Define the Queue Consumer mode: Listen or Poll 
     */
    public HazelcastQueueConsumerMode getQueueConsumerMode() {
        return queueConsumerMode;
    }

    public void setQueueConsumerMode(HazelcastQueueConsumerMode queueConsumerMode) {
        this.queueConsumerMode = queueConsumerMode;
    }

    /**
     * Define the Pool size for Queue Consumer Executor
     */
    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
}
