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
package org.apache.camel.component.ironmq;

import io.iron.ironmq.Client;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class IronMQConfiguration {
    // common properties
    
    @UriParam
    private String projectId;
    
    @UriParam
    private String token;
    
    @UriPath @Metadata(required = "true")
    private String queueName;
    
    @UriParam(defaultValue = "https://mq-aws-us-east-1.iron.io")
    private String ironMQCloud = "https://mq-aws-us-east-1.iron.io";
    
    @UriParam
    private boolean preserveHeaders;

    @UriParam
    private Client client;

    // producer properties
    @UriParam(label = "producer")
    private int visibilityDelay;

    // consumer properties
    @UriParam(defaultValue = "1", label = "consumer")
    private Integer concurrentConsumers = 1;
    
    @UriParam(label = "consumer")
    private boolean batchDelete;
    
    @UriParam(defaultValue = "1", label = "consumer")
    private int maxMessagesPerPoll = 1;
    
    @UriParam(defaultValue = "60", label = "consumer")
    private int timeout = 60;
    
    @UriParam(label = "consumer")
    private int wait;

    public Client getClient() {
        return client;
    }

    /**
     * Reference to a io.iron.ironmq.Client in the Registry. 
     */
    public void setClient(Client client) {
        this.client = client;
    }

    public Integer getConcurrentConsumers() {
        return concurrentConsumers;
    }

    /**
     * The number of concurrent consumers.
     */
    public void setConcurrentConsumers(Integer concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public String getProjectId() {
        return projectId;
    }

    /**
     * IronMq projectId
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getToken() {
        return token;
    }

    /**
     * IronMq token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * The name of the IronMQ queue 
     */
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getQueueName() {
        return queueName;
    }

    /**
     * IronMq Cloud url. See http://dev.iron.io/mq/reference/clouds/ for valid options
     */
    public void setIronMQCloud(String ironMQCloud) {
        this.ironMQCloud = ironMQCloud;
    }

    public String getIronMQCloud() {
        return ironMQCloud;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * sets the timeout
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Number of messages to poll pr. call
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public int getVisibilityDelay() {
        return visibilityDelay;
    }

    /**
     * Set's the visibility delay in seconds.
     */
    public void setVisibilityDelay(int visibilityDelay) {
        this.visibilityDelay = visibilityDelay;
    }

    public boolean isPreserveHeaders() {
        return preserveHeaders;
    }

    /**
     * Should camel message headers be preserved when publishing messages
     */
    public void setPreserveHeaders(boolean preserveHeaders) {
        this.preserveHeaders = preserveHeaders;
    }

    public boolean isBatchDelete() {
        return batchDelete;
    }

    /**
     * Shold messages be deleted in one batch or one at the time
     */
    public void setBatchDelete(boolean batchDelete) {
        this.batchDelete = batchDelete;
    }

    public int getWait() {
        return wait;
    }

    /**
     * Sets the wait
     */
    public void setWait(int wait) {
        this.wait = wait;
    }
}
