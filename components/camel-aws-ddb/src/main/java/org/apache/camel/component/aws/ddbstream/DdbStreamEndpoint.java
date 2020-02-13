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
package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder;
import com.amazonaws.services.dynamodbv2.model.Record;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The aws-ddbstream component is used for working with Amazon DynamoDB Streams.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "aws-ddbstream", title = "AWS DynamoDB Streams",
        consumerOnly = true, syntax = "aws-ddbstream:tableName",
        label = "cloud,messaging,streams")
public class DdbStreamEndpoint extends ScheduledPollEndpoint {

    @UriParam
    DdbStreamConfiguration configuration;

    private AmazonDynamoDBStreams ddbStreamClient;

    public DdbStreamEndpoint(String uri, DdbStreamConfiguration configuration, DdbStreamComponent component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DdbStreamConsumer consumer = new DdbStreamConsumer(this, processor);
        consumer.setSchedulerProperties(consumer.getEndpoint().getSchedulerProperties());
        configureConsumer(consumer);
        return consumer;
    }

    Exchange createExchange(Record record) {
        Exchange ex = super.createExchange();
        ex.getIn().setBody(record, Record.class);

        return ex;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        ddbStreamClient = configuration.getAmazonDynamoDbStreamsClient() != null ? configuration.getAmazonDynamoDbStreamsClient()
                : createDdbStreamClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonDynamoDbStreamsClient())) {
            if (ddbStreamClient != null) {
                ddbStreamClient.shutdown();
            }
        }
        super.doStop();
    }

    public DdbStreamConfiguration getConfiguration() {
        return configuration;
    }

    public AmazonDynamoDBStreams getClient() {
        return ddbStreamClient;
    }

    public String getSequenceNumber() {
        switch (configuration.getIteratorType()) {
            case AFTER_SEQUENCE_NUMBER:
            case AT_SEQUENCE_NUMBER:
                if (null == configuration.getSequenceNumberProvider()) {
                    throw new IllegalStateException("sequenceNumberProvider must be"
                            + " provided, either as an implementation of"
                            + " SequenceNumberProvider or a literal String.");
                } else {
                    return configuration.getSequenceNumberProvider().getSequenceNumber();
                }
            default:
                return "";
        }
    }

    AmazonDynamoDBStreams createDdbStreamClient() {
        AmazonDynamoDBStreams client = null;
        ClientConfiguration clientConfiguration = null;
        AmazonDynamoDBStreamsClientBuilder clientBuilder = null;
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
                clientBuilder = AmazonDynamoDBStreamsClientBuilder.standard().withClientConfiguration(clientConfiguration).withCredentials(credentialsProvider);
            } else {
                clientBuilder = AmazonDynamoDBStreamsClientBuilder.standard().withCredentials(credentialsProvider);
            }
        } else {
            if (isClientConfigFound) {
                clientBuilder = AmazonDynamoDBStreamsClientBuilder.standard();
            } else {
                clientBuilder = AmazonDynamoDBStreamsClientBuilder.standard().withClientConfiguration(clientConfiguration);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.withRegion(Regions.valueOf(configuration.getRegion()));
        }
        client = clientBuilder.build();
        return client;
    }

    @Override
    public String toString() {
        return "DdbStreamEndpoint{"
                + "tableName=" + configuration.getTableName()
                + ", amazonDynamoDbStreamsClient=[redacted], maxResultsPerRequest=" + configuration.getMaxResultsPerRequest()
                + ", iteratorType=" + configuration.getIteratorType()
                + ", sequenceNumberProvider=" + configuration.getSequenceNumberProvider()
                + ", uri=" + getEndpointUri()
                + '}';
    }
}
