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
package org.apache.camel.component.aws2.redshift.data;

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.redshiftdata.RedshiftDataClient;
import software.amazon.awssdk.services.redshiftdata.model.*;

/**
 * A Producer to perform to perform actions on AWS Redshift through Redshift Data
 * <a href="https://docs.aws.amazon.com/redshift-data/latest/APIReference">Amazon Redshift Data API</a>
 */
public class RedshiftData2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RedshiftData2Producer.class);

    private transient String redshiftDataProducerToString;

    public RedshiftData2Producer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listDatabases -> listDatabases(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case listSchemas -> listSchemas(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case listStatements -> listStatements(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case listTables -> listTables(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case describeTable -> describeTable(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case executeStatement -> executeStatement(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case batchExecuteStatement -> batchExecuteStatement(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case cancelStatement -> cancelStatement(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case describeStatement -> describeStatement(getConfiguration().getAwsRedshiftDataClient(), exchange);
            case getStatementResult -> getStatementResult(getConfiguration().getAwsRedshiftDataClient(), exchange);
            default -> throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private RedshiftData2Operations determineOperation(Exchange exchange) {
        RedshiftData2Operations operation
                = exchange.getIn().getHeader(RedshiftData2Constants.OPERATION, RedshiftData2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected RedshiftData2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (redshiftDataProducerToString == null) {
            redshiftDataProducerToString
                    = "RedshiftDataProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return redshiftDataProducerToString;
    }

    @Override
    public RedshiftData2Endpoint getEndpoint() {
        return (RedshiftData2Endpoint) super.getEndpoint();
    }

    private void listDatabases(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListDatabasesRequest request) {
                ListDatabasesResponse result;
                try {
                    result = redshiftDataClient.listDatabases(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Databases command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListDatabasesRequest.Builder builder = ListDatabasesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER))) {
                String clusterIdentifier = exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER, String.class);
                builder.clusterIdentifier(clusterIdentifier);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DATABASE))) {
                String database = exchange.getIn().getHeader(RedshiftData2Constants.DATABASE, String.class);
                builder.database(database);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DB_USER))) {
                String dbUser = exchange.getIn().getHeader(RedshiftData2Constants.DB_USER, String.class);
                builder.dbUser(dbUser);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN))) {
                String secretArn = exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN, String.class);
                builder.secretArn(secretArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.LIST_DATABASES_MAX_RESULTS))) {
                Integer maxResults
                        = exchange.getIn().getHeader(RedshiftData2Constants.LIST_DATABASES_MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME))) {
                String workgroupName = exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME, String.class);
                builder.workgroupName(workgroupName);
            }

            ListDatabasesResponse result;
            try {
                result = redshiftDataClient.listDatabases(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Databases command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listSchemas(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListSchemasRequest request) {
                ListSchemasResponse result;
                try {
                    result = redshiftDataClient.listSchemas(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Schemas command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListSchemasRequest.Builder builder = ListSchemasRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER))) {
                String clusterIdentifier = exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER, String.class);
                builder.clusterIdentifier(clusterIdentifier);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DATABASE))) {
                String database = exchange.getIn().getHeader(RedshiftData2Constants.DATABASE, String.class);
                builder.database(database);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DB_USER))) {
                String dbUser = exchange.getIn().getHeader(RedshiftData2Constants.DB_USER, String.class);
                builder.dbUser(dbUser);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN))) {
                String secretArn = exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN, String.class);
                builder.secretArn(secretArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.LIST_SCHEMAS_MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(RedshiftData2Constants.LIST_SCHEMAS_MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME))) {
                String workgroupName = exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME, String.class);
                builder.workgroupName(workgroupName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CONNECTED_DATABASE))) {
                String connectedDatabase = exchange.getIn().getHeader(RedshiftData2Constants.CONNECTED_DATABASE, String.class);
                builder.connectedDatabase(connectedDatabase);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SCHEMA_PATTERN))) {
                String schemaPattern = exchange.getIn().getHeader(RedshiftData2Constants.SCHEMA_PATTERN, String.class);
                builder.schemaPattern(schemaPattern);
            }

            ListSchemasResponse result;
            try {
                result = redshiftDataClient.listSchemas(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Schemas command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listStatements(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListStatementsRequest request) {
                ListStatementsResponse result;
                try {
                    result = redshiftDataClient.listStatements(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Statements command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListStatementsRequest.Builder builder = ListStatementsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.ROLE_LEVEL))) {
                Boolean roleLevel = exchange.getIn().getHeader(RedshiftData2Constants.ROLE_LEVEL, Boolean.class);
                builder.roleLevel(roleLevel);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.STATUS))) {
                String status = exchange.getIn().getHeader(RedshiftData2Constants.STATUS, String.class);
                builder.status(status);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_NAME))) {
                String statementName = exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_NAME, String.class);
                builder.statementName(statementName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.LIST_STATEMENTS_MAX_RESULTS))) {
                Integer maxResults
                        = exchange.getIn().getHeader(RedshiftData2Constants.LIST_STATEMENTS_MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }

            ListStatementsResponse result;
            try {
                result = redshiftDataClient.listStatements(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Statements command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listTables(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListTablesRequest request) {
                ListTablesResponse result;
                try {
                    result = redshiftDataClient.listTables(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Tables command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListTablesRequest.Builder builder = ListTablesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER))) {
                String clusterIdentifier = exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER, String.class);
                builder.clusterIdentifier(clusterIdentifier);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DATABASE))) {
                String database = exchange.getIn().getHeader(RedshiftData2Constants.DATABASE, String.class);
                builder.database(database);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DB_USER))) {
                String dbUser = exchange.getIn().getHeader(RedshiftData2Constants.DB_USER, String.class);
                builder.dbUser(dbUser);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN))) {
                String secretArn = exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN, String.class);
                builder.secretArn(secretArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.LIST_TABLES_MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(RedshiftData2Constants.LIST_TABLES_MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME))) {
                String workgroupName = exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME, String.class);
                builder.workgroupName(workgroupName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CONNECTED_DATABASE))) {
                String connectedDatabase = exchange.getIn().getHeader(RedshiftData2Constants.CONNECTED_DATABASE, String.class);
                builder.connectedDatabase(connectedDatabase);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SCHEMA_PATTERN))) {
                String schemaPattern = exchange.getIn().getHeader(RedshiftData2Constants.SCHEMA_PATTERN, String.class);
                builder.schemaPattern(schemaPattern);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.TABLE_PATTERN))) {
                String tablePattern = exchange.getIn().getHeader(RedshiftData2Constants.TABLE_PATTERN, String.class);
                builder.tablePattern(tablePattern);
            }

            ListTablesResponse result;
            try {
                result = redshiftDataClient.listTables(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Tables command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeTable(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeTableRequest request) {
                DescribeTableResponse result;
                try {
                    result = redshiftDataClient.describeTable(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeTableRequest.Builder builder = DescribeTableRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER))) {
                String clusterIdentifier = exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER, String.class);
                builder.clusterIdentifier(clusterIdentifier);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DATABASE))) {
                String database = exchange.getIn().getHeader(RedshiftData2Constants.DATABASE, String.class);
                builder.database(database);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DB_USER))) {
                String dbUser = exchange.getIn().getHeader(RedshiftData2Constants.DB_USER, String.class);
                builder.dbUser(dbUser);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN))) {
                String secretArn = exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN, String.class);
                builder.secretArn(secretArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DESCRIBE_TABLE_MAX_RESULTS))) {
                Integer maxResults
                        = exchange.getIn().getHeader(RedshiftData2Constants.DESCRIBE_TABLE_MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME))) {
                String workgroupName = exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME, String.class);
                builder.workgroupName(workgroupName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CONNECTED_DATABASE))) {
                String connectedDatabase = exchange.getIn().getHeader(RedshiftData2Constants.CONNECTED_DATABASE, String.class);
                builder.connectedDatabase(connectedDatabase);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.TABLE))) {
                String table = exchange.getIn().getHeader(RedshiftData2Constants.TABLE, String.class);
                builder.table(table);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SCHEMA))) {
                String schema = exchange.getIn().getHeader(RedshiftData2Constants.SCHEMA, String.class);
                builder.schema(schema);
            }

            DescribeTableResponse result;
            try {
                result = redshiftDataClient.describeTable(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Table command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void executeStatement(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ExecuteStatementRequest request) {
                ExecuteStatementResponse result;
                try {
                    result = redshiftDataClient.executeStatement(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Execute Statement command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ExecuteStatementRequest.Builder builder = ExecuteStatementRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER))) {
                String clusterIdentifier = exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER, String.class);
                builder.clusterIdentifier(clusterIdentifier);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DATABASE))) {
                String database = exchange.getIn().getHeader(RedshiftData2Constants.DATABASE, String.class);
                builder.database(database);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DB_USER))) {
                String dbUser = exchange.getIn().getHeader(RedshiftData2Constants.DB_USER, String.class);
                builder.dbUser(dbUser);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN))) {
                String secretArn = exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN, String.class);
                builder.secretArn(secretArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME))) {
                String workgroupName = exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME, String.class);
                builder.workgroupName(workgroupName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_NAME))) {
                String statementName = exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_NAME, String.class);
                builder.statementName(statementName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WITH_EVENT))) {
                Boolean withEvent = exchange.getIn().getHeader(RedshiftData2Constants.WITH_EVENT, Boolean.class);
                builder.withEvent(withEvent);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLIENT_TOKEN))) {
                String clientToken = exchange.getIn().getHeader(RedshiftData2Constants.CLIENT_TOKEN, String.class);
                builder.clientToken(clientToken);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SQL_STATEMENT))) {
                String sqlStatement = exchange.getIn().getHeader(RedshiftData2Constants.SQL_STATEMENT, String.class);
                builder.sql(sqlStatement);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SQL_PARAMETER_LIST))) {
                List<SqlParameter> sqlParameterList
                        = exchange.getIn().getHeader(RedshiftData2Constants.SQL_PARAMETER_LIST, List.class);
                builder.parameters(sqlParameterList);
            }

            ExecuteStatementResponse result;
            try {
                result = redshiftDataClient.executeStatement(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Execute Statement command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void batchExecuteStatement(RedshiftDataClient redshiftDataClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof BatchExecuteStatementRequest request) {
                BatchExecuteStatementResponse result;
                try {
                    result = redshiftDataClient.batchExecuteStatement(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Batch Execute Statement command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            BatchExecuteStatementRequest.Builder builder = BatchExecuteStatementRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER))) {
                String clusterIdentifier = exchange.getIn().getHeader(RedshiftData2Constants.CLUSTER_IDENTIFIER, String.class);
                builder.clusterIdentifier(clusterIdentifier);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DATABASE))) {
                String database = exchange.getIn().getHeader(RedshiftData2Constants.DATABASE, String.class);
                builder.database(database);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.DB_USER))) {
                String dbUser = exchange.getIn().getHeader(RedshiftData2Constants.DB_USER, String.class);
                builder.dbUser(dbUser);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN))) {
                String secretArn = exchange.getIn().getHeader(RedshiftData2Constants.SECRET_ARN, String.class);
                builder.secretArn(secretArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME))) {
                String workgroupName = exchange.getIn().getHeader(RedshiftData2Constants.WORKGROUP_NAME, String.class);
                builder.workgroupName(workgroupName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_NAME))) {
                String statementName = exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_NAME, String.class);
                builder.statementName(statementName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.WITH_EVENT))) {
                Boolean withEvent = exchange.getIn().getHeader(RedshiftData2Constants.WITH_EVENT, Boolean.class);
                builder.withEvent(withEvent);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.CLIENT_TOKEN))) {
                String clientToken = exchange.getIn().getHeader(RedshiftData2Constants.CLIENT_TOKEN, String.class);
                builder.clientToken(clientToken);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.SQL_STATEMENT_LIST))) {
                List<String> sqlStatements = exchange.getIn().getHeader(RedshiftData2Constants.SQL_STATEMENT_LIST, List.class);
                builder.sqls(sqlStatements);
            }

            BatchExecuteStatementResponse result;
            try {
                result = redshiftDataClient.batchExecuteStatement(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Batch Execute Statement command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void cancelStatement(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CancelStatementRequest request) {
                CancelStatementResponse result;
                try {
                    result = redshiftDataClient.cancelStatement(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Cancel Statement command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CancelStatementRequest.Builder builder = CancelStatementRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_ID))) {
                String id = exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_ID, String.class);
                builder.id(id);
            }

            CancelStatementResponse result;
            try {
                result = redshiftDataClient.cancelStatement(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Cancel Statement command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeStatement(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeStatementRequest request) {
                DescribeStatementResponse result;
                try {
                    result = redshiftDataClient.describeStatement(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Statement command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeStatementRequest.Builder builder = DescribeStatementRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_ID))) {
                String id = exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_ID, String.class);
                builder.id(id);
            }

            DescribeStatementResponse result;
            try {
                result = redshiftDataClient.describeStatement(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Statement command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getStatementResult(RedshiftDataClient redshiftDataClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetStatementResultRequest request) {
                GetStatementResultResponse result;
                try {
                    result = redshiftDataClient.getStatementResult(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Statement Result command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetStatementResultRequest.Builder builder = GetStatementResultRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_ID))) {
                String id = exchange.getIn().getHeader(RedshiftData2Constants.STATEMENT_ID, String.class);
                builder.id(id);
            }

            GetStatementResultResponse result;
            try {
                result = redshiftDataClient.getStatementResult(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Statement Result command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

}
