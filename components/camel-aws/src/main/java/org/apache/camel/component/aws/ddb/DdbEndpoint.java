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
package org.apache.camel.component.aws.ddb;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodb.AmazonDynamoDB;
import com.amazonaws.services.dynamodb.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodb.model.CreateTableRequest;
import com.amazonaws.services.dynamodb.model.DescribeTableRequest;
import com.amazonaws.services.dynamodb.model.KeySchema;
import com.amazonaws.services.dynamodb.model.KeySchemaElement;
import com.amazonaws.services.dynamodb.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodb.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodb.model.TableDescription;
import com.amazonaws.services.dynamodb.model.TableStatus;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://aws.amazon.com/dynamodb/">AWS DynamoDB endpoint</a>
 */
@UriEndpoint(scheme = "aws-ddb", title = "AWS DynamoDB", syntax = "aws-ddb:tableName", producerOnly = true, label = "cloud,database,nosql")
public class DdbEndpoint extends ScheduledPollEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(DdbEndpoint.class);

    @UriParam
    private DdbConfiguration configuration;

    private AmazonDynamoDB ddbClient;

    @Deprecated
    public DdbEndpoint(String uri, CamelContext context, DdbConfiguration configuration) {
        super(uri, context);
        this.configuration = configuration;
    }

    public DdbEndpoint(String uri, Component component, DdbConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public Producer createProducer() throws Exception {
        return new DdbProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        ddbClient = configuration.getAmazonDDBClient() != null ? configuration.getAmazonDDBClient()
            : createDdbClient();
        
        if (ObjectHelper.isNotEmpty(configuration.getAmazonDdbEndpoint())) {
            ddbClient.setEndpoint(configuration.getAmazonDdbEndpoint());
        }
        
        String tableName = getConfiguration().getTableName();
        LOG.trace("Querying whether table [{}] already exists...", tableName);

        try {
            DescribeTableRequest request = new DescribeTableRequest().withTableName(tableName);
            TableDescription tableDescription = ddbClient.describeTable(request).getTable();
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

    private TableDescription createTable(String tableName) {
        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                .withKeySchema(new KeySchema(
                        new KeySchemaElement().withAttributeName(
                                configuration.getKeyAttributeName())
                                .withAttributeType(configuration.getKeyAttributeType())))
                .withProvisionedThroughput(
                        new ProvisionedThroughput().withReadCapacityUnits(configuration.getReadCapacity())
                                .withWriteCapacityUnits(configuration.getWriteCapacity()));
        return getDdbClient().createTable(createTableRequest).getTableDescription();
    }

    public DdbConfiguration getConfiguration() {
        return configuration;
    }

    public AmazonDynamoDB getDdbClient() {
        return ddbClient;
    }

    AmazonDynamoDB createDdbClient() {
        AWSCredentials credentials = new BasicAWSCredentials(configuration.getAccessKey(),
                configuration.getSecretKey());
        AmazonDynamoDB client = new AmazonDynamoDBClient(credentials);
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
                DescribeTableRequest request = new DescribeTableRequest().withTableName(tableName);
                TableDescription tableDescription = getDdbClient().describeTable(request).getTable();
                if (isTableActive(tableDescription)) {
                    LOG.trace("Table [{}] became active", tableName);
                    return;
                }
                LOG.trace("Table [{}] not active yet", tableName);
            } catch (AmazonServiceException ase) {
                if (!ase.getErrorCode().equalsIgnoreCase("ResourceNotFoundException")) {
                    throw ase;
                }
            }
        }

        throw new RuntimeException("Table " + tableName + " never went active");
    }

    private boolean isTableActive(TableDescription tableDescription) {
        return tableDescription.getTableStatus().equals(TableStatus.ACTIVE.toString());
    }
}
