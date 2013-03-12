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
package org.apache.camel.component.aws.sqs;

import java.util.HashMap;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Defines the <a href="http://camel.apache.org/aws.html">AWS SQS Endpoint</a>.  
 *
 */
public class SqsEndpoint extends ScheduledPollEndpoint {
    
    private static final transient Logger LOG = LoggerFactory.getLogger(SqsEndpoint.class);
    
    private AmazonSQS client;
    private String queueUrl;
    private SqsConfiguration configuration;
    private int maxMessagesPerPoll;

    public SqsEndpoint(String uri, SqsComponent component, SqsConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        return new SqsProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        SqsConsumer sqsConsumer = new SqsConsumer(this, processor);
        configureConsumer(sqsConsumer);
        sqsConsumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return sqsConsumer;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        client = getConfiguration().getAmazonSQSClient() != null
                ? getConfiguration().getAmazonSQSClient() : getClient();

        // check whether the queue already exists
        ListQueuesResult listQueuesResult = client.listQueues();
        for (String url : listQueuesResult.getQueueUrls()) {
            if (url.endsWith("/" + configuration.getQueueName())) {
                queueUrl = url;
                LOG.trace("Queue available at '{}'.", queueUrl);
                break;
            }
        }
        
        if (queueUrl == null) {
            createQueue(client);
        } else {
            updateQueueAttributes(client);
        }
    }

    private void createQueue(AmazonSQS client) {
        LOG.trace("Queue '{}' doesn't exist. Will create it...", configuration.getQueueName());

        // creates a new queue, or returns the URL of an existing one
        CreateQueueRequest request = new CreateQueueRequest(configuration.getQueueName());
        if (getConfiguration().getDefaultVisibilityTimeout() != null) {
            request.getAttributes().put(QueueAttributeName.VisibilityTimeout.name(), String.valueOf(getConfiguration().getDefaultVisibilityTimeout()));
        }
        if (getConfiguration().getMaximumMessageSize() != null) {
            request.getAttributes().put(QueueAttributeName.MaximumMessageSize.name(), String.valueOf(getConfiguration().getMaximumMessageSize()));
        }
        if (getConfiguration().getMessageRetentionPeriod() != null) {
            request.getAttributes().put(QueueAttributeName.MessageRetentionPeriod.name(), String.valueOf(getConfiguration().getMessageRetentionPeriod()));
        }
        if (getConfiguration().getPolicy() != null) {
            request.getAttributes().put(QueueAttributeName.Policy.name(), String.valueOf(getConfiguration().getPolicy()));
        }
        if (getConfiguration().getReceiveMessageWaitTimeSeconds() != null) {
            request.getAttributes().put(QueueAttributeName.ReceiveMessageWaitTimeSeconds.name(), String.valueOf(getConfiguration().getReceiveMessageWaitTimeSeconds()));
        }
        LOG.trace("Creating queue [{}] with request [{}]...", configuration.getQueueName(), request);
        
        CreateQueueResult queueResult = client.createQueue(request);
        queueUrl = queueResult.getQueueUrl();
        
        LOG.trace("Queue created and available at: {}", queueUrl);
    }

    private void updateQueueAttributes(AmazonSQS client) {
        SetQueueAttributesRequest request = new SetQueueAttributesRequest();
        request.setQueueUrl(queueUrl);
        if (getConfiguration().getDefaultVisibilityTimeout() != null) {
            request.getAttributes().put(QueueAttributeName.VisibilityTimeout.name(), String.valueOf(getConfiguration().getDefaultVisibilityTimeout()));
        }
        if (getConfiguration().getMaximumMessageSize() != null) {
            request.getAttributes().put(QueueAttributeName.MaximumMessageSize.name(), String.valueOf(getConfiguration().getMaximumMessageSize()));
        }
        if (getConfiguration().getMessageRetentionPeriod() != null) {
            request.getAttributes().put(QueueAttributeName.MessageRetentionPeriod.name(), String.valueOf(getConfiguration().getMessageRetentionPeriod()));
        }
        if (getConfiguration().getPolicy() != null) {
            request.getAttributes().put(QueueAttributeName.Policy.name(), String.valueOf(getConfiguration().getPolicy()));
        }
        if (getConfiguration().getReceiveMessageWaitTimeSeconds() != null) {
            request.getAttributes().put(QueueAttributeName.ReceiveMessageWaitTimeSeconds.name(), String.valueOf(getConfiguration().getReceiveMessageWaitTimeSeconds()));
        }
        if (!request.getAttributes().isEmpty()) {
            LOG.trace("Updating queue '{}' with the provided queue attributes...", configuration.getQueueName());
            client.setQueueAttributes(request);
            LOG.trace("Queue '{}' updated and available at {}'", configuration.getQueueName(), queueUrl);
        }
    }

    @Override
    protected void doStop() throws Exception {
        client = null;
    }

    public Exchange createExchange(com.amazonaws.services.sqs.model.Message msg) {
        return createExchange(getExchangePattern(), msg);
    }

    private Exchange createExchange(ExchangePattern pattern, com.amazonaws.services.sqs.model.Message msg) {
        Exchange exchange = new DefaultExchange(this, pattern);
        Message message = exchange.getIn();
        message.setBody(msg.getBody());
        message.setHeaders(new HashMap<String, Object>(msg.getAttributes()));
        message.setHeader(SqsConstants.MESSAGE_ID, msg.getMessageId());
        message.setHeader(SqsConstants.MD5_OF_BODY, msg.getMD5OfBody());
        message.setHeader(SqsConstants.RECEIPT_HANDLE, msg.getReceiptHandle());
        message.setHeader(SqsConstants.ATTRIBUTES, msg.getAttributes());
        
        return exchange;
    }

    public SqsConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SqsConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public AmazonSQS getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }
    
    public void setClient(AmazonSQS client) {
        this.client = client;
    }

    /**
     * Provide the possibility to override this method for an mock implementation
     * @return AmazonSQSClient
     */
    AmazonSQSClient createClient() {
        AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
        AmazonSQSClient client = new AmazonSQSClient(credentials);
        if (configuration.getAmazonSQSEndpoint() != null) {
            client.setEndpoint(configuration.getAmazonSQSEndpoint());
        }
        return client;
    }

    protected String getQueueUrl() {
        return queueUrl;
    }
    
    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }
}
