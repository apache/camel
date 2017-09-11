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
package org.apache.camel.component.aws.sns;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.SetTopicAttributesRequest;
import com.amazonaws.services.sns.model.Topic;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The aws-sns component is used for sending messages to an Amazon Simple Notification Topic.
 */
@UriEndpoint(firstVersion = "2.8.0", scheme = "aws-sns", title = "AWS Simple Notification System", syntax = "aws-sns:topicNameOrArn",
    producerOnly = true, label = "cloud,mobile,messaging")
public class SnsEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(SnsEndpoint.class);

    private AmazonSNS snsClient;

    @UriPath(description = "Topic name or ARN")
    @Metadata(required = "true")
    private String topicNameOrArn; // to support component docs
    @UriParam
    private SnsConfiguration configuration;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy;

    @Deprecated
    public SnsEndpoint(String uri, CamelContext context, SnsConfiguration configuration) {
        super(uri, context);
        this.configuration = configuration;
    }
    public SnsEndpoint(String uri, Component component, SnsConfiguration configuration) {
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

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public Producer createProducer() throws Exception {
        return new SnsProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        snsClient = configuration.getAmazonSNSClient() != null
            ? configuration.getAmazonSNSClient() : createSNSClient();

        // check the setting the headerFilterStrategy
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new SnsHeaderFilterStrategy();
        }
        
        if (configuration.getTopicArn() == null) {
            try {
                String nextToken = null;
                final String arnSuffix = ":" + configuration.getTopicName();
                do {
                    final ListTopicsResult response = snsClient.listTopics(nextToken);
                    nextToken = response.getNextToken();

                    for (final Topic topic : response.getTopics()) {
                        if (topic.getTopicArn().endsWith(arnSuffix)) {
                            configuration.setTopicArn(topic.getTopicArn());
                            break;
                        }
                    }
                } while (nextToken != null);
            } catch (final AmazonServiceException ase) {
                LOG.trace("The list topics operation return the following error code {}", ase.getErrorCode());
                throw ase;
            }
        }

        if (configuration.getTopicArn() == null) {
            // creates a new topic, or returns the URL of an existing one
            CreateTopicRequest request = new CreateTopicRequest(configuration.getTopicName());

            LOG.trace("Creating topic [{}] with request [{}]...", configuration.getTopicName(), request);

            CreateTopicResult result = snsClient.createTopic(request);
            configuration.setTopicArn(result.getTopicArn());

            LOG.trace("Topic created with Amazon resource name: {}", configuration.getTopicArn());
        }
        
        if (ObjectHelper.isNotEmpty(configuration.getPolicy())) {
            LOG.trace("Updating topic [{}] with policy [{}]", configuration.getTopicArn(), configuration.getPolicy());
            
            snsClient.setTopicAttributes(new SetTopicAttributesRequest(configuration.getTopicArn(), "Policy", configuration.getPolicy()));
            
            LOG.trace("Topic policy updated");
        }
        
    }

    public SnsConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SnsConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public void setSNSClient(AmazonSNS snsClient) {
        this.snsClient = snsClient;
    }
    
    public AmazonSNS getSNSClient() {
        return snsClient;
    }

    /**
     * Provide the possibility to override this method for an mock implementation
     *
     * @return AmazonSNSClient
     */
    AmazonSNS createSNSClient() {
        AmazonSNS client = null;
        AmazonSNSClientBuilder clientBuilder = null;
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
                clientBuilder = AmazonSNSClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
            } else {
                clientBuilder = AmazonSNSClientBuilder.standard().withCredentials(credentialsProvider);
            }
        } else {
            if (isClientConfigFound) {
                clientBuilder = AmazonSNSClientBuilder.standard();
            } else {
                clientBuilder = AmazonSNSClientBuilder.standard().withClientConfiguration(clientConfiguration);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getAmazonSNSEndpoint()) && ObjectHelper.isNotEmpty(configuration.getRegion())) {
            EndpointConfiguration endpointConfiguration = new EndpointConfiguration(configuration.getAmazonSNSEndpoint(), configuration.getRegion());
            clientBuilder = clientBuilder.withEndpointConfiguration(endpointConfiguration);
        }
        client = clientBuilder.build();
        return client;
    }
}
