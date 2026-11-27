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

    @Metadata(description = "The operation that the producer performs, as text or as a Timestream2Operations. It"
                            + " replaces the operation option.",
              javaType = "String")
    String OPERATION = "CamelAwsTimestreamOperation";
    @Metadata(description = "The common attributes of the writeRecords operation: the dimensions, measure and time values "
                            + "that all the records of the CamelAwsTimestreamRecordList header share. It is not written as a record of its own.",
              javaType = "software.amazon.awssdk.services.timestreamwrite.model.Record")
    String RECORD = "CamelAwsTimestreamRecord";
    @Metadata(description = "The records that writeRecords writes, as a List of the Record of the AWS SDK. Timestream"
                            + " accepts at most 100 records in one request.",
              javaType = "List")
    String RECORD_LIST = "CamelAwsTimestreamRecordList";
    @Metadata(description = "The status of the batch load tasks that listBatchLoadTasks lists, such as IN_PROGRESS or"
                            + " SUCCEEDED, as text. If not set, the tasks of every status are listed.",
              javaType = "String")
    String TASK_STATUS = "CamelAwsTimestreamTaskStatus";
    @Metadata(description = "The ID of the batch load task, for describeBatchLoadTask and resumeBatchLoadTask.",
              javaType = "String")
    String TASK_ID = "CamelAwsTimestreamTaskId";
    @Metadata(description = "The name of the database, for the database operations except listDatabases, the table"
                            + " operations and writeRecords. For createScheduledQuery, the database of the table that"
                            + " the scheduled query writes its results to.",
              javaType = "String")
    String DATABASE_NAME = "CamelAwsTimestreamDatabaseName";
    @Metadata(description = "The name of the table, for createTable, deleteTable, describeTable, updateTable and"
                            + " writeRecords. For createScheduledQuery, the table that the scheduled query writes its"
                            + " results to.",
              javaType = "String")
    String TABLE_NAME = "CamelAwsTimestreamTableName";
    @Metadata(description = "The name of the database that the batch load task of createBatchLoadTask loads the data"
                            + " into.",
              javaType = "String")
    String TARGET_DATABASE_NAME = "CamelAwsTimestreamTargetDatabaseName";
    @Metadata(description = "The name of the table that the batch load task of createBatchLoadTask loads the data into.",
              javaType = "String")
    String TARGET_TABLE_NAME = "CamelAwsTimestreamTargetTableName";
    @Metadata(description = "The version that createBatchLoadTask gives to the records it loads. A record with a higher"
                            + " version replaces the measure value of an existing record of the same time series and"
                            + " time.",
              javaType = "Long")
    String RECORD_VERSION = "CamelAwsTimestreamRecordVersion";
    @Metadata(description = "The data model of the batch load task of createBatchLoadTask, which maps the columns of"
                            + " the source data to the dimensions, measures and time of the records, as a"
                            + " DataModelConfiguration of the AWS SDK.",
              javaType = "software.amazon.awssdk.services.timestreamwrite.model.DataModelConfiguration")
    String DATA_MODEL_CONFIGURATION = "CamelAwsTimestreamDataModelConfiguration";
    @Metadata(description = "The source data of the batch load task of createBatchLoadTask: its S3 location and its"
                            + " format, as a DataSourceConfiguration of the AWS SDK.",
              javaType = "software.amazon.awssdk.services.timestreamwrite.model.DataSourceConfiguration")
    String DATA_SOURCE_CONFIGURATION = "CamelAwsTimestreamDataSourceConfiguration";
    @Metadata(description = "The S3 location where the batch load task of createBatchLoadTask writes its error report,"
                            + " as a ReportConfiguration of the AWS SDK.",
              javaType = "software.amazon.awssdk.services.timestreamwrite.model.ReportConfiguration")
    String REPORT_CONFIGURATION = "CamelAwsTimestreamReportConfiguration";
    @Metadata(description = "The schema of the table for createTable and updateTable, which defines its composite"
                            + " partition key, as a Schema of the AWS SDK.",
              javaType = "software.amazon.awssdk.services.timestreamwrite.model.Schema")
    String SCHEMA = "CamelAwsTimestreamTableSchema";
    @Metadata(description = "How long the memory store and the magnetic store of the table keep the data, for"
                            + " createTable and updateTable, as a RetentionProperties of the AWS SDK.",
              javaType = "software.amazon.awssdk.services.timestreamwrite.model.RetentionProperties")
    String RETENTION_PROPERTIES = "CamelAwsTimestreamRetentionProperties";
    @Metadata(description = "Whether the table accepts writes of records that are older than the retention of its"
                            + " memory store, and where Timestream reports the rejected records, for createTable and"
                            + " updateTable, as a MagneticStoreWriteProperties of the AWS SDK.",
              javaType = "software.amazon.awssdk.services.timestreamwrite.model.MagneticStoreWriteProperties")
    String MAGNETIC_STORE_WRITE_PROPERTIES = "CamelAwsTimestreamMagneticStoreWriteProperties";
    @Metadata(description = "The column of the query result that gives the time of the records that the scheduled query"
                            + " of createScheduledQuery writes.",
              javaType = "String")
    String TIME_COLUMN = "CamelAwsTimestreamTimeColumn";
    @Metadata(description = "The column of the query result that gives the measure name of the records that the"
                            + " scheduled query of createScheduledQuery writes.",
              javaType = "String")
    String MEASURE_NAME_COLUMN = "CamelAwsTimestreamMeasureColumnName";
    @Metadata(description = "The columns of the query result that are the dimensions of the records that the scheduled"
                            + " query of createScheduledQuery writes, as a List of the DimensionMapping of the AWS SDK.",
              javaType = "List")
    String DIMENSION_MAPPING_LIST = "CamelAwsTimestreamDimensionMappingList";
    @Metadata(description = "How the scheduled query of createScheduledQuery maps the columns of the query result to"
                            + " multi-measure records, as a MultiMeasureMappings of the AWS SDK. Use it or the"
                            + " CamelAwsTimestreamMixedMeasureMappingList header.",
              javaType = "software.amazon.awssdk.services.timestreamquery.model.MultiMeasureMappings")
    String MULTI_MEASURE_MAPPINGS = "CamelAwsTimestreamMultiMeasureMappings";
    @Metadata(description = "How the scheduled query of createScheduledQuery maps the measures of the query result to"
                            + " records, as a List of the MixedMeasureMapping of the AWS SDK. Use it or the"
                            + " CamelAwsTimestreamMultiMeasureMappings header.",
              javaType = "List")
    String MIXED_MEASURE_MAPPING_LIST = "CamelAwsTimestreamMixedMeasureMappingList";
    @Metadata(description = "The name of the scheduled query that createScheduledQuery creates.",
              javaType = "String")
    String SCHEDULED_QUERY_NAME = "CamelAwsTimestreamScheduledQueryName";
    @Metadata(description = "The Amazon Resource Name (ARN) of the scheduled query, for deleteScheduledQuery,"
                            + " executeScheduledQuery, updateScheduledQuery and describeScheduledQuery.",
              javaType = "String")
    String SCHEDULED_QUERY_ARN = "CamelAwsTimestreamScheduledQueryArn";
    @Metadata(description = "The state that updateScheduledQuery sets on the scheduled query: ENABLED or DISABLED, as"
                            + " text.",
              javaType = "String")
    String SCHEDULED_QUERY_STATE = "CamelAwsTimestreamScheduledQueryState";
    @Metadata(description = "The time of the run that executeScheduledQuery starts: Timestream runs the query as if it"
                            + " was invoked at this time.",
              javaType = "java.time.Instant")
    String SCHEDULED_QUERY_INVOCATION_TIME = "CamelAwsTimestreamScheduledQueryInvocationTime";
    @Metadata(description = "The SQL query, for query and prepareQuery, or the query that the scheduled query of"
                            + " createScheduledQuery runs.",
              javaType = "String")
    String QUERY_STRING = "CamelAwsTimestreamQueryString";
    @Metadata(description = "The ID of the query that cancelQuery cancels, as given in the QueryResponse of query.",
              javaType = "String")
    String QUERY_ID = "CamelAwsTimestreamQueryId";
    @Metadata(description = "Whether prepareQuery only validates the query string, without storing the prepared query"
                            + " for later use.",
              javaType = "Boolean")
    String QUERY_VALIDATE_ONLY = "CamelAwsTimestreamQueryValidateOnly";
    @Metadata(description = "The maximum number of rows in one page of the result of query. If not set, Timestream"
                            + " returns as many rows as fit in a page of 1 MB.",
              javaType = "Integer")
    String QUERY_MAX_ROWS = "CamelAwsTimestreamQueryMaxRows";
    @Metadata(description = "The token of a page of a query result. As input, the query operation reads the page of"
                            + " this token; as output, it is the token of the next page, and it is removed after the last"
                            + " page. Set the same query string to read the next page.",
              javaType = "String")
    String QUERY_NEXT_TOKEN = "CamelAwsTimestreamQueryNextToken";
    @Metadata(description = "The maximum number of items that listBatchLoadTasks, listDatabases, listTables and"
                            + " listScheduledQueries return. The response holds one page only, with a nextToken when"
                            + " there are more.",
              javaType = "Integer")
    String MAX_RESULTS = "CamelAwsTimestreamMaxResults";
    @Metadata(description = "The schedule of the scheduled query that createScheduledQuery creates, as a cron or rate"
                            + " expression, for example rate(1 hour).",
              javaType = "String")
    String SCHEDULE_EXPRESSION = "CamelAwsTimestreamScheduleExpression";
    @Metadata(description = "The Amazon Resource Name (ARN) of the Amazon SNS topic that receives the notifications of"
                            + " the scheduled query that createScheduledQuery creates.",
              javaType = "String")
    String NOTIFICATION_TOPIC_ARN = "CamelAwsTimestreamNotificationTopicArn";
    @Metadata(description = "The name of the S3 bucket that the scheduled query of createScheduledQuery writes its"
                            + " error reports to. The other CamelAwsTimestreamErrorReportS3 headers are used only when"
                            + " it is set.",
              javaType = "String")
    String ERROR_REPORT_S3_BUCKET_NAME = "CamelAwsTimestreamErrorReportS3BucketName";
    @Metadata(description = "The prefix of the object keys of the error reports of the scheduled query of"
                            + " createScheduledQuery. Used only with the CamelAwsTimestreamErrorReportS3BucketName"
                            + " header.",
              javaType = "String")
    String ERROR_REPORT_S3_OBJECT_KEY_PREFIX = "CamelAwsTimestreamErrorReportS3ObjectKeyPrefix";
    @Metadata(description = "How S3 encrypts the error reports of the scheduled query of createScheduledQuery: SSE_S3"
                            + " or SSE_KMS, as text. Used only with the CamelAwsTimestreamErrorReportS3BucketName"
                            + " header.",
              javaType = "String")
    String ERROR_REPORT_S3_ENCRYPTION_OPTION = "CamelAwsTimestreamErrorReportS3EncryptionOption";
    @Metadata(description = "The Amazon Resource Name (ARN) of the IAM role that Timestream assumes to run the"
                            + " scheduled query that createScheduledQuery creates.",
              javaType = "String")
    String SCHEDULED_QUERY_EXECUTION_ROLE_ARN = "CamelAwsTimestreamScheduledQueryExecutionRoleArn";
    @Metadata(description = "A token that makes createBatchLoadTask, createScheduledQuery and executeScheduledQuery"
                            + " idempotent: a repeated request with the same token has the same result. If not set, the"
                            + " AWS SDK generates one for each request.",
              javaType = "String")
    String CLIENT_TOKEN = "CamelAwsTimestreamClientToken";
    @Metadata(description = "The AWS KMS key that encrypts at rest the database of createDatabase and updateDatabase,"
                            + " or the scheduled query of createScheduledQuery.",
              javaType = "String")
    String KMS_KEY_ID = "CamelAwsTimestreamKmsKeyId";

}
