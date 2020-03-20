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
package org.apache.camel.component.aws2.ddb;

import java.net.URI;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

/**
 * The aws2-ddb component is used for storing and retrieving data from Amazon's
 * DynamoDB service.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-ddb", title = "AWS 2 DynamoDB", syntax = "aws2-ddb:tableName", producerOnly = true, label = "cloud,database,nosql")
public class Ddb2Endpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(Ddb2Endpoint.class);

    @UriParam
    private Ddb2Configuration configuration;

    private DynamoDbClient ddbClient;

    public Ddb2Endpoint(String uri, Component component, Ddb2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Ddb2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        ddbClient = configuration.getAmazonDDBClient() != null ? configuration.getAmazonDDBClient() : createDdbClient();

        String tableName = getConfiguration().getTableName();
        LOG.trace("Querying whether table [{}] already exists...", tableName);

        try {
            DescribeTableRequest.Builder request = DescribeTableRequest.builder().tableName(tableName);
            TableDescription tableDescription = ddbClient.describeTable(request.build()).table();
            if (!isTableActive(tableDescription)) {
                waitForTableToBecomeAvailable(tableName);
            }

            LOG.trace("Table [{}] already exists", tableName);
            return;
        } catch (ResourceNotFoundException e) {
            LOG.trace("Table [{}] doesn't exist yet", tableName);
            LOG.trace("Creating table [{}]...", tableName);
            TableDescription tableDescription = createTable(tableName);
            if (!isTableActive(tableDescription)) {
                waitForTableToBecomeAvailable(tableName);
            }

            LOG.trace("Table [{}] created", tableName);
        }
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonDDBClient())) {
            if (ddbClient != null) {
                ddbClient.close();
            }
        }
        super.doStop();
    }

    private TableDescription createTable(String tableName) {
        CreateTableRequest.Builder createTableRequest = CreateTableRequest.builder().tableName(tableName)
            .keySchema(KeySchemaElement.builder().attributeName(configuration.getKeyAttributeName()).keyType(configuration.getKeyAttributeType()).build())
            .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(configuration.getReadCapacity()).writeCapacityUnits(configuration.getWriteCapacity()).build());
        return getDdbClient().createTable(createTableRequest.build()).tableDescription();
    }

    public Ddb2Configuration getConfiguration() {
        return configuration;
    }

    public DynamoDbClient getDdbClient() {
        return ddbClient;
    }

    DynamoDbClient createDdbClient() {
        DynamoDbClient client = null;
        DynamoDbClientBuilder clientBuilder = DynamoDbClient.builder();
        ProxyConfiguration.Builder proxyConfig = null;
        ApacheHttpClient.Builder httpClientBuilder = null;
        boolean isClientConfigFound = false;
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost()) && ObjectHelper.isNotEmpty(configuration.getProxyPort())) {
            proxyConfig = ProxyConfiguration.builder();
            URI proxyEndpoint = URI.create(configuration.getProxyProtocol() + "://" + configuration.getProxyHost() + ":" + configuration.getProxyPort());
            proxyConfig.endpoint(proxyEndpoint);
            httpClientBuilder = ApacheHttpClient.builder().proxyConfiguration(proxyConfig.build());
            isClientConfigFound = true;
        }
        if (configuration.getAccessKey() != null && configuration.getSecretKey() != null) {
            AwsBasicCredentials cred = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
            if (isClientConfigFound) {
                clientBuilder = clientBuilder.httpClientBuilder(httpClientBuilder).credentialsProvider(StaticCredentialsProvider.create(cred));
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
        client = clientBuilder.build();
        return client;
    }

    private void waitForTableToBecomeAvailable(String tableName) {
        LOG.trace("Waiting for [{}] to become ACTIVE...", tableName);

        long waitTime = 5 * 60 * 1000;
        while (waitTime > 0) {
            try {
                Thread.sleep(1000 * 5);
                waitTime -= 5000;
            } catch (Exception e) {
            }
            try {
                DescribeTableRequest request = DescribeTableRequest.builder().tableName(tableName).build();
                TableDescription tableDescription = getDdbClient().describeTable(request).table();
                if (isTableActive(tableDescription)) {
                    LOG.trace("Table [{}] became active", tableName);
                    return;
                }
                LOG.trace("Table [{}] not active yet", tableName);
            } catch (AwsServiceException ase) {
                if (!ase.getMessage().contains("ResourceNotFoundException")) {
                    throw ase;
                }
            }
        }

        throw new RuntimeException("Table " + tableName + " never went active");
    }

    private boolean isTableActive(TableDescription tableDescription) {
        return tableDescription.tableStatus().toString().equals(TableStatus.ACTIVE.toString());
    }
}
