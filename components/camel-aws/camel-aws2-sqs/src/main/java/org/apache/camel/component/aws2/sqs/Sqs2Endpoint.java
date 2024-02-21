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
package org.apache.camel.component.aws2.sqs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.sqs.client.Sqs2ClientFactory;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * Send and receive messages to/from AWS SQS.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-sqs", title = "AWS Simple Queue Service (SQS)",
             syntax = "aws2-sqs:queueNameOrArn", category = { Category.CLOUD, Category.MESSAGING },
             headersClass = Sqs2Constants.class)
public class Sqs2Endpoint extends ScheduledPollEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(Sqs2Endpoint.class);

    private SqsClient client;
    private String queueUrl;
    private boolean queueUrlInitialized;

    @UriPath(description = "Queue name or ARN")
    @Metadata(required = true)
    private String queueNameOrArn; // to support component docs
    @UriParam
    private Sqs2Configuration configuration;
    @UriParam(label = "consumer")
    private int maxMessagesPerPoll;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy;

    public Sqs2Endpoint(String uri, Sqs2Component component, Sqs2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to map headers to/from Camel.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Sqs2Producer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Sqs2Consumer consumer = new Sqs2Consumer(this, processor);
        configureConsumer(consumer);
        consumer.setMaxMessagesPerPoll(maxMessagesPerPoll);
        return consumer;
    }

    @Override
    public Sqs2Component getComponent() {
        return (Sqs2Component) super.getComponent();
    }

    private boolean isDefaultAwsHost() {
        return configuration.getAmazonAWSHost().equals("amazonaws.com");
    }

    /*
     * If using a different AWS host, do not assume specific parts of the AWS
     * host and, instead, just return whatever is provided as the host.
     */
    private String getFullyQualifiedAWSHost() {
        String host = configuration.getAmazonAWSHost();
        host = FileUtil.stripTrailingSeparator(host);

        if (isDefaultAwsHost()) {
            return "sqs." + Region.of(configuration.getRegion()).id() + "." + host;
        }

        return host;
    }

    /*
     * Gets the base endpoint for AWS (ie.: http(s)://host:port.
     *
     * Do not confuse with other Camel endpoint methods: this one is named after AWS'
     * own endpoint terminology and can also be used for the endpoint override in the
     * client builder.
     */
    private String getAwsEndpointUri() {
        return configuration.getProtocol() + "://" + getFullyQualifiedAWSHost();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        client = configuration.getAmazonSQSClient() != null
                ? configuration.getAmazonSQSClient() : Sqs2ClientFactory.getSqsClient(configuration).getSQSClient();

        // check the setting the headerFilterStrategy
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new Sqs2HeaderFilterStrategy();
        }

        if (configuration.getQueueUrl() != null) {
            queueUrl = configuration.getQueueUrl();
            queueUrlInitialized = true;
        } else {
            // If both region and Account ID is provided the queue URL can be
            // built manually.
            // This allows accessing queues where you don't have permission to
            // list queues or query queues
            if (configuration.getRegion() != null && configuration.getQueueOwnerAWSAccountId() != null) {
                queueUrl = getAwsEndpointUri() + "/" + configuration.getQueueOwnerAWSAccountId() + "/"
                           + configuration.getQueueName();
                queueUrlInitialized = true;
            } else if (configuration.getQueueOwnerAWSAccountId() != null) {
                GetQueueUrlRequest.Builder getQueueUrlRequest = GetQueueUrlRequest.builder();
                getQueueUrlRequest.queueName(configuration.getQueueName());
                getQueueUrlRequest.queueOwnerAWSAccountId(configuration.getQueueOwnerAWSAccountId());
                GetQueueUrlResponse getQueueUrlResult = client.getQueueUrl(getQueueUrlRequest.build());
                queueUrl = getQueueUrlResult.queueUrl();
                queueUrlInitialized = true;
            } else {
                initQueueUrl();
            }
        }

        if (queueUrl == null && configuration.isAutoCreateQueue()) {
            createQueue(client);
        } else {
            LOG.debug("Using Amazon SQS queue url: {}", queueUrl);
            updateQueueAttributes(client);
        }
    }

    private void initQueueUrl() {
        // check whether the queue already exists
        String queueNamePath = "/" + configuration.getQueueName();
        ListQueuesRequest.Builder listQueuesRequestBuilder
                = ListQueuesRequest.builder().maxResults(1000).queueNamePrefix(configuration.getQueueName());

        for (;;) {
            ListQueuesResponse listQueuesResult = client.listQueues(listQueuesRequestBuilder.build());
            for (String url : listQueuesResult.queueUrls()) {
                if (url.endsWith(queueNamePath)) {
                    queueUrl = url;
                    LOG.trace("Queue available at '{}'.", queueUrl);
                    break;
                }
            }

            if (queueUrl != null) {
                queueUrlInitialized = true;
                break;
            }

            String token = listQueuesResult.nextToken();
            if (token == null) {
                break;
            }

            listQueuesRequestBuilder = listQueuesRequestBuilder.nextToken(token);
        }
    }

    private boolean queueExists(SqsClient client) {
        LOG.trace("Checking if queue '{}' exists", configuration.getQueueName());

        GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                .queueName(configuration.getQueueName())
                .build();
        try {
            queueUrl = client.getQueueUrl(getQueueUrlRequest).queueUrl();
            LOG.trace("Queue '{}' exists and its URL is '{}'", configuration.getQueueName(),
                    queueUrl);

            return true;

        } catch (QueueDoesNotExistException e) {
            LOG.trace("Queue '{}' does not exist", configuration.getQueueName());

            return false;
        }
    }

    protected void createQueue(SqsClient client) throws IOException {
        if (queueExists(client)) {
            return;
        }

        LOG.trace("Creating the a queue named '{}'", configuration.getQueueName());

        // creates a new queue, or returns the URL of an existing one
        CreateQueueRequest.Builder request = CreateQueueRequest.builder().queueName(configuration.getQueueName());
        Map<QueueAttributeName, String> attributes = new EnumMap<>(QueueAttributeName.class);
        if (getConfiguration().isFifoQueue()) {
            attributes.put(QueueAttributeName.FIFO_QUEUE, String.valueOf(true));
            boolean useContentBasedDeduplication
                    = getConfiguration().getMessageDeduplicationIdStrategy() instanceof NullMessageDeduplicationIdStrategy;
            attributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, String.valueOf(useContentBasedDeduplication));
        }
        if (getConfiguration().getDefaultVisibilityTimeout() != null) {
            attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT,
                    String.valueOf(getConfiguration().getDefaultVisibilityTimeout()));
        }
        if (getConfiguration().getMaximumMessageSize() != null) {
            attributes.put(QueueAttributeName.MAXIMUM_MESSAGE_SIZE, String.valueOf(getConfiguration().getMaximumMessageSize()));
        }
        if (getConfiguration().getMessageRetentionPeriod() != null) {
            attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD,
                    String.valueOf(getConfiguration().getMessageRetentionPeriod()));
        }
        if (getConfiguration().getPolicy() != null) {
            InputStream s = ResourceHelper.resolveMandatoryResourceAsInputStream(this.getCamelContext(),
                    getConfiguration().getPolicy());
            String policy = IOUtils.toString(s, Charset.defaultCharset());
            attributes.put(QueueAttributeName.POLICY, policy);
        }
        if (getConfiguration().getReceiveMessageWaitTimeSeconds() != null) {
            attributes.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS,
                    String.valueOf(getConfiguration().getReceiveMessageWaitTimeSeconds()));
        }
        if (getConfiguration().getDelaySeconds() != null && getConfiguration().isDelayQueue()) {
            attributes.put(QueueAttributeName.DELAY_SECONDS, String.valueOf(getConfiguration().getDelaySeconds()));
        }
        if (getConfiguration().getRedrivePolicy() != null) {
            attributes.put(QueueAttributeName.REDRIVE_POLICY, getConfiguration().getRedrivePolicy());
        }
        if (getConfiguration().isServerSideEncryptionEnabled()) {
            if (getConfiguration().getKmsMasterKeyId() != null) {
                attributes.put(QueueAttributeName.KMS_MASTER_KEY_ID, getConfiguration().getKmsMasterKeyId());
            }
            if (getConfiguration().getKmsDataKeyReusePeriodSeconds() != null) {
                attributes.put(QueueAttributeName.KMS_DATA_KEY_REUSE_PERIOD_SECONDS,
                        String.valueOf(getConfiguration().getKmsDataKeyReusePeriodSeconds()));
            }
        }
        LOG.trace("Trying to create queue [{}] with request [{}]...", configuration.getQueueName(), request);
        request.attributes(attributes);

        try {
            CreateQueueResponse queueResult = client.createQueue(request.build());
            queueUrl = queueResult.queueUrl();
        } catch (SqsException e) {
            if (queueExists(client)) {
                LOG.warn("The queue may have been created since last check and could not be created");
                LOG.debug("AWS SDK error preventing queue creation: {}", e.getMessage(), e);
            } else {
                throw e;
            }
        }

        LOG.trace("Queue created and available at: {}", queueUrl);
    }

    private void updateQueueAttributes(SqsClient client) throws IOException {
        SetQueueAttributesRequest.Builder request = SetQueueAttributesRequest.builder().queueUrl(queueUrl);
        Map<QueueAttributeName, String> attributes = new EnumMap<>(QueueAttributeName.class);
        if (getConfiguration().getDefaultVisibilityTimeout() != null) {
            attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT,
                    String.valueOf(getConfiguration().getDefaultVisibilityTimeout()));
        }
        if (getConfiguration().getMaximumMessageSize() != null) {
            attributes.put(QueueAttributeName.MAXIMUM_MESSAGE_SIZE, String.valueOf(getConfiguration().getMaximumMessageSize()));
        }
        if (getConfiguration().getMessageRetentionPeriod() != null) {
            attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD,
                    String.valueOf(getConfiguration().getMessageRetentionPeriod()));
        }
        if (getConfiguration().getPolicy() != null) {
            InputStream s = ResourceHelper.resolveMandatoryResourceAsInputStream(this.getCamelContext(),
                    getConfiguration().getPolicy());
            String policy = IOUtils.toString(s, Charset.defaultCharset());
            attributes.put(QueueAttributeName.POLICY, policy);
        }
        if (getConfiguration().getReceiveMessageWaitTimeSeconds() != null) {
            attributes.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS,
                    String.valueOf(getConfiguration().getReceiveMessageWaitTimeSeconds()));
        }
        if (getConfiguration().getDelaySeconds() != null && getConfiguration().isDelayQueue()) {
            attributes.put(QueueAttributeName.DELAY_SECONDS, String.valueOf(getConfiguration().getDelaySeconds()));
        }
        if (getConfiguration().getRedrivePolicy() != null) {
            attributes.put(QueueAttributeName.REDRIVE_POLICY, getConfiguration().getRedrivePolicy());
        }
        if (getConfiguration().isServerSideEncryptionEnabled()) {
            if (getConfiguration().getKmsMasterKeyId() != null) {
                attributes.put(QueueAttributeName.KMS_MASTER_KEY_ID, getConfiguration().getKmsMasterKeyId());
            }
            if (getConfiguration().getKmsDataKeyReusePeriodSeconds() != null) {
                attributes.put(QueueAttributeName.KMS_DATA_KEY_REUSE_PERIOD_SECONDS,
                        String.valueOf(getConfiguration().getKmsDataKeyReusePeriodSeconds()));
            }
        }
        if (!attributes.isEmpty()) {
            request.attributes(attributes);
            LOG.trace("Updating queue '{}' with the provided queue attributes...", configuration.getQueueName());
            client.setQueueAttributes(request.build());
            LOG.trace("Queue '{}' updated and available at {}'", configuration.getQueueName(), queueUrl);
        }
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonSQSClient())) {
            if (client != null) {
                client.close();
            }
        }
        super.doStop();
    }

    public Sqs2Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Sqs2Configuration configuration) {
        this.configuration = configuration;
    }

    public SqsClient getClient() {
        return client;
    }

    public void setClient(SqsClient client) {
        this.client = client;
    }

    /**
     * If queue does not exist during endpoint initialization, the queueUrl has to be initialized again. See
     * https://issues.apache.org/jira/browse/CAMEL-18968 for more details.
     */
    protected String getQueueUrl() {
        if (!queueUrlInitialized) {
            LOG.trace("Queue url was not initialized during the start of the component. Initializing again.");
            initQueueUrl();
        }
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

}
