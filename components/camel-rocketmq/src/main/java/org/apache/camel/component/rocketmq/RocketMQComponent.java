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

    @Metadata(description = "The producer group of the producer. The producer fails to start if it is not set.",
              label = "producer")
    private String producerGroup;

    @Metadata(description = "The consumer group of the consumer. The consumers of a group share the messages of the topic,"
                            + " and each message goes to one of them. The consumer fails to start if it is not set.",
              label = "consumer")
    private String consumerGroup;

    @Metadata(description = "How the consumer selects the messages of the topic that it receives: tag uses the subscribeTags"
                            + " option, and sql uses the subscribeSql option.",
              label = "consumer", defaultValue = "tag")
    private String messageSelectorType = "tag";

    @Metadata(description = "The tags of the messages that the consumer receives, when messageSelectorType is tag. Separate"
                            + " several tags with ||, for example TagA||TagB. The default * receives all messages.",
              label = "consumer", defaultValue = "*")
    private String subscribeTags = "*";

    @Metadata(description = "The SQL92 expression on the message properties that selects the messages that the consumer"
                            + " receives, when messageSelectorType is sql. The broker must have enablePropertyFilter set to"
                            + " true. The default 1 = 1 receives all messages.",
              label = "consumer", defaultValue = "1 = 1")
    private String subscribeSql = "1 = 1";

    @Metadata(description = "The tag of each message that the producer sends. The CamelRocketMQOverrideTag header replaces"
                            + " it. If not set, the messages are sent with an empty tag.",
              label = "common")
    private String sendTag = "";

    @Metadata(description = "The address of the RocketMQ name server, as host:port. Separate several name servers with a"
                            + " semicolon.",
              label = "common", defaultValue = "localhost:9876")
    private String namesrvAddr = "localhost:9876";

    @Metadata(description = "The namespace of the topics and groups, which the RocketMQ client sends with each request. Set"
                            + " it for a RocketMQ instance that uses namespaces, such as a serverless instance. The consumer of"
                            + " replyToTopic does not use it.",
              label = "common")
    private String namespace;

    @Metadata(description = "Whether the producer and the consumer record message traces, which the RocketMQ client sends to"
                            + " the trace topic RMQ_SYS_TRACE_TOPIC. The consumer of replyToTopic does not record traces.",
              label = "common", defaultValue = "false")
    private boolean enableTrace = false;

    @Metadata(description = "The access channel of the RocketMQ client: LOCAL for an Apache RocketMQ cluster, or CLOUD for a"
                            + " RocketMQ service of a cloud provider. With CLOUD the message traces go to a trace topic per"
                            + " region, in the format of the cloud service.",
              label = "common", defaultValue = "LOCAL")
    private String accessChannel = "LOCAL";

    @Metadata(description = "The topic on which the producer receives the reply of an InOut exchange. The reply must carry"
                            + " the keys of the request, one of which the producer generates to match the reply. If not set, an"
                            + " InOut exchange does not wait for a reply.",
              label = "producer")
    private String replyToTopic;

    @Metadata(description = "The consumer group that receives the replies on replyToTopic, required with replyToTopic. Use a"
                            + " group per producer: in a shared group a reply can reach another producer, which ignores it, and"
                            + " the exchange times out.",
              label = "producer")
    private String replyToConsumerGroup;

    @Metadata(description = "How long, in milliseconds, the producer waits for the reply of an InOut exchange on replyToTopic"
                            + " before it fails the exchange with an ExchangeTimedOutException. A value of 0 or less waits for"
                            + " Integer.MAX_VALUE milliseconds (about 24 days).",
              label = "advanced", defaultValue = "10000")
    private long requestTimeoutMillis = 10000L;

    @Metadata(description = "How often, in milliseconds, the producer checks for InOut exchanges whose requestTimeoutMillis"
                            + " has passed. A timeout can be detected up to this interval late.",
              label = "advanced", defaultValue = "1000")
    private long requestTimeoutCheckerIntervalMillis = 1000L;

    @Metadata(description = "Whether the producer waits for the send result before the exchange continues. When true, a send"
                            + " that fails, or whose status is not SEND_OK, fails the exchange. When false, an InOnly exchange"
                            + " continues as soon as the message is handed to the RocketMQ client, and a failed send is only"
                            + " logged. An InOut exchange always waits for the send result, and for the reply when replyToTopic"
                            + " is set.",
              label = "producer", defaultValue = "true")
    private boolean waitForSendResult = true;

    @Metadata(description = "The access key of the RocketMQ ACL credentials. The producer and the consumer sign their"
                            + " requests only when both accessKey and secretKey are set. The consumer of replyToTopic does not"
                            + " use it.",
              label = "secret", security = "secret")
    private String accessKey;

    @Metadata(description = "The secret key of the RocketMQ ACL credentials. The producer and the consumer sign their"
                            + " requests only when both accessKey and secretKey are set. The consumer of replyToTopic does not"
                            + " use it.",
              label = "secret", security = "secret")
    private String secretKey;

    @Override
    protected RocketMQEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RocketMQEndpoint endpoint = new RocketMQEndpoint(uri, this);
        endpoint.setProducerGroup(getProducerGroup());
        endpoint.setConsumerGroup(getConsumerGroup());
        endpoint.setMessageSelectorType(getMessageSelectorType());
        endpoint.setSubscribeSql(getSubscribeSql());
        endpoint.setSubscribeTags(getSubscribeTags());
        endpoint.setNamesrvAddr(getNamesrvAddr());
        endpoint.setNamespace(getNamespace());
        endpoint.setEnableTrace(isEnableTrace());
        endpoint.setAccessChannel(getAccessChannel());
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

    public String getMessageSelectorType() {
        return messageSelectorType;
    }

    public void setMessageSelectorType(String messageSelectorType) {
        this.messageSelectorType = messageSelectorType;
    }

    public String getSubscribeSql() {
        return subscribeSql;
    }

    public void setSubscribeSql(String subscribeSql) {
        this.subscribeSql = subscribeSql;
    }

    public String getSubscribeTags() {
        return subscribeTags;
    }

    public void setSubscribeTags(String subscribeTags) {
        this.subscribeTags = subscribeTags;
    }

    public String getSendTag() {
        return sendTag;
    }

    public void setSendTag(String sendTag) {
        this.sendTag = sendTag;
    }

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isEnableTrace() {
        return enableTrace;
    }

    public void setEnableTrace(boolean enableTrace) {
        this.enableTrace = enableTrace;
    }

    public String getAccessChannel() {
        return accessChannel;
    }

    public void setAccessChannel(String accessChannel) {
        this.accessChannel = accessChannel;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getReplyToTopic() {
        return replyToTopic;
    }

    public void setReplyToTopic(String replyToTopic) {
        this.replyToTopic = replyToTopic;
    }

    public String getReplyToConsumerGroup() {
        return replyToConsumerGroup;
    }

    public void setReplyToConsumerGroup(String replyToConsumerGroup) {
        this.replyToConsumerGroup = replyToConsumerGroup;
    }

    public long getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(long requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public long getRequestTimeoutCheckerIntervalMillis() {
        return requestTimeoutCheckerIntervalMillis;
    }

    public void setRequestTimeoutCheckerIntervalMillis(long requestTimeoutCheckerIntervalMillis) {
        this.requestTimeoutCheckerIntervalMillis = requestTimeoutCheckerIntervalMillis;
    }

    public boolean isWaitForSendResult() {
        return waitForSendResult;
    }

    public void setWaitForSendResult(boolean waitForSendResult) {
        this.waitForSendResult = waitForSendResult;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
