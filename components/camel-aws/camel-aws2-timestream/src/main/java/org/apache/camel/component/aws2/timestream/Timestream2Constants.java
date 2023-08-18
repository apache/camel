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
package org.apache.camel.component.aws2.timestream;

import org.apache.camel.spi.Metadata;

public interface Timestream2Constants {

    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsTimestreamOperation";
    @Metadata(description = "The limit number of results while listing databases", javaType = "Integer")
    String LIST_DATABASES_MAX_RESULTS = "CamelAwsTimestreamListDatabaseMaxResults";
    @Metadata(description = "Name of Database", javaType = "String")
    String DATABASE_NAME = "CamelAwsTimestreamDatabaseName";
    @Metadata(description = "Name of Table", javaType = "String")
    String TABLE_NAME = "CamelAwsTimestreamTableName";
    @Metadata(description = "Name of Time column", javaType = "String")
    String TIME_COLUMN = "CamelAwsTimestreamTimeColumn";
    @Metadata(description = "Name of the measure column.", javaType = "String")
    String MEASURE_NAME_COLUMN = "CamelAwsTimestreamMeasureColumnName";
    @Metadata(description = "This is to allow mapping column(s) from the query result to the dimension in the destination table.",
              javaType = "List")
    String DIMENSION_MAPPING_LIST = "CamelAwsTimestreamDimensionMappingList";
    @Metadata(description = "Multi-measure mappings.", javaType = "String")
    String MULTI_MEASURE_MAPPINGS = "CamelAwsTimestreamMultiMeasureMappings";
    @Metadata(description = "Specifies how to map measures to multi-measure records.", javaType = "List")
    String MIXED_MEASURE_MAPPING_LIST = "CamelAwsTimestreamMixedMeasureMappingList";
    @Metadata(description = "Name of scheduled query", javaType = "String")
    String SCHEDULED_QUERY_NAME = "CamelAwsTimestreamScheduledQueryName";
    @Metadata(description = "Arn of scheduled query", javaType = "String")
    String SCHEDULED_QUERY_ARN = "CamelAwsTimestreamScheduledQueryArn";
    @Metadata(description = "State of scheduled query", javaType = "String")
    String SCHEDULED_QUERY_STATE = "CamelAwsTimestreamScheduledQueryState";
    @Metadata(description = "Invocation Time for scheduled query execution", javaType = "Instant")
    String SCHEDULED_QUERY_INVOCATION_TIME = "CamelAwsTimestreamScheduledQueryInvocationTime";
    @Metadata(description = "The query string to run.", javaType = "String")
    String QUERY_STRING = "CamelAwsTimestreamQueryString";
    @Metadata(description = "ID of query.", javaType = "String")
    String QUERY_ID = "CamelAwsTimestreamQueryId";
    @Metadata(description = "Validates the prepared query, but does not store for later execution", javaType = "Boolean")
    String QUERY_VALIDATE_ONLY = "CamelAwsTimestreamQueryValidateOnly";
    @Metadata(description = "The total number of rows to be returned in the Query output.", javaType = "Integer")
    String QUERY_MAX_ROWS = "CamelAwsTimestreamQueryMaxRows";
    @Metadata(description = "Max Results to be returned in output", javaType = "Integer")
    String MAX_RESULTS = "CamelAwsTimestreamMaxResults";
    @Metadata(description = "The schedule expression for the query.", javaType = "String")
    String SCHEDULE_EXPRESSION = "CamelAwsTimestreamScheduleExpression";
    @Metadata(description = "Notification Topic Arn for the scheduled query.", javaType = "String")
    String NOTIFICATION_TOPIC_ARN = "CamelAwsTimestreamNotificationTopicArn";
    @Metadata(description = "S3 Bucket name for error reporting.", javaType = "String")
    String ERROR_REPORT_S3_BUCKET_NAME = "CamelAwsTimestreamErrorReportS3BucketName";
    @Metadata(description = "S3 object key prefix for error reporting.", javaType = "String")
    String ERROR_REPORT_S3_OBJECT_KEY_PREFIX = "CamelAwsTimestreamErrorReportS3ObjectKeyPrefix";
    @Metadata(description = "S3 encryption option for error reporting.", javaType = "String")
    String ERROR_REPORT_S3_ENCRYPTION_OPTION = "CamelAwsTimestreamErrorReportS3EncryptionOption";
    @Metadata(description = "he ARN for the IAM role that Timestream will assume when running the scheduled query.",
              javaType = "String")
    String SCHEDULED_QUERY_EXECUTION_ROLE_ARN = "CamelAwsTimestreamScheduledQueryExecutionRoleArn";
    @Metadata(description = "Using a ClientToken makes the call to CreateScheduledQuery idempotent", javaType = "String")
    String CLIENT_TOKEN = "CamelAwsTimestreamClientToken";
    @Metadata(description = "The Amazon KMS key used to encrypt the scheduled query resource, at-rest.", javaType = "String")
    String KMS_KEY_ID = "CamelAwsTimestreamKmsKeyId";

}
