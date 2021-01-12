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

/**
 * Constants used in Camel AWS Athena module SDK v2.
 */
public interface Athena2Constants {

    // common
    String OPERATION = "CamelAwsAthenaOperation";
    String DATABASE = "CamelAwsAthenaDatabase";
    String QUERY_EXECUTION_ID = "CamelAwsAthenaQueryExecutionId";
    String WORK_GROUP = "CamelAwsAthenaWorkGroup";
    String NEXT_TOKEN = "CamelAwsAthenaNextToken";
    String MAX_RESULTS = "CamelAwsAthenaMaxResults";
    String INCLUDE_TRACE = "CamelAwsAthenaIncludeTrace";
    String OUTPUT_LOCATION = "CamelAwsAthenaOutputLocation";
    String OUTPUT_TYPE = "CamelAwsAthenaOutputType";
    String QUERY_EXECUTION_STATE = "CamelAwsAthenaQueryExecutionState"; // read only

    // startQueryExecution
    String CLIENT_REQUEST_TOKEN = "CamelAwsAthenaClientRequestToken";
    String QUERY_STRING = "CamelAwsAthenaQueryString";
    String ENCRYPTION_OPTION = "CamelAwsAthenaEncryptionOption";
    String KMS_KEY = "CamelAwsAthenaKmsKey";

    String WAIT_TIMEOUT = "CamelAwsAthenaWaitTimeout";
    String INITIAL_DELAY = "CamelAwsAthenaInitialDelay";
    String DELAY = "CamelAwsAthenaDelay";

    String MAX_ATTEMPTS = "CamelAwsAthenaMaxAttempts";
    String RETRY = "CamelAwsAthenaRetry";
    String RESET_WAIT_TIMEOUT_ON_RETRY = "CamelAwsAthenaResetWaitTimeoutOnRetry";
    String START_QUERY_EXECUTION_ATTEMPTS = "CamelAwsAthenaStartQueryExecutionAttempts"; // read only
    String START_QUERY_EXECUTION_ELAPSED_MILLIS = "CamelAwsAthenaStartQueryExecutionElapsedMillis"; // read only

}
