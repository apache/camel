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

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.CloudTopic;
import com.aliyun.mns.model.Base64TopicMessage;
import com.aliyun.mns.model.BaseMessage;
import com.aliyun.mns.model.Message;
import com.aliyun.mns.model.TopicMessage;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.component.alibaba.mns.constants.MNSOperations;
import org.apache.camel.component.alibaba.mns.constants.MNSProperties;
import org.apache.camel.component.alibaba.mns.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MNSProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MNSProducer.class);

    public MNSProducer(MNSEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        MNSEndpoint endpoint = getEndpoint();
        endpoint.initClient();

        ClientConfigurations configuration = MNSUtils.createClientConfigurations(endpoint, exchange);
        String operation = configuration.operation();

        switch (operation) {
            case MNSOperations.SEND_MESSAGE -> sendMessage(endpoint, exchange, configuration);
            case MNSOperations.DELETE_MESSAGE -> deleteMessage(endpoint, exchange, configuration);
            case MNSOperations.PUBLISH_MESSAGE -> publishMessage(endpoint, exchange, configuration);
            case MNSOperations.RECEIVE_MESSAGE -> throw new UnsupportedOperationException(
                    "receiveMessage is not a producer operation: the consumer receives the messages of the queue");
            default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
        }
    }

    private void sendMessage(MNSEndpoint endpoint, Exchange exchange, ClientConfigurations configuration) throws Exception {
        CloudQueue queue = endpoint.getMnsClient().getQueueRef(configuration.queueName());
        Message message = new Message();
        MNSUtils.setMessageBody(exchange, message);

        Integer delaySeconds = exchange.getIn().getHeader(MNSHeaders.DELAY_SECONDS, Integer.class);
        if (delaySeconds != null) {
            message.setDelaySeconds(delaySeconds);
        }

        Integer priority = exchange.getIn().getHeader(MNSHeaders.PRIORITY, Integer.class);
        if (priority != null) {
            message.setPriority(priority);
        }

        Message response = queue.putMessage(message);
        setMessageResponse(exchange, response);
    }

    private void deleteMessage(MNSEndpoint endpoint, Exchange exchange, ClientConfigurations configuration) throws Exception {
        String receiptHandle = MNSUtils.resolveReceiptHandle(exchange);
        if (ObjectHelper.isEmpty(receiptHandle)) {
            throw new IllegalArgumentException("Receipt handle is required for deleteMessage operation");
        }

        CloudQueue queue = endpoint.getMnsClient().getQueueRef(configuration.queueName());
        queue.deleteMessage(receiptHandle);
    }

    private void publishMessage(MNSEndpoint endpoint, Exchange exchange, ClientConfigurations configuration) throws Exception {
        String topic = configuration.topicName();
        if (ObjectHelper.isEmpty(topic)) {
            throw new IllegalArgumentException("Topic name is required for publishMessage operation");
        }

        CloudTopic cloudTopic = endpoint.getMnsClient().getTopicRef(topic);
        TopicMessage topicMessage = new Base64TopicMessage();
        MNSUtils.setMessageBody(exchange, topicMessage);

        String messageTag = exchange.getIn().getHeader(MNSHeaders.MESSAGE_TAG, String.class);
        if (messageTag != null) {
            topicMessage.setMessageTag(messageTag);
        }

        TopicMessage response = cloudTopic.publishMessage(topicMessage);
        setMessageResponse(exchange, response);
    }

    private void setMessageResponse(Exchange exchange, BaseMessage response) {
        if (response == null) {
            return;
        }
        // the message id and the MD5 are headers, as the consumer sets them, and also exchange properties, which is
        // where the producer of earlier versions set them
        if (ObjectHelper.isNotEmpty(response.getMessageId())) {
            exchange.getMessage().setHeader(MNSHeaders.MESSAGE_ID, response.getMessageId());
            exchange.setProperty(MNSHeaders.MESSAGE_ID, response.getMessageId());
        }
        if (ObjectHelper.isNotEmpty(response.getRequestId())) {
            exchange.setProperty(MNSProperties.REQUEST_ID, response.getRequestId());
        }
        if (ObjectHelper.isNotEmpty(response.getMessageBodyMD5())) {
            exchange.getMessage().setHeader(MNSHeaders.MESSAGE_BODY_MD5, response.getMessageBodyMD5());
            exchange.setProperty(MNSHeaders.MESSAGE_BODY_MD5, response.getMessageBodyMD5());
        }
    }

    @Override
    public MNSEndpoint getEndpoint() {
        return (MNSEndpoint) super.getEndpoint();
    }
}
