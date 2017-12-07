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
import java.util.Map.Entry;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultScheduledPollConsumerScheduler;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The aws-sqs component is used for sending and receiving messages to Amazon's SQS service.
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "aws-sqs", title = "AWS Simple Queue Service", syntax = "aws-sqs:queueNameOrArn",
    consumerClass = SqsConsumer.class, label = "cloud,messaging")
public class SqsEndpoint extends ScheduledPollEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(SqsEndpoint.class);

    private AmazonSQS client;
    private String queueUrl;

    @UriPath(description = "Queue name or ARN")
    @Metadata(required = "true")
    private String queueNameOrArn; // to support component docs
    @UriParam
    private SqsConfiguration configuration;
    @UriParam(label = "consumer")
    private int maxMessagesPerPoll;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy;

    public SqsEndpoint(String uri, SqsComponent component, SqsConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to map headers to/from Camel.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    public Producer createProducer() throws Exception {
        return new SqsProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        SqsConsumer sqsConsumer = new SqsConsumer(this, processor);
        configureConsumer(sqsConsumer);
        sqsConsumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        DefaultScheduledPollConsumerScheduler scheduler = new DefaultScheduledPollConsumerScheduler();
        scheduler.setConcurrentTasks(configuration.getConcurrentConsumers());
        sqsConsumer.setScheduler(scheduler);
        return sqsConsumer;
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        client = getConfiguration().getAmazonSQSClient() != null
            ? getConfiguration().getAmazonSQSClient() : getClient();

        // Override the endpoint location
        if (ObjectHelper.isNotEmpty(getConfiguration().getAmazonSQSEndpoint())) {
            client.setEndpoint(getConfiguration().getAmazonSQSEndpoint());
        }

        // check the setting the headerFilterStrategy
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new SqsHeaderFilterStrategy();
        }

        // If both region and Account ID is provided the queue URL can be built manually.
        // This allows accessing queues where you don't have permission to list queues or query queues
        if (configuration.getRegion() != null && configuration.getQueueOwnerAWSAccountId() != null) {
            String host = configuration.getAmazonAWSHost();
            host = FileUtil.stripTrailingSeparator(host);
            queueUrl = "https://sqs." + configuration.getRegion() + "." + host + "/"
                +  configuration.getQueueOwnerAWSAccountId() + "/" + configuration.getQueueName();
        } else if (configuration.getQueueOwnerAWSAccountId() != null) {
            GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest();
            getQueueUrlRequest.setQueueName(configuration.getQueueName());
            getQueueUrlRequest.setQueueOwnerAWSAccountId(configuration.getQueueOwnerAWSAccountId());
            GetQueueUrlResult getQueueUrlResult = client.getQueueUrl(getQueueUrlRequest);
            queueUrl = getQueueUrlResult.getQueueUrl();
        } else {
            // check whether the queue already exists
            ListQueuesResult listQueuesResult = client.listQueues();
            for (String url : listQueuesResult.getQueueUrls()) {
                if (url.endsWith("/" + configuration.getQueueName())) {
                    queueUrl = url;
                    LOG.trace("Queue available at '{}'.", queueUrl);
                    break;
                }
            }
        }

        if (queueUrl == null) {
            createQueue(client);
        } else {
            LOG.debug("Using Amazon SQS queue url: {}", queueUrl);
            updateQueueAttributes(client);
        }
    }

    protected void createQueue(AmazonSQS client) {
        LOG.trace("Queue '{}' doesn't exist. Will create it...", configuration.getQueueName());

        // creates a new queue, or returns the URL of an existing one
        CreateQueueRequest request = new CreateQueueRequest(configuration.getQueueName());
        if (getConfiguration().isFifoQueue()) {
            request.getAttributes().put(QueueAttributeName.FifoQueue.name(), String.valueOf(true));
            boolean useContentBasedDeduplication = getConfiguration().getMessageDeduplicationIdStrategy() instanceof NullMessageDeduplicationIdStrategy;
            request.getAttributes().put(QueueAttributeName.ContentBasedDeduplication.name(), String.valueOf(useContentBasedDeduplication));
        }
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
        if (getConfiguration().getRedrivePolicy() != null) {
            request.getAttributes().put(QueueAttributeName.RedrivePolicy.name(), getConfiguration().getRedrivePolicy());
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
        if (getConfiguration().getRedrivePolicy() != null) {
            request.getAttributes().put(QueueAttributeName.RedrivePolicy.name(), getConfiguration().getRedrivePolicy());
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
        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();
        message.setBody(msg.getBody());
        message.setHeaders(new HashMap<String, Object>(msg.getAttributes()));
        message.setHeader(SqsConstants.MESSAGE_ID, msg.getMessageId());
        message.setHeader(SqsConstants.MD5_OF_BODY, msg.getMD5OfBody());
        message.setHeader(SqsConstants.RECEIPT_HANDLE, msg.getReceiptHandle());
        message.setHeader(SqsConstants.ATTRIBUTES, msg.getAttributes());
        message.setHeader(SqsConstants.MESSAGE_ATTRIBUTES, msg.getMessageAttributes());

        //Need to apply the SqsHeaderFilterStrategy this time
        HeaderFilterStrategy headerFilterStrategy = getHeaderFilterStrategy();
        //add all sqs message attributes as camel message headers so that knowledge of
        //the Sqs class MessageAttributeValue will not leak to the client
        for (Entry<String, MessageAttributeValue> entry : msg.getMessageAttributes().entrySet()) {
            String header = entry.getKey();
            Object value = translateValue(entry.getValue());
            if (!headerFilterStrategy.applyFilterToExternalHeaders(header, value, exchange)) {
                message.setHeader(header, value);
            }
        }
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
    AmazonSQS createClient() {
        AmazonSQS client = null;
        AmazonSQSClientBuilder clientBuilder = null;
        ClientConfiguration clientConfiguration = null;
        boolean isClientConfigFound = false;
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProxyHost(configuration.getProxyHost());
            clientConfiguration.setProxyPort(configuration.getProxyPort());
            isClientConfigFound = true;
        }
        if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
            AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
            AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
            if (isClientConfigFound) {
                clientBuilder = AmazonSQSClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
            } else {
                clientBuilder = AmazonSQSClientBuilder.standard().withCredentials(credentialsProvider);
            }
        } else {
            if (isClientConfigFound) {
                clientBuilder = AmazonSQSClientBuilder.standard();
            } else {
                clientBuilder = AmazonSQSClientBuilder.standard().withClientConfiguration(clientConfiguration);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getAmazonSQSEndpoint()) && ObjectHelper.isNotEmpty(configuration.getRegion())) {
            EndpointConfiguration endpointConfiguration = new EndpointConfiguration(configuration.getAmazonSQSEndpoint(), configuration.getRegion());
            clientBuilder = clientBuilder.withEndpointConfiguration(endpointConfiguration);
        }
        client = clientBuilder.build();
        return client;
    }

    protected String getQueueUrl() {
        return queueUrl;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Gets the maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Is default unlimited, but use 0 or negative number to disable it as unlimited.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    private Object translateValue(MessageAttributeValue mav) {
        Object result = null;
        if (mav.getStringValue() != null) {
            result = mav.getStringValue();
        } else if (mav.getBinaryValue() != null) {
            result = mav.getBinaryValue();
        }
        return result;
    }
}
