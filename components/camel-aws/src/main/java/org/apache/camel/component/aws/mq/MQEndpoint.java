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
package org.apache.camel.component.aws.mq;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.mq.AmazonMQ;
import com.amazonaws.services.mq.AmazonMQClient;
import com.amazonaws.services.mq.AmazonMQClientBuilder;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

/**
 * The aws-mq is used for managing Amazon MQ instances.
 */
@UriEndpoint(firstVersion = "2.21.0", scheme = "aws-mq", title = "AWS MQ", syntax = "aws-mq:label", producerOnly = true, label = "cloud,management")
public class MQEndpoint extends ScheduledPollEndpoint {

    private AmazonMQ mqClient;

    @UriParam
    private MQConfiguration configuration;

    public MQEndpoint(String uri, Component component, MQConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public Producer createProducer() throws Exception {
        return new MQProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        mqClient = configuration.getAmazonMqClient() != null ? configuration.getAmazonMqClient() : (AmazonMQClient)createMQClient();
    }

    public MQConfiguration getConfiguration() {
        return configuration;
    }

    public AmazonMQ getAmazonMqClient() {
        return mqClient;
    }

    AmazonMQ createMQClient() {
        AmazonMQ client = null;
        ClientConfiguration clientConfiguration = null;
        AmazonMQClientBuilder clientBuilder = null;
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
                clientBuilder = AmazonMQClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
            } else {
                clientBuilder = AmazonMQClientBuilder.standard().withCredentials(credentialsProvider);
            }
        } else {
            if (isClientConfigFound) {
                clientBuilder = AmazonMQClientBuilder.standard();
            } else {
                clientBuilder = AmazonMQClientBuilder.standard().withClientConfiguration(clientConfiguration);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.withRegion(configuration.getRegion());
        }
        client = clientBuilder.build();
        return client;
    }
}
