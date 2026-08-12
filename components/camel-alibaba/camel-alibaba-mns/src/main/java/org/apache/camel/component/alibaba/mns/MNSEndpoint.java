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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Send and receive messages to/from Alibaba Cloud Message Service (MNS).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-mns", title = "Alibaba Message Service (MNS)",
             syntax = "alibaba-mns:queueName", category = { Category.CLOUD, Category.MESSAGING },
             headersClass = MNSHeaders.class)
public class MNSEndpoint extends ScheduledPollEndpoint {

    @UriPath(description = "Queue name, or topic name when using the topic URI syntax", displayName = "Queue Name")
    @Metadata(required = true)
    private String queueName;

    @UriParam(description = "Operation to perform", displayName = "Operation",
              enums = "sendMessage,receiveMessage,deleteMessage,publishMessage")
    private String operation;

    @UriParam(description = "Alibaba Cloud region", displayName = "Region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "MNS account endpoint, for example https://123456.mns.cn-hangzhou.aliyuncs.com",
              displayName = "Account Endpoint")
    @Metadata(required = true)
    private String accountEndpoint;

    @UriParam(description = "Access key for the cloud user", displayName = "Access Key",
              secret = true, security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "Secret Key",
              secret = true, security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Keys",
              secret = true, security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "Topic name for publishMessage operations", displayName = "Topic Name")
    private String topicName;

    @UriParam(description = "Long polling wait time in seconds when receiving messages", displayName = "Wait Seconds",
              defaultValue = "0")
    private int waitSeconds;

    @UriParam(description = "Delete message from the queue after it has been processed", displayName = "Delete After Read",
              defaultValue = "true", label = "consumer")
    private boolean deleteAfterRead = true;

    @UriParam(description = "Maximum number of messages to receive per poll", displayName = "Max Messages Per Poll",
              defaultValue = "1", label = "consumer")
    private int maxMessagesPerPoll = 1;

    @UriParam(description = "Autowire an existing MNSClient instance", displayName = "MNS Client", label = "advanced")
    @Metadata(autowired = true)
    private MNSClient mnsClient;

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

    public void initClient() {
        if (mnsClient != null) {
            return;
        }
        mnsClient = MNSUtils.createClient(this);
    }

    @Override
    protected void doStop() throws Exception {
        if (mnsClient != null && !autowiredMnsClient) {
            mnsClient.close();
            mnsClient = null;
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
        this.mnsClient = mnsClient;
        this.autowiredMnsClient = mnsClient != null;
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
