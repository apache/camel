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
    
    @UriParam(defaultValue = "https://mq-aws-us-east-1-1.iron.io")
    private String ironMQCloud = "https://mq-aws-us-east-1-1.iron.io";
    
    @UriParam
    private boolean preserveHeaders;

    @UriParam
    private Client client;

    // producer properties
    @UriParam(label = "producer")
    private int visibilityDelay;

    // consumer properties
    @UriParam(defaultValue = "1", label = "consumer")
    private int concurrentConsumers = 1;
    
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

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    /**
     * The number of concurrent consumers.
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public String getProjectId() {
        return projectId;
    }

    /**
     * IronMQ projectId
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getToken() {
        return token;
    }

    /**
     * IronMQ token
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
     * IronMq Cloud url. Urls for public clusters: https://mq-aws-us-east-1-1.iron.io (US) and https://mq-aws-eu-west-1-1.iron.io (EU)
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
     * After timeout (in seconds), item will be placed back onto the queue.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Number of messages to poll pr. call. Maximum is 100.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public int getVisibilityDelay() {
        return visibilityDelay;
    }

    /**
     * The item will not be available on the queue until this many seconds have passed. 
     * Default is 0 seconds.
     */
    public void setVisibilityDelay(int visibilityDelay) {
        this.visibilityDelay = visibilityDelay;
    }

    public boolean isPreserveHeaders() {
        return preserveHeaders;
    }

    /**
     * Should message headers be preserved when publishing messages.
     * This will add the Camel headers to the Iron MQ message as a json payload with a header list, and a message body.
     * Useful when Camel is both consumer and producer.
     */
    public void setPreserveHeaders(boolean preserveHeaders) {
        this.preserveHeaders = preserveHeaders;
    }

    public boolean isBatchDelete() {
        return batchDelete;
    }

    /**
     * Should messages be deleted in one batch. 
     * This will limit the number of api requests since messages are deleted in one request, instead of one pr. exchange. 
     * If enabled care should be taken that the consumer is idempotent when processing exchanges.
     */
    public void setBatchDelete(boolean batchDelete) {
        this.batchDelete = batchDelete;
    }

    public int getWait() {
        return wait;
    }

    /**
     * Time in seconds to wait for a message to become available. 
     * This enables long polling. Default is 0 (does not wait), maximum is 30.
     */
    public void setWait(int wait) {
        this.wait = wait;
    }
}
