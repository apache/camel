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
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Access AWS Athena.
 */
public fun UriDsl.`aws2-athena`(i: Aws2AthenaUriDsl.() -> Unit) {
  Aws2AthenaUriDsl(this).apply(i)
}

@CamelDslMarker
public class Aws2AthenaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("aws2-athena")
  }

  private var label: String = ""

  /**
   * Logical name
   */
  public fun label(label: String) {
    this.label = label
    it.url("$label")
  }

  /**
   * The Athena database to use.
   */
  public fun database(database: String) {
    it.property("database", database)
  }

  /**
   * Milliseconds before the next poll for query execution status. See the section Waiting for Query
   * Completion and Retrying Failed Queries to learn more.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * Milliseconds before the next poll for query execution status. See the section Waiting for Query
   * Completion and Retrying Failed Queries to learn more.
   */
  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
  }

  /**
   * Milliseconds before the first poll for query execution status. See the section Waiting for
   * Query Completion and Retrying Failed Queries to learn more.
   */
  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  /**
   * Milliseconds before the first poll for query execution status. See the section Waiting for
   * Query Completion and Retrying Failed Queries to learn more.
   */
  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  /**
   * Maximum number of times to attempt a query. Set to 1 to disable retries. See the section
   * Waiting for Query Completion and Retrying Failed Queries to learn more.
   */
  public fun maxAttempts(maxAttempts: String) {
    it.property("maxAttempts", maxAttempts)
  }

  /**
   * Maximum number of times to attempt a query. Set to 1 to disable retries. See the section
   * Waiting for Query Completion and Retrying Failed Queries to learn more.
   */
  public fun maxAttempts(maxAttempts: Int) {
    it.property("maxAttempts", maxAttempts.toString())
  }

  /**
   * Max number of results to return for the given operation (if supported by the Athena API
   * endpoint). If not set, will use the Athena API default for the given operation.
   */
  public fun maxResults(maxResults: String) {
    it.property("maxResults", maxResults)
  }

  /**
   * Max number of results to return for the given operation (if supported by the Athena API
   * endpoint). If not set, will use the Athena API default for the given operation.
   */
  public fun maxResults(maxResults: Int) {
    it.property("maxResults", maxResults.toString())
  }

  /**
   * Pagination token to use in the case where the response from the previous request was truncated.
   */
  public fun nextToken(nextToken: String) {
    it.property("nextToken", nextToken)
  }

  /**
   * The Athena API function to call.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * The location in Amazon S3 where query results are stored, such as s3://path/to/query/bucket/.
   * Ensure this value ends with a forward slash.
   */
  public fun outputLocation(outputLocation: String) {
    it.property("outputLocation", outputLocation)
  }

  /**
   * How query results should be returned. One of StreamList (default - return a
   * GetQueryResultsIterable that can page through all results), SelectList (returns at most 1000 rows
   * at a time, plus a NextToken value as a header than can be used for manual pagination of results),
   * S3Pointer (return an S3 path pointing to the results).
   */
  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  /**
   * The unique ID identifying the query execution.
   */
  public fun queryExecutionId(queryExecutionId: String) {
    it.property("queryExecutionId", queryExecutionId)
  }

  /**
   * The SQL query to run. Except for simple queries, prefer setting this as the body of the
   * Exchange or as a header using Athena2Constants.QUERY_STRING to avoid having to deal with URL
   * encoding issues.
   */
  public fun queryString(queryString: String) {
    it.property("queryString", queryString)
  }

  /**
   * The region in which Athena client needs to work. When using this parameter, the configuration
   * will expect the lowercase name of the region (for example ap-east-1).
   */
  public fun region(region: String) {
    it.property("region", region)
  }

  /**
   * Reset the waitTimeout countdown in the event of a query retry. If set to true, potential max
   * time spent waiting for queries is equal to waitTimeout x maxAttempts. See the section Waiting for
   * Query Completion and Retrying Failed Queries to learn more.
   */
  public fun resetWaitTimeoutOnRetry(resetWaitTimeoutOnRetry: String) {
    it.property("resetWaitTimeoutOnRetry", resetWaitTimeoutOnRetry)
  }

  /**
   * Reset the waitTimeout countdown in the event of a query retry. If set to true, potential max
   * time spent waiting for queries is equal to waitTimeout x maxAttempts. See the section Waiting for
   * Query Completion and Retrying Failed Queries to learn more.
   */
  public fun resetWaitTimeoutOnRetry(resetWaitTimeoutOnRetry: Boolean) {
    it.property("resetWaitTimeoutOnRetry", resetWaitTimeoutOnRetry.toString())
  }

  /**
   * Optional comma separated list of error types to retry the query for. Use: 'retryable' to retry
   * all retryable failure conditions (e.g. generic errors and resources exhausted), 'generic' to retry
   * 'GENERIC_INTERNAL_ERROR' failures, 'exhausted' to retry queries that have exhausted resource
   * limits, 'always' to always retry regardless of failure condition, or 'never' or null to never
   * retry (default). See the section Waiting for Query Completion and Retrying Failed Queries to learn
   * more.
   */
  public fun retry(retry: String) {
    it.property("retry", retry)
  }

  /**
   * Optional max wait time in millis to wait for a successful query completion. See the section
   * Waiting for Query Completion and Retrying Failed Queries to learn more.
   */
  public fun waitTimeout(waitTimeout: String) {
    it.property("waitTimeout", waitTimeout)
  }

  /**
   * Optional max wait time in millis to wait for a successful query completion. See the section
   * Waiting for Query Completion and Retrying Failed Queries to learn more.
   */
  public fun waitTimeout(waitTimeout: Int) {
    it.property("waitTimeout", waitTimeout.toString())
  }

  /**
   * The workgroup to use for running the query.
   */
  public fun workGroup(workGroup: String) {
    it.property("workGroup", workGroup)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * The AmazonAthena instance to use as the client.
   */
  public fun amazonAthenaClient(amazonAthenaClient: String) {
    it.property("amazonAthenaClient", amazonAthenaClient)
  }

  /**
   * A unique string to ensure issues queries are idempotent. It is unlikely you will need to set
   * this.
   */
  public fun clientRequestToken(clientRequestToken: String) {
    it.property("clientRequestToken", clientRequestToken)
  }

  /**
   * Include useful trace information at the beginning of queries as an SQL comment (prefixed
   * with --).
   */
  public fun includeTrace(includeTrace: String) {
    it.property("includeTrace", includeTrace)
  }

  /**
   * Include useful trace information at the beginning of queries as an SQL comment (prefixed
   * with --).
   */
  public fun includeTrace(includeTrace: Boolean) {
    it.property("includeTrace", includeTrace.toString())
  }

  /**
   * To define a proxy host when instantiating the Athena client.
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * To define a proxy port when instantiating the Athena client.
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * To define a proxy port when instantiating the Athena client.
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * To define a proxy protocol when instantiating the Athena client.
   */
  public fun proxyProtocol(proxyProtocol: String) {
    it.property("proxyProtocol", proxyProtocol)
  }

  /**
   * Amazon AWS Access Key.
   */
  public fun accessKey(accessKey: String) {
    it.property("accessKey", accessKey)
  }

  /**
   * The encryption type to use when storing query results in S3. One of SSE_S3, SSE_KMS, or
   * CSE_KMS.
   */
  public fun encryptionOption(encryptionOption: String) {
    it.property("encryptionOption", encryptionOption)
  }

  /**
   * For SSE-KMS and CSE-KMS, this is the KMS key ARN or ID.
   */
  public fun kmsKey(kmsKey: String) {
    it.property("kmsKey", kmsKey)
  }

  /**
   * If using a profile credentials provider, this parameter will set the profile name
   */
  public fun profileCredentialsName(profileCredentialsName: String) {
    it.property("profileCredentialsName", profileCredentialsName)
  }

  /**
   * Amazon AWS Secret Key.
   */
  public fun secretKey(secretKey: String) {
    it.property("secretKey", secretKey)
  }

  /**
   * Amazon AWS Session Token used when the user needs to assume an IAM role
   */
  public fun sessionToken(sessionToken: String) {
    it.property("sessionToken", sessionToken)
  }

  /**
   * Set whether the Athena client should expect to load credentials through a default credentials
   * provider or to expect static credentials to be passed in
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: String) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider)
  }

  /**
   * Set whether the Athena client should expect to load credentials through a default credentials
   * provider or to expect static credentials to be passed in
   */
  public fun useDefaultCredentialsProvider(useDefaultCredentialsProvider: Boolean) {
    it.property("useDefaultCredentialsProvider", useDefaultCredentialsProvider.toString())
  }

  /**
   * Set whether the Athena client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: String) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider)
  }

  /**
   * Set whether the Athena client should expect to load credentials through a profile credentials
   * provider.
   */
  public fun useProfileCredentialsProvider(useProfileCredentialsProvider: Boolean) {
    it.property("useProfileCredentialsProvider", useProfileCredentialsProvider.toString())
  }

  /**
   * Set whether the Athena client should expect to use Session Credentials. This is useful in a
   * situation in which the user needs to assume an IAM role for doing operations in Athena.
   */
  public fun useSessionCredentials(useSessionCredentials: String) {
    it.property("useSessionCredentials", useSessionCredentials)
  }

  /**
   * Set whether the Athena client should expect to use Session Credentials. This is useful in a
   * situation in which the user needs to assume an IAM role for doing operations in Athena.
   */
  public fun useSessionCredentials(useSessionCredentials: Boolean) {
    it.property("useSessionCredentials", useSessionCredentials.toString())
  }
}
