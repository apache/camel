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
package org.apache.camel.component.aws2.timestream.write;

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.aws2.timestream.Timestream2Configuration;
import org.apache.camel.component.aws2.timestream.Timestream2Constants;
import org.apache.camel.component.aws2.timestream.Timestream2Operations;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient;
import software.amazon.awssdk.services.timestreamwrite.model.*;
import software.amazon.awssdk.services.timestreamwrite.model.Record;

/**
 * A Producer which sends messages to the Amazon Web Service Timestream <a href="https://aws.amazon.com/timestream/">AWS
 * Timestream</a>
 */
public class Timestream2WriteProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Timestream2WriteProducer.class);

    private transient String timestreamWriteProducerToString;

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Timestream2WriteProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case describeEndpoints -> describeEndpoints(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case createBatchLoadTask -> createBatchLoadTask(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case describeBatchLoadTask -> describeBatchLoadTask(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case resumeBatchLoadTask -> resumeBatchLoadTask(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case listBatchLoadTasks -> listBatchLoadTasks(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case createDatabase -> createDatabase(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case deleteDatabase -> deleteDatabase(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case describeDatabase -> describeDatabase(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case updateDatabase -> updateDatabase(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case listDatabases -> listDatabases(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case createTable -> createTable(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case deleteTable -> deleteTable(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case describeTable -> describeTable(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case updateTable -> updateTable(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case listTables -> listTables(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            case writeRecords -> writeRecords(getEndpoint().getAwsTimestreamWriteClient(), exchange);
            default -> throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Timestream2Operations determineOperation(Exchange exchange) throws InvalidPayloadException {
        Timestream2Operations operation
                = exchange.getIn().getHeader(Timestream2Constants.OPERATION, Timestream2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected Timestream2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (timestreamWriteProducerToString == null) {
            timestreamWriteProducerToString
                    = "TimestreamWriteProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return timestreamWriteProducerToString;
    }

    @Override
    public Timestream2WriteEndpoint getEndpoint() {
        return (Timestream2WriteEndpoint) super.getEndpoint();
    }

    private void describeEndpoints(TimestreamWriteClient timestreamWriteClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeEndpointsRequest request) {
                DescribeEndpointsResponse result;
                try {
                    result = timestreamWriteClient.describeEndpoints(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Endpoints command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeEndpointsRequest.Builder builder = DescribeEndpointsRequest.builder();

            DescribeEndpointsResponse result;
            try {
                result = timestreamWriteClient.describeEndpoints(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Endpoints command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createBatchLoadTask(TimestreamWriteClient timestreamWriteClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateBatchLoadTaskRequest request) {
                CreateBatchLoadTaskResponse result;
                try {
                    result = timestreamWriteClient.createBatchLoadTask(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Batch Load Task command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateBatchLoadTaskRequest.Builder builder = CreateBatchLoadTaskRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.CLIENT_TOKEN))) {
                String clientToken = exchange.getIn().getHeader(Timestream2Constants.CLIENT_TOKEN, String.class);
                builder.clientToken(clientToken);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TARGET_DATABASE_NAME))) {
                String targetDB = exchange.getIn().getHeader(Timestream2Constants.TARGET_DATABASE_NAME, String.class);
                builder.targetDatabaseName(targetDB);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TARGET_TABLE_NAME))) {
                String targetTable = exchange.getIn().getHeader(Timestream2Constants.TARGET_TABLE_NAME, String.class);
                builder.targetTableName(targetTable);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.RECORD_VERSION))) {
                Long recordVersion = exchange.getIn().getHeader(Timestream2Constants.RECORD_VERSION, Long.class);
                builder.recordVersion(recordVersion);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATA_MODEL_CONFIGURATION))) {
                DataModelConfiguration config = exchange.getIn().getHeader(Timestream2Constants.DATA_MODEL_CONFIGURATION,
                        DataModelConfiguration.class);
                builder.dataModelConfiguration(config);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATA_SOURCE_CONFIGURATION))) {
                DataSourceConfiguration config = exchange.getIn().getHeader(Timestream2Constants.DATA_SOURCE_CONFIGURATION,
                        DataSourceConfiguration.class);
                builder.dataSourceConfiguration(config);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.REPORT_CONFIGURATION))) {
                ReportConfiguration config
                        = exchange.getIn().getHeader(Timestream2Constants.REPORT_CONFIGURATION, ReportConfiguration.class);
                builder.reportConfiguration(config);
            }

            CreateBatchLoadTaskResponse result;
            try {
                result = timestreamWriteClient.createBatchLoadTask(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Batch Load Task command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeBatchLoadTask(TimestreamWriteClient timestreamWriteClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeBatchLoadTaskRequest request) {
                DescribeBatchLoadTaskResponse result;
                try {
                    result = timestreamWriteClient.describeBatchLoadTask(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Batch Load Task command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeBatchLoadTaskRequest.Builder builder = DescribeBatchLoadTaskRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TASK_ID))) {
                String taskId = exchange.getIn().getHeader(Timestream2Constants.TASK_ID, String.class);
                builder.taskId(taskId);
            }

            DescribeBatchLoadTaskResponse result;
            try {
                result = timestreamWriteClient.describeBatchLoadTask(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Batch Load Task command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void resumeBatchLoadTask(TimestreamWriteClient timestreamWriteClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ResumeBatchLoadTaskRequest request) {
                ResumeBatchLoadTaskResponse result;
                try {
                    result = timestreamWriteClient.resumeBatchLoadTask(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Resume Batch Load Task command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ResumeBatchLoadTaskRequest.Builder builder = ResumeBatchLoadTaskRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TASK_ID))) {
                String taskId = exchange.getIn().getHeader(Timestream2Constants.TASK_ID, String.class);
                builder.taskId(taskId);
            }

            ResumeBatchLoadTaskResponse result;
            try {
                result = timestreamWriteClient.resumeBatchLoadTask(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Resume Batch Load Task command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listBatchLoadTasks(TimestreamWriteClient timestreamWriteClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListBatchLoadTasksRequest request) {
                ListBatchLoadTasksResponse result;
                try {
                    result = timestreamWriteClient.listBatchLoadTasks(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Batch Load Tasks command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListBatchLoadTasksRequest.Builder builder = ListBatchLoadTasksRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(Timestream2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TASK_STATUS))) {
                String taskStatus = exchange.getIn().getHeader(Timestream2Constants.TASK_STATUS, String.class);
                builder.taskStatus(taskStatus);
            }

            ListBatchLoadTasksResponse result;
            try {
                result = timestreamWriteClient.listBatchLoadTasks(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Batch Load Tasks command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createDatabase(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateDatabaseRequest request) {
                CreateDatabaseResponse result;
                try {
                    result = timestreamWriteClient.createDatabase(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Database command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateDatabaseRequest.Builder builder = CreateDatabaseRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.KMS_KEY_ID))) {
                String kmsKeyId = exchange.getIn().getHeader(Timestream2Constants.KMS_KEY_ID, String.class);
                builder.kmsKeyId(kmsKeyId);
            }

            CreateDatabaseResponse result;
            try {
                result = timestreamWriteClient.createDatabase(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Database command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteDatabase(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteDatabaseRequest request) {
                DeleteDatabaseResponse result;
                try {
                    result = timestreamWriteClient.deleteDatabase(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Database command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteDatabaseRequest.Builder builder = DeleteDatabaseRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }

            DeleteDatabaseResponse result;
            try {
                result = timestreamWriteClient.deleteDatabase(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Database command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeDatabase(TimestreamWriteClient timestreamWriteClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeDatabaseRequest request) {
                DescribeDatabaseResponse result;
                try {
                    result = timestreamWriteClient.describeDatabase(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Database command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeDatabaseRequest.Builder builder = DescribeDatabaseRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }

            DescribeDatabaseResponse result;
            try {
                result = timestreamWriteClient.describeDatabase(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Database command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void updateDatabase(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateDatabaseRequest request) {
                UpdateDatabaseResponse result;
                try {
                    result = timestreamWriteClient.updateDatabase(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Update Database command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            UpdateDatabaseRequest.Builder builder = UpdateDatabaseRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.KMS_KEY_ID))) {
                String kmsKeyId = exchange.getIn().getHeader(Timestream2Constants.KMS_KEY_ID, String.class);
                builder.kmsKeyId(kmsKeyId);
            }

            UpdateDatabaseResponse result;
            try {
                result = timestreamWriteClient.updateDatabase(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Update Database command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listDatabases(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListDatabasesRequest request) {
                ListDatabasesResponse result;
                try {
                    result = timestreamWriteClient.listDatabases(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Databases command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListDatabasesRequest.Builder builder = ListDatabasesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(Timestream2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }

            ListDatabasesResponse result;
            try {
                result = timestreamWriteClient.listDatabases(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Databases command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createTable(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateTableRequest request) {
                CreateTableResponse result;
                try {
                    result = timestreamWriteClient.createTable(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateTableRequest.Builder builder = CreateTableRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME))) {
                String tableName = exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME, String.class);
                builder.tableName(tableName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEMA))) {
                Schema schema = exchange.getIn().getHeader(Timestream2Constants.SCHEMA, Schema.class);
                builder.schema(schema);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.RETENTION_PROPERTIES))) {
                RetentionProperties properties
                        = exchange.getIn().getHeader(Timestream2Constants.RETENTION_PROPERTIES, RetentionProperties.class);
                builder.retentionProperties(properties);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MAGNETIC_STORE_WRITE_PROPERTIES))) {
                MagneticStoreWriteProperties properties = exchange.getIn()
                        .getHeader(Timestream2Constants.MAGNETIC_STORE_WRITE_PROPERTIES, MagneticStoreWriteProperties.class);
                builder.magneticStoreWriteProperties(properties);
            }

            CreateTableResponse result;
            try {
                result = timestreamWriteClient.createTable(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteTable(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteTableRequest request) {
                DeleteTableResponse result;
                try {
                    result = timestreamWriteClient.deleteTable(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteTableRequest.Builder builder = DeleteTableRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME))) {
                String tableName = exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME, String.class);
                builder.tableName(tableName);
            }

            DeleteTableResponse result;
            try {
                result = timestreamWriteClient.deleteTable(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeTable(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeTableRequest request) {
                DescribeTableResponse result;
                try {
                    result = timestreamWriteClient.describeTable(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeTableRequest.Builder builder = DescribeTableRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME))) {
                String tableName = exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME, String.class);
                builder.tableName(tableName);
            }

            DescribeTableResponse result;
            try {
                result = timestreamWriteClient.describeTable(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void updateTable(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateTableRequest request) {
                UpdateTableResponse result;
                try {
                    result = timestreamWriteClient.updateTable(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Update Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            UpdateTableRequest.Builder builder = UpdateTableRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME))) {
                String tableName = exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME, String.class);
                builder.tableName(tableName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEMA))) {
                Schema schema = exchange.getIn().getHeader(Timestream2Constants.SCHEMA, Schema.class);
                builder.schema(schema);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.RETENTION_PROPERTIES))) {
                RetentionProperties properties
                        = exchange.getIn().getHeader(Timestream2Constants.RETENTION_PROPERTIES, RetentionProperties.class);
                builder.retentionProperties(properties);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MAGNETIC_STORE_WRITE_PROPERTIES))) {
                MagneticStoreWriteProperties properties = exchange.getIn()
                        .getHeader(Timestream2Constants.MAGNETIC_STORE_WRITE_PROPERTIES, MagneticStoreWriteProperties.class);
                builder.magneticStoreWriteProperties(properties);
            }

            UpdateTableResponse result;
            try {
                result = timestreamWriteClient.updateTable(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Update Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listTables(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListTablesRequest request) {
                ListTablesResponse result;
                try {
                    result = timestreamWriteClient.listTables(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Tables command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListTablesRequest.Builder builder = ListTablesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(Timestream2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }

            ListTablesResponse result;
            try {
                result = timestreamWriteClient.listTables(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Tables command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void writeRecords(TimestreamWriteClient timestreamWriteClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof WriteRecordsRequest request) {
                WriteRecordsResponse result;
                try {
                    result = timestreamWriteClient.writeRecords(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Write Records command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            WriteRecordsRequest.Builder builder = WriteRecordsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String dbName = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                builder.databaseName(dbName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME))) {
                String tableName = exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME, String.class);
                builder.tableName(tableName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.RECORD))) {
                Record recordObject = exchange.getIn().getHeader(Timestream2Constants.RECORD, Record.class);
                builder.commonAttributes(recordObject);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.RECORD_LIST))) {
                List<Record> records = exchange.getIn().getHeader(Timestream2Constants.RECORD_LIST, List.class);
                builder.records(records);
            }

            WriteRecordsResponse result;
            try {
                result = timestreamWriteClient.writeRecords(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Write Records command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) throws InvalidPayloadException {
        return exchange.getMessage();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new Timestream2WriteProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
