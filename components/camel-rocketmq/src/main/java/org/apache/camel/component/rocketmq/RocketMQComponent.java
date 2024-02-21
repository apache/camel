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

package org.apache.camel.component.rocketmq;

import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("rocketmq")
public class RocketMQComponent extends DefaultComponent {

    @Metadata(label = "producer")
    private String producerGroup;

    @Metadata(label = "consumer")
    private String consumerGroup;

    @Metadata(label = "consumer", defaultValue = "*")
    private String subscribeTags = "*";

    @Metadata(label = "common")
    private String sendTag = "";

    @Metadata(label = "common", defaultValue = "localhost:9876")
    private String namesrvAddr = "localhost:9876";

    @Metadata(label = "producer")
    private String replyToTopic;

    @Metadata(label = "producer")
    private String replyToConsumerGroup;

    @Metadata(label = "advanced", defaultValue = "10000")
    private long requestTimeoutMillis = 10000L;

    @Metadata(label = "advanced", defaultValue = "1000")
    private long requestTimeoutCheckerIntervalMillis = 1000L;

    @Metadata(label = "producer", defaultValue = "false")
    private boolean waitForSendResult;

    @Metadata(label = "secret", secret = true)
    private String accessKey;

    @Metadata(label = "secret", secret = true)
    private String secretKey;

    @Override
    protected RocketMQEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RocketMQEndpoint endpoint = new RocketMQEndpoint(uri, this);
        endpoint.setProducerGroup(getProducerGroup());
        endpoint.setConsumerGroup(getConsumerGroup());
        endpoint.setSubscribeTags(getSubscribeTags());
        endpoint.setNamesrvAddr(getNamesrvAddr());
        endpoint.setSendTag(getSendTag());
        endpoint.setReplyToTopic(getReplyToTopic());
        endpoint.setReplyToConsumerGroup(getReplyToConsumerGroup());
        endpoint.setRequestTimeoutMillis(getRequestTimeoutMillis());
        endpoint.setRequestTimeoutCheckerIntervalMillis(getRequestTimeoutCheckerIntervalMillis());
        endpoint.setWaitForSendResult(isWaitForSendResult());
        endpoint.setAccessKey(getAccessKey());
        endpoint.setSecretKey(getSecretKey());
        setProperties(endpoint, parameters);
        endpoint.setTopicName(remaining);
        return endpoint;
    }

    public String getSubscribeTags() {
        return subscribeTags;
    }

    /**
     * Subscribe tags of consumer. Multiple tags could be split by "||", such as "TagA||TagB"
     */
    public void setSubscribeTags(String subscribeTags) {
        this.subscribeTags = subscribeTags;
    }

    public String getSendTag() {
        return sendTag;
    }

    /**
     * Each message would be sent with this tag.
     */
    public void setSendTag(String sendTag) {
        this.sendTag = sendTag;
    }

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    /**
     * Name server address of RocketMQ cluster.
     */
    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    /**
     * Producer group name.
     */
    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    /**
     * Consumer group name.
     */
    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getReplyToTopic() {
        return replyToTopic;
    }

    /**
     * Topic used for receiving response when using in-out pattern.
     */
    public void setReplyToTopic(String replyToTopic) {
        this.replyToTopic = replyToTopic;
    }

    public String getReplyToConsumerGroup() {
        return replyToConsumerGroup;
    }

    /**
     * Consumer group name used for receiving response.
     */
    public void setReplyToConsumerGroup(String replyToConsumerGroup) {
        this.replyToConsumerGroup = replyToConsumerGroup;
    }

    public long getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    /**
     * Timeout milliseconds of receiving response when using in-out pattern.
     */
    public void setRequestTimeoutMillis(long requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public long getRequestTimeoutCheckerIntervalMillis() {
        return requestTimeoutCheckerIntervalMillis;
    }

    /**
     * Check interval milliseconds of request timeout.
     */
    public void setRequestTimeoutCheckerIntervalMillis(long requestTimeoutCheckerIntervalMillis) {
        this.requestTimeoutCheckerIntervalMillis = requestTimeoutCheckerIntervalMillis;
    }

    public boolean isWaitForSendResult() {
        return waitForSendResult;
    }

    /**
     * Whether waiting for send result before routing to next endpoint.
     */
    public void setWaitForSendResult(boolean waitForSendResult) {
        this.waitForSendResult = waitForSendResult;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Access key for RocketMQ ACL.
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Secret key for RocketMQ ACL.
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
