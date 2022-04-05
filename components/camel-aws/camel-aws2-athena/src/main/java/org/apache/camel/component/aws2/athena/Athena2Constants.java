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
package org.apache.camel.component.aws2.athena;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Athena module SDK v2.
 */
public interface Athena2Constants {

    // common
    @Metadata(label = "all",
              description = "The operation to perform. Permitted values are getQueryExecution, getQueryResults, listQueryExecutions, startQueryExecution.",
              javaType = "org.apache.camel.component.aws2.athena.Athena2Operations", defaultValue = "startQueryExecution")
    String OPERATION = "CamelAwsAthenaOperation";
    @Metadata(label = "startQueryExecution", description = "The Athena database to use.", javaType = "String")
    String DATABASE = "CamelAwsAthenaDatabase";
    @Metadata(label = "getQueryExecution getQueryResults startQueryExecution",
              description = "The unique ID identifying the query execution.", javaType = "String")
    String QUERY_EXECUTION_ID = "CamelAwsAthenaQueryExecutionId";
    @Metadata(label = "listQueryExecutions startQueryExecution", description = "The workgroup to use for running the query.",
              javaType = "String")
    String WORK_GROUP = "CamelAwsAthenaWorkGroup";
    @Metadata(label = "getQueryResults listQueryExecutions",
              description = "Pagination token to use in the case where the response from the previous request was truncated.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsAthenaNextToken";
    @Metadata(label = "getQueryResults listQueryExecutions",
              description = "Max number of results to return for the given operation (if supported by the Athena API endpoint).\n"
                            +
                            "If not set, will use the Athena API default for the given operation.",
              javaType = "Integer")
    String MAX_RESULTS = "CamelAwsAthenaMaxResults";
    @Metadata(label = "startQueryExecution",
              description = "Include useful trace information at the beginning of queries as an SQL comment (prefixed with \"--\").",
              javaType = "boolean")
    String INCLUDE_TRACE = "CamelAwsAthenaIncludeTrace";
    @Metadata(label = "getQueryExecution getQueryResults startQueryExecution",
              description = "The location in Amazon S3 where query results are stored, such as s3://path/to/query/bucket/.\n" +
                            "Ensure this value ends with a forward slash ('/').",
              javaType = "String")
    String OUTPUT_LOCATION = "CamelAwsAthenaOutputLocation";
    @Metadata(label = "getQueryResults", description = "How query results should be returned.  One of\n" +
                                                       "StreamList (default - return a GetQueryResultsIterable that can page through all results),\n"
                                                       +
                                                       "SelectList (returns at most 1,000 rows at a time, plus a NextToken value as a header than can be used for manual pagination of results),\n"
                                                       +
                                                       "S3Pointer (return an S3 path pointing to the results).",
              javaType = "org.apache.camel.component.aws2.athena.Athena2OutputType")
    String OUTPUT_TYPE = "CamelAwsAthenaOutputType";
    @Metadata(label = "getQueryExecution getQueryResults startQueryExecution",
              description = "The state of the query execution.",
              javaType = "software.amazon.awssdk.services.athena.model.QueryExecutionState")
    String QUERY_EXECUTION_STATE = "CamelAwsAthenaQueryExecutionState"; // read only

    // startQueryExecution
    @Metadata(label = "startQueryExecution",
              description = "A unique string to ensure issues queries are idempotent.  It is unlikely you will need to set this.",
              javaType = "String")
    String CLIENT_REQUEST_TOKEN = "CamelAwsAthenaClientRequestToken";
    @Metadata(label = "startQueryExecution",
              description = "The SQL query to run.  Except for simple queries, prefer setting this as the `body` of the\n" +
                            "Exchange or as this header to avoid having to deal with URL encoding issues.",
              javaType = "String")
    String QUERY_STRING = "CamelAwsAthenaQueryString";
    @Metadata(label = "startQueryExecution", description = "The encryption type to use when storing query results in S3.",
              javaType = "software.amazon.awssdk.services.athena.model.EncryptionOption")
    String ENCRYPTION_OPTION = "CamelAwsAthenaEncryptionOption";
    @Metadata(label = "startQueryExecution", description = "For SSE-KMS and CSE-KMS, this is the KMS key ARN or ID.",
              javaType = "String")
    String KMS_KEY = "CamelAwsAthenaKmsKey";
    @Metadata(label = "startQueryExecution",
              description = "Optional max wait time in millis to wait for a successful query completion.\n" +
                            "See the section 'Waiting for Query Completion and Retrying Failed Queries' to learn more.",
              javaType = "long")
    String WAIT_TIMEOUT = "CamelAwsAthenaWaitTimeout";
    @Metadata(label = "startQueryExecution", description = "Milliseconds before the first poll for query execution status.\n" +
                                                           "See the section 'Waiting for Query Completion and Retrying Failed Queries' to learn more.",
              javaType = "long")
    String INITIAL_DELAY = "CamelAwsAthenaInitialDelay";
    @Metadata(label = "startQueryExecution", description = "Milliseconds before the next poll for query execution status.\n" +
                                                           "See the section 'Waiting for Query Completion and Retrying Failed Queries' to learn more.",
              javaType = "long")
    String DELAY = "CamelAwsAthenaDelay";
    @Metadata(label = "startQueryExecution",
              description = "Maximum number of times to attempt a query.  Set to 1 to disable retries.\n" +
                            "See the section 'Waiting for Query Completion and Retrying Failed Queries' to learn more.",
              javaType = "int")
    String MAX_ATTEMPTS = "CamelAwsAthenaMaxAttempts";
    @Metadata(label = "startQueryExecution",
              description = "Optional comma separated list of error types to retry the query for.  Use\n" +
                            "'retryable' to retry all retryable failure conditions (e.g. generic errors and resources exhausted),\n"
                            +
                            "'generic' to retry 'GENERIC_INTERNAL_ERROR' failures,\n" +
                            "'exhausted' to retry queries that have exhausted resource limits,\n" +
                            "'always' to always retry regardless of failure condition, or\n" +
                            "'never' or null to never retry (default).\n" +
                            "See the section 'Waiting for Query Completion and Retrying Failed Queries' to learn more.",
              javaType = "String")
    String RETRY = "CamelAwsAthenaRetry";
    @Metadata(label = "startQueryExecution", description = "Reset the waitTimeout countdown in the event of a query retry.\n" +
                                                           "If set to true, potential max time spent waiting for queries is equal to waitTimeout x maxAttempts.\n"
                                                           +
                                                           "See the section 'Waiting for Query Completion and Retrying Failed Queries' to learn more.",
              javaType = "boolean")
    String RESET_WAIT_TIMEOUT_ON_RETRY = "CamelAwsAthenaResetWaitTimeoutOnRetry";
    @Metadata(label = "startQueryExecution",
              description = "Total number of attempts made to run the query.  Will be greater than 1 if the query is retried.",
              javaType = "int")
    String START_QUERY_EXECUTION_ATTEMPTS = "CamelAwsAthenaStartQueryExecutionAttempts"; // read only
    @Metadata(label = "startQueryExecution",
              description = "Total time in millis taken in startQueryExecution (mostly relevant when waiting for query completion within startQueryExecution).",
              javaType = "long")
    String START_QUERY_EXECUTION_ELAPSED_MILLIS = "CamelAwsAthenaStartQueryExecutionElapsedMillis"; // read only

}
