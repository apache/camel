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
package org.apache.camel.component.alibaba.mns;

import com.aliyun.mns.client.MNSClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.component.alibaba.mns.constants.MNSOperations;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Send and receive messages using Alibaba Cloud Simple Message Queue (SMQ, formerly MNS).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-mns", title = "Alibaba Message Service (MNS)",
             syntax = "alibaba-mns:queueName", category = { Category.CLOUD, Category.MESSAGING },
             headersClass = MNSHeaders.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.TEXT, Carrier.BYTES },
                                   description = "The body of the MNS message: a String when it is UTF-8 text, else a byte"
                                                 + " array, such as for the bytes of a file a producer sent, with the message"
                                                 + " id, the receipt handle and the dequeue count in headers. Binary data that"
                                                 + " is valid UTF-8 arrives as a String, so getBody(byte[].class) is the"
                                                 + " reliable way to get the bytes of a message"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = @BodyType.When(option = "operation", value = "deleteMessage",
                                                            carriers = Carrier.ANY),
                                  description = "The body of the message to send or publish: text is sent as it is, and bytes,"
                                                + " a stream or a file as their bytes, so binary data is kept. A Map, a List"
                                                + " or a POJO is sent as its toString(), so marshal it first, and no body fails."
                                                + " deleteMessage does not use the body: it takes the receipt handle from the"
                                                + " CamelAlibabaMnsReceiptHandle header"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   description = "Unchanged: the message id and the MD5 of the body are set in headers (and"
                                                 + " also as exchange properties, as before), and the request id as an exchange"
                                                 + " property"))
public class MNSEndpoint extends ScheduledPollEndpoint {

    @UriPath(description = "The name of the queue, or of the topic for a topic endpoint such as alibaba-mns:topic:myTopic. The"
                           + " CamelAlibabaMnsQueueName exchange property replaces the name of the queue for sendMessage and"
                           + " deleteMessage.",
             displayName = "Queue Name")
    @Metadata(required = true)
    private String queueName;

    @UriParam(description = "The operation that the producer performs. If not set, the producer performs publishMessage for a"
                            + " topic endpoint and sendMessage otherwise. The CamelAlibabaMnsOperation header replaces this"
                            + " option, and an exchange property of the same name replaces both.",
              displayName = "Operation", enums = "sendMessage,deleteMessage,publishMessage")
    private String operation;

    @UriParam(description = "The region ID of Simple Message Queue, for example cn-hangzhou.", displayName = "Region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "The endpoint of Simple Message Queue for the Alibaba Cloud account, for example"
                            + " https://123456.mns.cn-hangzhou.aliyuncs.com. The value must start with http:// or https://.",
              displayName = "Account Endpoint")
    @Metadata(required = true)
    private String accountEndpoint;

    @UriParam(description = "The AccessKey ID of the AccessKey pair for Simple Message Queue. If not set, the accessKey of the"
                            + " serviceKeys option is used.",
              displayName = "Access Key", security = "secret",
              label = "security")
    private String accessKey;

    @UriParam(description = "The AccessKey secret of the AccessKey pair for Simple Message Queue. If not set, the secretKey of"
                            + " the serviceKeys option is used.",
              displayName = "Secret Key", security = "secret",
              label = "security")
    private String secretKey;

    @UriParam(description = "The AccessKey pair as an org.apache.camel.component.alibaba.common.models.ServiceKeys. Each of"
                            + " its accessKey and secretKey is used when the option of the same name is not set.",
              displayName = "Service Keys",
              security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "The name of the topic for publishMessage. If not set, a topic endpoint takes the name of the"
                            + " topic from its URI. The CamelAlibabaMnsTopicName header replaces this option, and an exchange"
                            + " property of the same name replaces both.",
              displayName = "Topic Name")
    private String topicName;

    @UriParam(description = "The long polling wait time in seconds when the consumer receives messages. If 0 or less, the"
                            + " PollingWaitSeconds of the queue applies. Simple Message Queue accepts a value of at most 30.",
              displayName = "Wait Seconds",
              defaultValue = "0")
    private int waitSeconds;

    @UriParam(description = "Whether to delete the message from the queue after it is processed successfully.",
              displayName = "Delete After Read",
              defaultValue = "true", label = "consumer")
    private boolean deleteAfterRead = true;

    @UriParam(description = "The maximum number of messages that the consumer receives in one poll. Simple Message Queue"
                            + " accepts a value of at most 16. The consumer treats a value of 0 or less as 1.",
              displayName = "Max Messages Per Poll",
              defaultValue = "1", label = "consumer")
    private int maxMessagesPerPoll = 1;

    @UriParam(description = "To use a custom com.aliyun.mns.client.MNSClient.", displayName = "MNS Client", label = "advanced")
    @Metadata(autowired = true)
    private volatile MNSClient mnsClient;

    private boolean autowiredMnsClient;

    private boolean topicEndpoint;

    public MNSEndpoint() {
    }

    public MNSEndpoint(String uri, MNSComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MNSProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (topicEndpoint) {
            throw new IllegalArgumentException("Topic endpoints do not support consumers");
        }
        MNSConsumer consumer = new MNSConsumer(this, processor);
        configureConsumer(consumer);
        consumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return consumer;
    }

    /**
     * Returns the client that the producer and the consumer send their requests with. If no client is set or autowired,
     * the first call creates it from the options of this endpoint, while it holds the lock of this endpoint: the calls
     * made at the same time wait for this creation, and the calls made once the client exists do not take the lock. A
     * failed creation keeps no client, so each call that waited for it, and each later call, tries again.
     */
    public MNSClient initClient() {
        MNSClient answer = mnsClient;
        if (answer == null) {
            lock.lock();
            try {
                answer = mnsClient;
                if (answer == null) {
                    answer = createClient();
                    mnsClient = answer;
                }
            } finally {
                lock.unlock();
            }
        }
        return answer;
    }

    MNSClient createClient() {
        return MNSUtils.createClient(this);
    }

    @Override
    protected void doStop() throws Exception {
        lock.lock();
        try {
            if (mnsClient != null && !autowiredMnsClient) {
                mnsClient.close();
                mnsClient = null;
            }
        } finally {
            lock.unlock();
        }
        super.doStop();
    }

    public boolean isTopicEndpoint() {
        return topicEndpoint;
    }

    public void setTopicEndpoint(boolean topicEndpoint) {
        this.topicEndpoint = topicEndpoint;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccountEndpoint() {
        return accountEndpoint;
    }

    public void setAccountEndpoint(String accountEndpoint) {
        this.accountEndpoint = accountEndpoint;
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

    public ServiceKeys getServiceKeys() {
        return serviceKeys;
    }

    public void setServiceKeys(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public int getWaitSeconds() {
        return waitSeconds;
    }

    public void setWaitSeconds(int waitSeconds) {
        this.waitSeconds = waitSeconds;
    }

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public MNSClient getMnsClient() {
        return mnsClient;
    }

    public void setMnsClient(MNSClient mnsClient) {
        lock.lock();
        try {
            this.mnsClient = mnsClient;
            this.autowiredMnsClient = mnsClient != null;
        } finally {
            lock.unlock();
        }
    }

    public String resolveOperation() {
        if (operation != null) {
            return operation;
        }
        if (topicEndpoint) {
            return MNSOperations.PUBLISH_MESSAGE;
        }
        return MNSOperations.SEND_MESSAGE;
    }

    public String resolveConsumerOperation() {
        if (operation != null) {
            return operation;
        }
        return MNSOperations.RECEIVE_MESSAGE;
    }

    public String resolveTopicName() {
        if (topicName != null) {
            return topicName;
        }
        if (topicEndpoint) {
            return queueName;
        }
        return null;
    }
}
