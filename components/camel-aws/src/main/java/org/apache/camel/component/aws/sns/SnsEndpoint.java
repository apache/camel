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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SetTopicAttributesRequest;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/aws.html">AWS SNS Endpoint</a>.  
 */
@UriEndpoint(scheme = "aws-sns", title = "AWS Simple Notification System", syntax = "aws-sns:topicName", producerOnly = true, label = "cloud,mobile,messaging")
public class SnsEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(SnsEndpoint.class);

    private AmazonSNS snsClient;

    @UriParam
    private SnsConfiguration configuration;

    @Deprecated
    public SnsEndpoint(String uri, CamelContext context, SnsConfiguration configuration) {
        super(uri, context);
        this.configuration = configuration;
    }
    public SnsEndpoint(String uri, Component component, SnsConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
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
        
        // Override the setting Endpoint from url
        if (ObjectHelper.isNotEmpty(configuration.getAmazonSNSEndpoint())) {
            LOG.trace("Updating the SNS region with : {} " + configuration.getAmazonSNSEndpoint());
            snsClient.setEndpoint(configuration.getAmazonSNSEndpoint());
        }
        
        // creates a new topic, or returns the URL of an existing one
        CreateTopicRequest request = new CreateTopicRequest(configuration.getTopicName());
        
        LOG.trace("Creating topic [{}] with request [{}]...", configuration.getTopicName(), request);
        
        CreateTopicResult result = snsClient.createTopic(request);
        configuration.setTopicArn(result.getTopicArn());
        
        LOG.trace("Topic created with Amazon resource name: {}", configuration.getTopicArn());
        
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
        AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProxyHost(configuration.getProxyHost());
            clientConfiguration.setProxyPort(configuration.getProxyPort());
            client = new AmazonSNSClient(credentials, clientConfiguration);
        } else {
            client = new AmazonSNSClient(credentials);
        }
        return client;
    }
}