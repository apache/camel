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

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultMessage;

/**
 * Send and receive messages from <a href="https://rocketmq.apache.org/">RocketMQ</a> cluster.
 */
@UriEndpoint(firstVersion = "3.20.0", scheme = "rocketmq", syntax = "rocketmq:topicName", title = "RocketMQ",
             category = Category.MESSAGING, headersClass = RocketMQConstants.class)
public class RocketMQEndpoint extends DefaultEndpoint implements AsyncEndpoint {

    @UriPath
    @Metadata(required = true)
    private String topicName;
    @UriParam(label = "producer")
    private String producerGroup;
    @UriParam(label = "consumer")
    private String consumerGroup;
    @UriParam(label = "consumer", defaultValue = "*")
    private String subscribeTags = "*";
    @UriParam(label = "producer")
    private String sendTag = "";
    @UriParam(label = "producer")
    private String replyToTopic;
    @UriParam(label = "producer")
    private String replyToConsumerGroup;
    @UriParam(label = "common", defaultValue = "localhost:9876")
    private String namesrvAddr = "localhost:9876";
    @UriParam(label = "advanced", defaultValue = "10000")
    private long requestTimeoutMillis = 10000L;
    @UriParam(label = "advanced", defaultValue = "1000")
    private long requestTimeoutCheckerIntervalMillis = 1000L;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean waitForSendResult;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;

    public RocketMQEndpoint() {
    }

    public RocketMQEndpoint(String endpointUri, RocketMQComponent component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() {
        return new RocketMQProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RocketMQConsumer consumer = new RocketMQConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Exchange createRocketExchange(byte[] body) {
        Exchange exchange = super.createExchange();
        DefaultMessage message = new DefaultMessage(exchange.getContext());
        message.setBody(body);
        exchange.setIn(message);
        return exchange;
    }

    public String getTopicName() {
        return topicName;
    }

    /**
     * Topic name of this endpoint.
     */
    public void setTopicName(String topicName) {
        this.topicName = topicName;
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
