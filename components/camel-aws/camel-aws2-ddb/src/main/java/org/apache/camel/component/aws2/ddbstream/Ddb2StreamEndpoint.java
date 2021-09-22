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
package org.apache.camel.component.aws2.ddbstream;

import java.net.URI;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.ddbstream.client.Ddb2StreamClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClientBuilder;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * Receive messages from AWS DynamoDB Stream service using AWS SDK version 2.x.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-ddbstream", title = "AWS DynamoDB Streams", consumerOnly = true,
             syntax = "aws2-ddbstream:tableName", category = { Category.CLOUD, Category.MESSAGING, Category.STREAMS })
public class Ddb2StreamEndpoint extends ScheduledPollEndpoint {

    @UriParam
    Ddb2StreamConfiguration configuration;

    private DynamoDbStreamsClient ddbStreamClient;

    public Ddb2StreamEndpoint(String uri, Ddb2StreamConfiguration configuration, Ddb2StreamComponent component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Ddb2StreamConsumer consumer = new Ddb2StreamConsumer(this, processor);
        consumer.setSchedulerProperties(consumer.getEndpoint().getSchedulerProperties());
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        ddbStreamClient = configuration.getAmazonDynamoDbStreamsClient() != null
                ? configuration.getAmazonDynamoDbStreamsClient()
                : Ddb2StreamClientFactory.getDynamoDBStreamClient(configuration).getDynamoDBStreamClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonDynamoDbStreamsClient())) {
            if (ddbStreamClient != null) {
                ddbStreamClient.close();
            }
        }
        super.doStop();
    }

    public Ddb2StreamConfiguration getConfiguration() {
        return configuration;
    }

    public DynamoDbStreamsClient getClient() {
        return ddbStreamClient;
    }

    DynamoDbStreamsClient createDdbStreamClient() {
        DynamoDbStreamsClient client = null;
        DynamoDbStreamsClientBuilder clientBuilder = DynamoDbStreamsClient.builder();
        ProxyConfiguration.Builder proxyConfig = null;
        ApacheHttpClient.Builder httpClientBuilder = null;
        boolean isClientConfigFound = false;
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            proxyConfig = ProxyConfiguration.builder();
            URI proxyEndpoint = URI.create(configuration.getProxyProtocol() + "://" + configuration.getProxyHost() + ":"
                                           + configuration.getProxyPort());
            proxyConfig.endpoint(proxyEndpoint);
            httpClientBuilder = ApacheHttpClient.builder().proxyConfiguration(proxyConfig.build());
            isClientConfigFound = true;
        }
        if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
            AwsBasicCredentials cred = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
            if (isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder)
                        .credentialsProvider(StaticCredentialsProvider.create(cred));
            } else {
                clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
            }
        } else {
            if (!isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            clientBuilder = clientBuilder.region(Region.of(configuration.getRegion()));
        }
        if (configuration.isOverrideEndpoint()) {
            clientBuilder.endpointOverride(URI.create(configuration.getUriEndpointOverride()));
        }
        if (configuration.isTrustAllCertificates()) {
            SdkHttpClient ahc = ApacheHttpClient.builder().buildWithDefaults(AttributeMap
                    .builder()
                    .put(
                            SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES,
                            Boolean.TRUE)
                    .build());
            clientBuilder.httpClient(ahc);
        }
        client = clientBuilder.build();
        return client;
    }

    @Override
    public String toString() {
        return "DdbStreamEndpoint{" + "tableName=" + configuration.getTableName()
               + ", amazonDynamoDbStreamsClient=[redacted], maxResultsPerRequest="
               + configuration.getMaxResultsPerRequest() + ", streamIteratorType=" + configuration.getStreamIteratorType()
               + ", uri=" + getEndpointUri() + '}';
    }
}
