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
package org.apache.camel.component.aws.firehose;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehose;
import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehoseClientBuilder;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The aws-kinesis-firehose component is used for producing Amazon's Kinesis Firehose streams.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "aws-kinesis-firehose", title = "AWS Kinesis Firehose", syntax = "aws-kinesis-firehose:streamName",
    producerOnly = true, label = "cloud,messaging")
public class KinesisFirehoseEndpoint extends DefaultEndpoint {

    @UriParam
    private KinesisFirehoseConfiguration configuration;
    
    private AmazonKinesisFirehose kinesisFirehoseClient;

    public KinesisFirehoseEndpoint(String uri, KinesisFirehoseConfiguration configuration, KinesisFirehoseComponent component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KinesisFirehoseProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume messages from this endpoint");
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        kinesisFirehoseClient = configuration.getAmazonKinesisFirehoseClient() != null ? configuration.getAmazonKinesisFirehoseClient()
            : createKinesisFirehoseClient();
               
    }
    
    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonKinesisFirehoseClient())) {
            if (kinesisFirehoseClient != null) {
                kinesisFirehoseClient.shutdown();
            }
        }
        super.doStop();
    }
    
    AmazonKinesisFirehose createKinesisFirehoseClient() {
        AmazonKinesisFirehose client = null;
        ClientConfiguration clientConfiguration = null;
        AmazonKinesisFirehoseClientBuilder clientBuilder = null;
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
                clientBuilder = AmazonKinesisFirehoseClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
            } else {
                clientBuilder = AmazonKinesisFirehoseClientBuilder.standard().withCredentials(credentialsProvider);
            }
        } else {
            if (isClientConfigFound) {
                clientBuilder = AmazonKinesisFirehoseClientBuilder.standard();
            } else {
                clientBuilder = AmazonKinesisFirehoseClientBuilder.standard().withClientConfiguration(clientConfiguration);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
        }
        client = clientBuilder.build();
        return client;
    }

    public AmazonKinesisFirehose getClient() {
        return kinesisFirehoseClient;
    }

    public KinesisFirehoseConfiguration getConfiguration() {
        return configuration;
    }
}
