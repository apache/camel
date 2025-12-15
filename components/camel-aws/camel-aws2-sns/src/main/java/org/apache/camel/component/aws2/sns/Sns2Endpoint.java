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
package org.apache.camel.component.aws2.sns;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.sns.client.Sns2ClientFactory;
import org.apache.camel.spi.*;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sns.model.SetTopicAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sns.model.Topic;

/**
 * Send messages to AWS Simple Notification Topic.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-sns", title = "AWS Simple Notification System (SNS)",
             syntax = "aws2-sns:topicNameOrArn", producerOnly = true,
             category = { Category.CLOUD, Category.MESSAGING, Category.MOBILE }, headersClass = Sns2Constants.class)
public class Sns2Endpoint extends DefaultEndpoint implements HeaderFilterStrategyAware, EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(Sns2Endpoint.class);

    private SnsClient snsClient;

    @UriPath(description = "Topic name or ARN")
    @Metadata(required = true)
    private String topicNameOrArn; // to support component docs
    @UriParam
    private final Sns2Configuration configuration;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy;

    public Sns2Endpoint(String uri, Component component, Sns2Configuration configuration) {
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
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Sns2Producer(this);
    }

    @Override
    public Sns2Component getComponent() {
        return (Sns2Component) super.getComponent();
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();
        snsClient = configuration.getAmazonSNSClient() != null
                ? configuration.getAmazonSNSClient() : Sns2ClientFactory.getSnsClient(configuration);

        // check the setting the headerFilterStrategy
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new Sns2HeaderFilterStrategy();
        }

        if (configuration.getTopicArn() == null) {
            try {
                String nextToken = null;
                final String arnSuffix = ":" + configuration.getTopicName();
                do {
                    ListTopicsRequest request = ListTopicsRequest.builder().nextToken(nextToken).build();
                    final ListTopicsResponse response = snsClient.listTopics(request);
                    nextToken = response.nextToken();

                    for (final Topic topic : response.topics()) {
                        if (topic.topicArn().endsWith(arnSuffix)) {
                            configuration.setTopicArn(topic.topicArn());
                            break;
                        }
                    }
                } while (nextToken != null);
            } catch (final AwsServiceException ase) {
                LOG.trace("The list topics operation return the following error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
        }

        if (configuration.getTopicArn() == null && configuration.isAutoCreateTopic()) {
            // creates a new topic, or returns the URL of an existing one
            CreateTopicRequest.Builder builder = CreateTopicRequest.builder().name(configuration.getTopicName());

            Map<String, String> attributes = new HashMap<>();

            if (configuration.isServerSideEncryptionEnabled()) {
                if (ObjectHelper.isNotEmpty(configuration.getKmsMasterKeyId())) {
                    attributes.put("KmsMasterKeyId", configuration.getKmsMasterKeyId());
                    builder.attributes(attributes);
                }
            }

            if (configuration.isFifoTopic()) {
                attributes.put("FifoTopic", "true");
                builder.attributes(attributes);
            }

            LOG.trace("Creating topic [{}] with request [{}]...", configuration.getTopicName(), builder);

            CreateTopicResponse result = snsClient.createTopic(builder.build());
            configuration.setTopicArn(result.topicArn());

            LOG.trace("Topic created with Amazon resource name: {}", configuration.getTopicArn());
        }

        if (ObjectHelper.isNotEmpty(configuration.getPolicy())) {
            LOG.trace("Updating topic [{}] with policy [{}]", configuration.getTopicArn(), configuration.getPolicy());

            try (InputStream s = ResourceHelper.resolveMandatoryResourceAsInputStream(this.getCamelContext(),
                    getConfiguration().getPolicy())) {
                String policy = IOUtils.toString(s, Charset.defaultCharset());

                snsClient.setTopicAttributes(SetTopicAttributesRequest.builder().topicArn(configuration.getTopicArn())
                        .attributeName("Policy").attributeValue(policy)
                        .build());

                LOG.trace("Topic policy updated");
            }
        }

        if (configuration.isSubscribeSNStoSQS()) {
            if (ObjectHelper.isNotEmpty(ObjectHelper.isNotEmpty(configuration.getQueueArn()))) {
                SubscribeResponse resp = snsClient.subscribe(SubscribeRequest.builder().topicArn(configuration.getTopicArn())
                        .protocol("sqs").endpoint(configuration.getQueueArn())
                        .returnSubscriptionArn(true).build());
                LOG.trace("Subscription of SQS Queue to SNS Topic done with Amazon resource name: {}", resp.subscriptionArn());
            } else {
                throw new IllegalArgumentException(
                        "Using the SubscribeSNStoSQS option require both AmazonSQSClient and Queue URL options");
            }
        }

    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonSNSClient())) {
            if (snsClient != null) {
                snsClient.close();
            }
        }

        super.doStop();
    }

    public Sns2Configuration getConfiguration() {
        return configuration;
    }

    public void setSNSClient(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    public SnsClient getSNSClient() {
        return snsClient;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "sns";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        HashMap<String, String> metadata = new HashMap<>();
        if (configuration.getQueueArn() != null) {
            metadata.put("queueArn", configuration.getQueueArn());
        }
        if (configuration.getTopicArn() != null) {
            metadata.put("topicArn", configuration.getTopicArn());
        }
        return metadata;
    }
}
