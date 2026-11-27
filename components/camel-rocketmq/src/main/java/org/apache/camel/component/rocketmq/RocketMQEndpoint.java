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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultMessage;

/**
 * Send messages to and receive messages from Apache RocketMQ topics.
 */
@UriEndpoint(firstVersion = "3.20.0", scheme = "rocketmq", syntax = "rocketmq:topicName", title = "RocketMQ",
             category = Category.MESSAGING, headersClass = RocketMQConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.BYTES, javaType = "byte[]",
                                   description = "The body of the RocketMQ message. The consumer does not reply: the body"
                                                 + " the route ends with is not sent back"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  description = "What converts to a byte array: text, bytes, a stream or a file, sent as"
                                                + " the body of the message. A Map, a POJO, a number or no body fails, and"
                                                + " the broker rejects an empty body: marshal it first"),
           producerOut = @BodyType(value = Carrier.UNCHANGED, dependsOn = "replyToTopic",
                                   description = "The body is left as it was. With the InOut pattern and a replyToTopic,"
                                                 + " the producer waits for the reply and the body is the reply as a byte"
                                                 + " array"))
public class RocketMQEndpoint extends DefaultEndpoint implements AsyncEndpoint, EndpointServiceLocation {

    @UriPath(description = "The name of the topic. The consumer subscribes to this topic, and the producer sends to it"
                           + " unless the CamelRocketMQOverrideTopicName header names another topic.")
    @Metadata(required = true)
    private String topicName;
    @UriParam(description = "The producer group of the producer. The producer fails to start if it is not set.",
              label = "producer")
    private String producerGroup;
    @UriParam(description = "The consumer group of the consumer. The consumers of a group share the messages of the topic,"
                            + " and each message goes to one of them. The consumer fails to start if it is not set.",
              label = "consumer")
    private String consumerGroup;
    @UriParam(description = "How the consumer selects the messages of the topic that it receives: tag uses the subscribeTags"
                            + " option, and sql uses the subscribeSql option.",
              label = "consumer", defaultValue = "tag", enums = "tag,sql")
    private String messageSelectorType = "tag";
    @UriParam(description = "The tags of the messages that the consumer receives, when messageSelectorType is tag. Separate"
                            + " several tags with ||, for example TagA||TagB. The default * receives all messages.",
              label = "consumer", defaultValue = "*")
    private String subscribeTags = "*";
    @UriParam(description = "The SQL92 expression on the message properties that selects the messages that the consumer"
                            + " receives, when messageSelectorType is sql. The broker must have enablePropertyFilter set to"
                            + " true. The default 1 = 1 receives all messages.",
              label = "consumer", defaultValue = "1 = 1")
    private String subscribeSql = "1 = 1";
    @UriParam(description = "The tag of each message that the producer sends. The CamelRocketMQOverrideTag header replaces"
                            + " it. If not set, the messages are sent with an empty tag.",
              label = "producer")
    private String sendTag = "";
    @UriParam(description = "The topic on which the producer receives the reply of an InOut exchange. The reply must carry"
                            + " the keys of the request, one of which the producer generates to match the reply. If not set, an"
                            + " InOut exchange does not wait for a reply.",
              label = "producer")
    private String replyToTopic;
    @UriParam(description = "The consumer group that receives the replies on replyToTopic, required with replyToTopic. Use a"
                            + " group per producer: in a shared group a reply can reach another producer, which ignores it, and"
                            + " the exchange times out.",
              label = "producer")
    private String replyToConsumerGroup;
    @UriParam(description = "The address of the RocketMQ name server, as host:port. Separate several name servers with a"
                            + " semicolon.",
              label = "common", defaultValue = "localhost:9876")
    private String namesrvAddr = "localhost:9876";
    @UriParam(description = "The namespace of the topics and groups, which the RocketMQ client sends with each request. Set"
                            + " it for a RocketMQ instance that uses namespaces, such as a serverless instance. The consumer of"
                            + " replyToTopic does not use it.",
              label = "common")
    private String namespace;
    @UriParam(description = "Whether the producer and the consumer record message traces, which the RocketMQ client sends to"
                            + " the trace topic RMQ_SYS_TRACE_TOPIC. The consumer of replyToTopic does not record traces.",
              label = "common")
    private boolean enableTrace;
    @UriParam(description = "The access channel of the RocketMQ client: LOCAL for an Apache RocketMQ cluster, or CLOUD for a"
                            + " RocketMQ service of a cloud provider. With CLOUD the message traces go to a trace topic per"
                            + " region, in the format of the cloud service.",
              label = "common", defaultValue = "LOCAL", enums = "LOCAL,CLOUD")
    private String accessChannel = "LOCAL";
    @UriParam(description = "How long, in milliseconds, the producer waits for the reply of an InOut exchange on replyToTopic"
                            + " before it fails the exchange with an ExchangeTimedOutException. A value of 0 or less waits for"
                            + " Integer.MAX_VALUE milliseconds (about 24 days).",
              label = "advanced", defaultValue = "10000")
    private long requestTimeoutMillis = 10000L;
    @UriParam(description = "How often, in milliseconds, the producer checks for InOut exchanges whose requestTimeoutMillis"
                            + " has passed. A timeout can be detected up to this interval late.",
              label = "advanced", defaultValue = "1000")
    private long requestTimeoutCheckerIntervalMillis = 1000L;
    @UriParam(description = "Whether the producer waits for the send result before the exchange continues. When true, a send"
                            + " that fails, or whose status is not SEND_OK, fails the exchange. When false, an InOnly exchange"
                            + " continues as soon as the message is handed to the RocketMQ client, and a failed send is only"
                            + " logged. An InOut exchange always waits for the send result, and for the reply when replyToTopic"
                            + " is set.",
              label = "producer", defaultValue = "true")
    private boolean waitForSendResult = true;
    @UriParam(description = "The access key of the RocketMQ ACL credentials. The producer and the consumer sign their"
                            + " requests only when both accessKey and secretKey are set. The consumer of replyToTopic does not"
                            + " use it.",
              label = "security", security = "secret")
    private String accessKey;
    @UriParam(description = "The secret key of the RocketMQ ACL credentials. The producer and the consumer sign their"
                            + " requests only when both accessKey and secretKey are set. The consumer of replyToTopic does not"
                            + " use it.",
              label = "security", security = "secret")
    private String secretKey;

    public RocketMQEndpoint() {
    }

    public RocketMQEndpoint(String endpointUri, RocketMQComponent component) {
        super(endpointUri, component);
    }

    @Override
    public String getServiceUrl() {
        return namesrvAddr;
    }

    @Override
    public String getServiceProtocol() {
        return "rocketmq";
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

    public void setTopicName(String topicName) {
        this.topicName = topicName;
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
