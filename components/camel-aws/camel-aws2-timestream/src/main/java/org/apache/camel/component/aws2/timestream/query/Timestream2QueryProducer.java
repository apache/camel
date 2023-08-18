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
package org.apache.camel.component.aws2.timestream.query;

import java.time.Instant;
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
import software.amazon.awssdk.services.timestreamquery.TimestreamQueryClient;
import software.amazon.awssdk.services.timestreamquery.model.*;

/**
 * A Producer which sends messages to the Amazon Web Service Timestream <a href="https://aws.amazon.com/timestream/">AWS
 * Timestream</a>
 */
public class Timestream2QueryProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Timestream2QueryProducer.class);

    private transient String timestreamQueryProducerToString;

    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Timestream2QueryProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case describeEndpoints -> describeEndpoints(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case createScheduledQuery -> createScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case deleteScheduledQuery -> deleteScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case executeScheduledQuery -> executeScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case updateScheduledQuery -> updateScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case describeScheduledQuery -> describeScheduledQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case listScheduledQueries -> listScheduledQueries(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case prepareQuery -> prepareQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case query -> query(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            case cancelQuery -> cancelQuery(getEndpoint().getAwsTimestreamQueryClient(), exchange);
            default -> throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Timestream2Operations determineOperation(Exchange exchange) {
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
        if (timestreamQueryProducerToString == null) {
            timestreamQueryProducerToString
                    = "TimestreamQueryProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return timestreamQueryProducerToString;
    }

    @Override
    public Timestream2QueryEndpoint getEndpoint() {
        return (Timestream2QueryEndpoint) super.getEndpoint();
    }

    private void describeEndpoints(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeEndpointsRequest request) {
                DescribeEndpointsResponse result;
                try {
                    result = timestreamQueryClient.describeEndpoints(request);
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
                result = timestreamQueryClient.describeEndpoints(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Endpoints command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateScheduledQueryRequest request) {
                CreateScheduledQueryResponse result;
                try {
                    result = timestreamQueryClient.createScheduledQuery(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateScheduledQueryRequest.Builder builder = CreateScheduledQueryRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_NAME))) {
                String name = exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_NAME, String.class);
                builder.name(name);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.QUERY_STRING))) {
                String queryString = exchange.getIn().getHeader(Timestream2Constants.QUERY_STRING, String.class);
                builder.queryString(queryString);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULE_EXPRESSION))) {
                String scheduleExp = exchange.getIn().getHeader(Timestream2Constants.SCHEDULE_EXPRESSION, String.class);
                builder.scheduleConfiguration(ScheduleConfiguration.builder().scheduleExpression(scheduleExp).build());
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.NOTIFICATION_TOPIC_ARN))) {
                String topicArn = exchange.getIn().getHeader(Timestream2Constants.NOTIFICATION_TOPIC_ARN, String.class);
                SnsConfiguration snsConfiguration = SnsConfiguration.builder().topicArn(topicArn).build();
                builder.notificationConfiguration(
                        NotificationConfiguration.builder().snsConfiguration(snsConfiguration).build());
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.KMS_KEY_ID))) {
                String kmsKeyId = exchange.getIn().getHeader(Timestream2Constants.KMS_KEY_ID, String.class);
                builder.kmsKeyId(kmsKeyId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.CLIENT_TOKEN))) {
                String clientToken = exchange.getIn().getHeader(Timestream2Constants.CLIENT_TOKEN, String.class);
                builder.clientToken(clientToken);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_EXECUTION_ROLE_ARN))) {
                String roleArn
                        = exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_EXECUTION_ROLE_ARN, String.class);
                builder.scheduledQueryExecutionRoleArn(roleArn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.ERROR_REPORT_S3_BUCKET_NAME))) {
                String s3BucketName
                        = exchange.getIn().getHeader(Timestream2Constants.ERROR_REPORT_S3_BUCKET_NAME, String.class);
                S3Configuration.Builder s3Configuration = S3Configuration.builder().bucketName(s3BucketName);
                if (ObjectHelper
                        .isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.ERROR_REPORT_S3_OBJECT_KEY_PREFIX))) {
                    String objectKeyPrefix
                            = exchange.getIn().getHeader(Timestream2Constants.ERROR_REPORT_S3_OBJECT_KEY_PREFIX, String.class);
                    s3Configuration.objectKeyPrefix(objectKeyPrefix);
                }

                if (ObjectHelper
                        .isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.ERROR_REPORT_S3_ENCRYPTION_OPTION))) {
                    String encryptionOption
                            = exchange.getIn().getHeader(Timestream2Constants.ERROR_REPORT_S3_ENCRYPTION_OPTION, String.class);
                    s3Configuration.encryptionOption(encryptionOption);
                }

                ErrorReportConfiguration errorReportConfiguration
                        = ErrorReportConfiguration.builder().s3Configuration(s3Configuration.build()).build();
                builder.errorReportConfiguration(errorReportConfiguration);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_EXECUTION_ROLE_ARN))) {
                String roleArn
                        = exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_EXECUTION_ROLE_ARN, String.class);
                builder.scheduledQueryExecutionRoleArn(roleArn);
            }

            TargetConfiguration.Builder targetConfiguration = TargetConfiguration.builder();
            TimestreamConfiguration.Builder timestreamConfigBuilder = TimestreamConfiguration.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME))) {
                String database = exchange.getIn().getHeader(Timestream2Constants.DATABASE_NAME, String.class);
                timestreamConfigBuilder.databaseName(database);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME))) {
                String table = exchange.getIn().getHeader(Timestream2Constants.TABLE_NAME, String.class);
                timestreamConfigBuilder.tableName(table);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.TIME_COLUMN))) {
                String timeColumn = exchange.getIn().getHeader(Timestream2Constants.TIME_COLUMN, String.class);
                timestreamConfigBuilder.tableName(timeColumn);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MEASURE_NAME_COLUMN))) {
                String measureNameColumn = exchange.getIn().getHeader(Timestream2Constants.MEASURE_NAME_COLUMN, String.class);
                timestreamConfigBuilder.measureNameColumn(measureNameColumn);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.DIMENSION_MAPPING_LIST))) {
                List<DimensionMapping> dimensionMappingList
                        = exchange.getIn().getHeader(Timestream2Constants.DIMENSION_MAPPING_LIST, List.class);
                timestreamConfigBuilder.dimensionMappings(dimensionMappingList);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MULTI_MEASURE_MAPPINGS))) {
                MultiMeasureMappings multiMeasureMappings
                        = exchange.getIn().getHeader(Timestream2Constants.MULTI_MEASURE_MAPPINGS, MultiMeasureMappings.class);
                timestreamConfigBuilder.multiMeasureMappings(multiMeasureMappings);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MIXED_MEASURE_MAPPING_LIST))) {
                List<MixedMeasureMapping> mixedMeasureMappings
                        = exchange.getIn().getHeader(Timestream2Constants.MIXED_MEASURE_MAPPING_LIST, List.class);
                timestreamConfigBuilder.mixedMeasureMappings(mixedMeasureMappings);
            }
            targetConfiguration.timestreamConfiguration(timestreamConfigBuilder.build());
            builder.targetConfiguration(targetConfiguration.build());

            CreateScheduledQueryResponse result;
            try {
                result = timestreamQueryClient.createScheduledQuery(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteScheduledQueryRequest request) {
                DeleteScheduledQueryResponse result;
                try {
                    result = timestreamQueryClient.deleteScheduledQuery(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteScheduledQueryRequest.Builder builder = DeleteScheduledQueryRequest.builder();

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_ARN))) {
                String queryArn = exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_ARN, String.class);
                builder.scheduledQueryArn(queryArn);
            }

            DeleteScheduledQueryResponse result;
            try {
                result = timestreamQueryClient.deleteScheduledQuery(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }

    }

    private void executeScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ExecuteScheduledQueryRequest request) {
                ExecuteScheduledQueryResponse result;
                try {
                    result = timestreamQueryClient.executeScheduledQuery(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Execute Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ExecuteScheduledQueryRequest.Builder builder = ExecuteScheduledQueryRequest.builder();

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_ARN))) {
                String queryArn = exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_ARN, String.class);
                builder.scheduledQueryArn(queryArn);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.CLIENT_TOKEN))) {
                String clientToken = exchange.getIn().getHeader(Timestream2Constants.CLIENT_TOKEN, String.class);
                builder.clientToken(clientToken);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_INVOCATION_TIME))) {
                Instant invocationTime
                        = exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_INVOCATION_TIME, Instant.class);
                builder.invocationTime(invocationTime);
            }

            ExecuteScheduledQueryResponse result;
            try {
                result = timestreamQueryClient.executeScheduledQuery(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Execute Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void updateScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateScheduledQueryRequest request) {
                UpdateScheduledQueryResponse result;
                try {
                    result = timestreamQueryClient.updateScheduledQuery(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Update Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            UpdateScheduledQueryRequest.Builder builder = UpdateScheduledQueryRequest.builder();

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_ARN))) {
                String queryArn = exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_ARN, String.class);
                builder.scheduledQueryArn(queryArn);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_STATE))) {
                String state = exchange.getIn().getHeader(Timestream2Constants.SCHEDULED_QUERY_STATE, String.class);
                builder.state(state);
            }

            UpdateScheduledQueryResponse result;
            try {
                result = timestreamQueryClient.updateScheduledQuery(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Update Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeScheduledQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeScheduledQueryRequest request) {
                DescribeScheduledQueryResponse result;
                try {
                    result = timestreamQueryClient.describeScheduledQuery(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeScheduledQueryRequest.Builder builder = DescribeScheduledQueryRequest.builder();

            DescribeScheduledQueryResponse result;
            try {
                result = timestreamQueryClient.describeScheduledQuery(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Scheduled Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listScheduledQueries(TimestreamQueryClient timestreamQueryClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListScheduledQueriesRequest request) {
                ListScheduledQueriesResponse result;
                try {
                    result = timestreamQueryClient.listScheduledQueries(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Scheduled Queries command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListScheduledQueriesRequest.Builder builder = ListScheduledQueriesRequest.builder();

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.MAX_RESULTS))) {
                Integer maxResults = exchange.getIn().getHeader(Timestream2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }

            ListScheduledQueriesResponse result;
            try {
                result = timestreamQueryClient.listScheduledQueries(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Scheduled Queries command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void prepareQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PrepareQueryRequest request) {
                PrepareQueryResponse result;
                try {
                    result = timestreamQueryClient.prepareQuery(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Prepare Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            PrepareQueryRequest.Builder builder = PrepareQueryRequest.builder();

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.QUERY_STRING))) {
                String queryString = exchange.getIn().getHeader(Timestream2Constants.QUERY_STRING, String.class);
                builder.queryString(queryString);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.QUERY_VALIDATE_ONLY))) {
                Boolean validateFlag = exchange.getIn().getHeader(Timestream2Constants.QUERY_VALIDATE_ONLY, Boolean.class);
                builder.validateOnly(validateFlag);
            }

            PrepareQueryResponse result;
            try {
                result = timestreamQueryClient.prepareQuery(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Prepare Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void query(TimestreamQueryClient timestreamQueryClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof QueryRequest request) {
                QueryResponse result;
                try {
                    result = timestreamQueryClient.query(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            QueryRequest.Builder builder = QueryRequest.builder();

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.QUERY_STRING))) {
                String queryString = exchange.getIn().getHeader(Timestream2Constants.QUERY_STRING, String.class);
                builder.queryString(queryString);
            }

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.QUERY_MAX_ROWS))) {
                Integer maxRows = exchange.getIn().getHeader(Timestream2Constants.QUERY_MAX_ROWS, Integer.class);
                builder.maxRows(maxRows);
            }

            QueryResponse result;
            try {
                result = timestreamQueryClient.query(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void cancelQuery(TimestreamQueryClient timestreamQueryClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CancelQueryRequest request) {
                CancelQueryResponse result;
                try {
                    result = timestreamQueryClient.cancelQuery(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Cancel Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CancelQueryRequest.Builder builder = CancelQueryRequest.builder();

            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Timestream2Constants.QUERY_ID))) {
                String queryId = exchange.getIn().getHeader(Timestream2Constants.QUERY_ID, String.class);
                builder.queryId(queryId);
            }

            CancelQueryResponse result;
            try {
                result = timestreamQueryClient.cancelQuery(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Cancel Query command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
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
            producerHealthCheck = new Timestream2QueryProducerHealthCheck(getEndpoint(), id);
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
