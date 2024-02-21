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

import java.time.Duration;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws2.ddb.client.Ddb2ClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.support.task.budget.IterationBoundedBudget;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

/**
 * Store and retrieve data from AWS DynamoDB.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-ddb", title = "AWS DynamoDB", syntax = "aws2-ddb:tableName",
             producerOnly = true, category = { Category.CLOUD, Category.DATABASE },
             headersClass = Ddb2Constants.class)
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
    public Ddb2Component getComponent() {
        return (Ddb2Component) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        ddbClient = configuration.getAmazonDDBClient() != null
                ? configuration.getAmazonDDBClient() : Ddb2ClientFactory.getDynamoDBClient(configuration).getDynamoDBClient();

        String tableName = getConfiguration().getTableName();
        LOG.trace("Querying whether table [{}] already exists...", tableName);

        if (configuration.isEnabledInitialDescribeTable()) {
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
                .keySchema(KeySchemaElement.builder().attributeName(configuration.getKeyAttributeName())
                        .keyType(configuration.getKeyAttributeType()).build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(configuration.getReadCapacity())
                        .writeCapacityUnits(configuration.getWriteCapacity()).build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName(configuration.getKeyAttributeName())
                        .attributeType(configuration.getKeyScalarType())
                        .build());
        return getDdbClient().createTable(createTableRequest.build()).tableDescription();
    }

    public Ddb2Configuration getConfiguration() {
        return configuration;
    }

    public DynamoDbClient getDdbClient() {
        return ddbClient;
    }

    private void waitForTableToBecomeAvailable(String tableName) {
        LOG.trace("Waiting for [{}] to become ACTIVE...", tableName);

        BlockingTask task = Tasks.foregroundTask().withBudget(Budgets.iterationTimeBudget()
                .withMaxIterations(IterationBoundedBudget.UNLIMITED_ITERATIONS)
                .withMaxDuration(Duration.ofMinutes(5))
                .withInterval(Duration.ofSeconds(5))
                .build())
                .build();

        if (!task.run(this::waitForTable, tableName)) {
            throw new RuntimeCamelException("Table " + tableName + " never went active");
        }
    }

    private boolean waitForTable(String tableName) {
        try {
            DescribeTableRequest request = DescribeTableRequest.builder().tableName(tableName).build();
            TableDescription tableDescription = getDdbClient().describeTable(request).table();
            if (isTableActive(tableDescription)) {
                LOG.trace("Table [{}] became active", tableName);
                return true;
            }
            LOG.trace("Table [{}] not active yet", tableName);
        } catch (AwsServiceException ase) {
            if (!ase.getMessage().contains("ResourceNotFoundException")) {
                throw ase;
            }
        }
        return false;
    }

    private boolean isTableActive(TableDescription tableDescription) {
        return tableDescription.tableStatus().toString().equals(TableStatus.ACTIVE.toString());
    }
}
