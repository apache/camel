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
package org.apache.camel.component.aws.kinesis;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.Record;
import com.amazonaws.services.kinesis.model.ShardIteratorType;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The aws-kinesis component is for consuming and producing records from Amazon
 * Kinesis Streams.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "aws-kinesis", title = "AWS Kinesis", syntax = "aws-kinesis:streamName", label = "cloud,messaging")
public class KinesisEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private KinesisConfiguration configuration;
    
    private AmazonKinesis kinesisClient;
    
    public KinesisEndpoint(String uri, KinesisConfiguration configuration, KinesisComponent component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        kinesisClient = configuration.getAmazonKinesisClient() != null ? configuration.getAmazonKinesisClient()
            : createKinesisClient();
       
        
        if ((configuration.getIteratorType().equals(ShardIteratorType.AFTER_SEQUENCE_NUMBER) || configuration.getIteratorType().equals(ShardIteratorType.AT_SEQUENCE_NUMBER))
            && configuration.getSequenceNumber().isEmpty()) {
            throw new IllegalArgumentException("Sequence Number must be specified with iterator Types AFTER_SEQUENCE_NUMBER or AT_SEQUENCE_NUMBER");
        }
    }
    
    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonKinesisClient())) {
            if (kinesisClient != null) {
                kinesisClient.shutdown();
            }
        }
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KinesisProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final KinesisConsumer consumer = new KinesisConsumer(this, processor);
        consumer.setSchedulerProperties(getSchedulerProperties());
        configureConsumer(consumer);
        return consumer;
    }

    public Exchange createExchange(Record record) {
        Exchange exchange = super.createExchange();
        exchange.getIn().setBody(record);
        exchange.getIn().setHeader(KinesisConstants.APPROX_ARRIVAL_TIME, record.getApproximateArrivalTimestamp());
        exchange.getIn().setHeader(KinesisConstants.PARTITION_KEY, record.getPartitionKey());
        exchange.getIn().setHeader(KinesisConstants.SEQUENCE_NUMBER, record.getSequenceNumber());
        return exchange;
    }

    public AmazonKinesis getClient() {
        return kinesisClient;
    }

    public KinesisConfiguration getConfiguration() {
        return configuration;
    }
    
    AmazonKinesis createKinesisClient() {
        AmazonKinesis client = null;
        ClientConfiguration clientConfiguration = null;
        AmazonKinesisClientBuilder clientBuilder = null;
        boolean isClientConfigFound = false;
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProxyProtocol(configuration.getProxyProtocol());
            clientConfiguration.setProxyHost(configuration.getProxyHost());
            clientConfiguration.setProxyPort(configuration.getProxyPort());
            isClientConfigFound = true;
        }
        if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
            AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey());
            AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
            if (isClientConfigFound) {
                clientBuilder = AmazonKinesisClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
            } else {
                clientBuilder = AmazonKinesisClientBuilder.standard().withCredentials(credentialsProvider);
            }
        } else {
            if (isClientConfigFound) {
                clientBuilder = AmazonKinesisClientBuilder.standard();
            } else {
                clientBuilder = AmazonKinesisClientBuilder.standard().withClientConfiguration(clientConfiguration);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
        }
        client = clientBuilder.build();
        return client;
    }
}
