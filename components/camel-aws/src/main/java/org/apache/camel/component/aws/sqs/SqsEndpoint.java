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
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
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
    
    private AmazonSQSClient client;
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
        return sqsConsumer;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        client = getConfiguration().getAmazonSQSClient() != null
                ? getConfiguration().getAmazonSQSClient() : getClient();
        
        // creates a new queue, or returns the URL of an existing one
        CreateQueueRequest request = new CreateQueueRequest(configuration.getQueueName());
        
        LOG.trace("Creating queue [{}] with request [{}]...", configuration.getQueueName(), request);
        
        CreateQueueResult queueResult = client.createQueue(request);
        queueUrl = queueResult.getQueueUrl();
        
        LOG.trace("Queue created and available at: {}", queueUrl);

        // According to the documentation, only one setting can be made at a time, even though they go into a Map.
        if (getConfiguration().getDefaultVisibilityTimeout() != null) {
            updateAttribute("VisibilityTimeout", getConfiguration().getDefaultVisibilityTimeout());
        }
        if (getConfiguration().getMaximumMessageSize() != null) {
            updateAttribute("MaximumMessageSize", getConfiguration().getMaximumMessageSize());
        }
        if (getConfiguration().getMessageRetentionPeriod() != null) {
            updateAttribute("MessageRetentionPeriod", getConfiguration().getMessageRetentionPeriod());
        }
        if (getConfiguration().getPolicy() != null) {
            updateAttribute("Policy", getConfiguration().getPolicy());
        }
    }

    protected void updateAttribute(String attribute, Object value) {
        SetQueueAttributesRequest setQueueAttributesRequest = new SetQueueAttributesRequest();
        setQueueAttributesRequest.setQueueUrl(queueUrl);
        setQueueAttributesRequest.getAttributes().put(attribute, String.valueOf(value));
        
        LOG.trace("Updating queue [{}] with request: {}", configuration.getQueueName(), setQueueAttributesRequest);
        
        client.setQueueAttributes(setQueueAttributesRequest);
        
        LOG.trace("Queue updated");
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
    
    public AmazonSQSClient getClient() {
        if (client == null) {
            client = createClient();
        }
        return client;
    }
    
    public void setClient(AmazonSQSClient client) {
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
