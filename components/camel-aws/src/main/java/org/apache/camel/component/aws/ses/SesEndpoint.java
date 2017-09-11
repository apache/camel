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
package org.apache.camel.component.aws.ses;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

/**
 * The aws-ses component is used for sending emails with Amazon's SES service.
 */
@UriEndpoint(firstVersion = "2.9.0", scheme = "aws-ses", title = "AWS Simple Email Service", syntax = "aws-ses:from", producerOnly = true, label = "cloud,mail")
public class SesEndpoint extends DefaultEndpoint {

    private AmazonSimpleEmailService sesClient;

    @UriParam
    private SesConfiguration configuration;

    @Deprecated
    public SesEndpoint(String uri, CamelContext context, SesConfiguration configuration) {
        super(uri, context);
        this.configuration = configuration;
    }
    public SesEndpoint(String uri, Component component, SesConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }
    
    @Override
    public void doStart() throws Exception {
        super.doStart();
        sesClient = configuration.getAmazonSESClient() != null
            ? configuration.getAmazonSESClient()
            : createSESClient();
            
        if (ObjectHelper.isNotEmpty(configuration.getAmazonSESEndpoint())) {
            sesClient.setEndpoint(configuration.getAmazonSESEndpoint());
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public Producer createProducer() throws Exception {
        return new SesProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    public SesConfiguration getConfiguration() {
        return configuration;
    }

    public AmazonSimpleEmailService getSESClient() {
        return sesClient;
    }

    private AmazonSimpleEmailService createSESClient() {
        AmazonSimpleEmailService client = null;
        AmazonSimpleEmailServiceClientBuilder clientBuilder = null;
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
                clientBuilder = AmazonSimpleEmailServiceClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
            } else {
                clientBuilder = AmazonSimpleEmailServiceClientBuilder.standard().withCredentials(credentialsProvider);
            }
        } else {
            if (isClientConfigFound) {
                clientBuilder = AmazonSimpleEmailServiceClientBuilder.standard();
            } else {
                clientBuilder = AmazonSimpleEmailServiceClientBuilder.standard().withClientConfiguration(clientConfiguration);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getAmazonSESEndpoint()) && ObjectHelper.isNotEmpty(configuration.getRegion())) {
            EndpointConfiguration endpointConfiguration = new EndpointConfiguration(configuration.getAmazonSESEndpoint(), configuration.getRegion());
            clientBuilder = clientBuilder.withEndpointConfiguration(endpointConfiguration);
        }
        client = clientBuilder.build();
        return client;
    }
}
