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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.EncryptionOption;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

@UriParams
public class Athena2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;

    @UriParam(defaultValue = "startQueryExecution",
              description = "The Athena API function to call.")
    private Athena2Operations operation = Athena2Operations.startQueryExecution;
    @UriParam(description = "The Athena database to use.")
    private String database;
    @UriParam(description = "The unique ID identifying the query execution.")
    private String queryExecutionId;
    @UriParam(description = "The workgroup to use for running the query.")
    private String workGroup;
    @UriParam(description = "Pagination token to use in the case where the response from the previous request was truncated.")
    private String nextToken;
    @UriParam(description = "Max number of results to return for the given operation (if supported by the Athena API endpoint). If not set, will use the Athena API default for the given operation.")
    private Integer maxResults;
    @UriParam(label = "advanced",
              description = "Include useful trace information at the beginning of queries as an SQL comment (prefixed with --).")
    private boolean includeTrace;
    @UriParam(description = "The location in Amazon S3 where query results are stored, such as s3://path/to/query/bucket/. Ensure this value ends with a forward slash.")
    private String outputLocation;
    @UriParam(defaultValue = "StreamList",
              description = "How query results should be returned. One of "
                            + "StreamList (default - return a GetQueryResultsIterable that can page through all results), "
                            + "SelectList (returns at most 1000 rows at a time, plus a NextToken value as a header than can be used for manual pagination of results), "
                            + "S3Pointer (return an S3 path pointing to the results).")
    private Athena2OutputType outputType = Athena2OutputType.StreamList;
    @UriParam(label = "advanced",
              description = "A unique string to ensure issues queries are idempotent. It is unlikely you will need to set this.")
    private String clientRequestToken;
    @UriParam(description = "The SQL query to run. Except for simple queries, prefer setting this as the body of the "
                            + "Exchange or as a header using Athena2Constants.QUERY_STRING to avoid having to deal with URL encoding issues.")
    private String queryString;
    @UriParam(label = "security",
              description = "The encryption type to use when storing query results in S3. One of SSE_S3, SSE_KMS, or CSE_KMS.")
    private EncryptionOption encryptionOption;
    @UriParam(label = "security",
              description = "For SSE-KMS and CSE-KMS, this is the KMS key ARN or ID.")
    private String kmsKey;
    @UriParam(defaultValue = "0",
              description = "Optional max wait time in millis to wait for a successful query completion."
                            + " See the section Waiting for Query Completion and Retrying Failed Queries to learn more.")
    private long waitTimeout;
    @UriParam(defaultValue = "1000",
              description = "Milliseconds before the first poll for query execution status."
                            + " See the section Waiting for Query Completion and Retrying Failed Queries to learn more.")
    private long initialDelay = 1000;
    @UriParam(defaultValue = "2000",
              description = "Milliseconds before the next poll for query execution status."
                            + " See the section Waiting for Query Completion and Retrying Failed Queries to learn more.")
    private long delay = 2000;
    @UriParam(label = "producer",
              defaultValue = "1",
              description = "Maximum number of times to attempt a query. Set to 1 to disable retries."
                            + " See the section Waiting for Query Completion and Retrying Failed Queries to learn more.")
    private int maxAttempts = 1;
    @UriParam(label = "producer",
              defaultValue = "never",
              description = "Optional comma separated list of error types to retry the query for. Use: "
                            + "'retryable' to retry all retryable failure conditions (e.g. generic errors and resources exhausted), "
                            + "'generic' to retry 'GENERIC_INTERNAL_ERROR' failures, "
                            + "'exhausted' to retry queries that have exhausted resource limits, "
                            + "'always' to always retry regardless of failure condition, or "
                            + "'never' or null to never retry (default). "
                            + "See the section Waiting for Query Completion and Retrying Failed Queries to learn more.",
              enums = "never,always,retryable,exhausted,generic")
    private String retry;
    @UriParam(label = "producer",
              defaultValue = "true",
              description = "Reset the waitTimeout countdown in the event of a query retry."
                            + " If set to true, potential max time spent waiting for queries is equal to waitTimeout x maxAttempts."
                            + " See the section Waiting for Query Completion and Retrying Failed Queries to learn more.")
    private boolean resetWaitTimeoutOnRetry = true;
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private AthenaClient amazonAthenaClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "security", secret = true)
    private String sessionToken;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global",
              description = "The region in which Athena client needs to work. When using this parameter, the configuration will expect the"
                            + " lowercase name of the region (for example ap-east-1).")
    private String region;
    @UriParam(label = "security",
              description = "Set whether the Athena client should expect to load credentials through a default credentials provider"
                            + " or to expect static credentials to be passed in")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security", defaultValue = "false")
    private boolean useProfileCredentialsProvider;

    @UriParam(label = "security")
    private boolean useSessionCredentials;

    @UriParam(label = "security")
    private String profileCredentialsName;

    @UriParam(label = "security")
    private boolean trustAllCertificates;
    @UriParam
    private boolean overrideEndpoint;
    @UriParam
    private String uriEndpointOverride;

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key.
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key.
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * Amazon AWS Session Token used when the user needs to assume an IAM role
     */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public AthenaClient getAmazonAthenaClient() {
        return amazonAthenaClient;
    }

    /**
     * The AmazonAthena instance to use as the client.
     */
    public void setAmazonAthenaClient(AthenaClient amazonAthenaClient) {
        this.amazonAthenaClient = amazonAthenaClient;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the Athena client.
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the Athena client.
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the Athena client.
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which the Athena client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example, ap-east-1). You'll need to use the name Region.EU_WEST_1.id().
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public String getOutputLocation() {
        return outputLocation;
    }

    /**
     * The location in Amazon S3 where query results are stored, such as {@code s3://path/to/query/bucket/}. Ensure this
     * value ends with a forward slash ('/').
     */
    public void setOutputLocation(String outputLocation) {
        this.outputLocation = outputLocation;
    }

    public Athena2OutputType getOutputType() {
        return outputType;
    }

    /**
     * How query results should be returned. One of {@code StreamList} (default - return a
     * {@link GetQueryResultsIterable} that can page through all results), {@code SelectList} (returns at most 1,000
     * rows at a time, plus a NextToken value as a header than can be used for manual pagination of results),
     * {@code S3Pointer} (return an S3 path pointing to the results).
     */
    public void setOutputType(Athena2OutputType outputType) {
        this.outputType = outputType;
    }

    public Athena2Operations getOperation() {
        return operation;
    }

    /**
     * The Athena API function to call.
     */
    public void setOperation(Athena2Operations operation) {
        this.operation = operation;
    }

    public String getDatabase() {
        return database;
    }

    /**
     * The Athena database to use.
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    public String getQueryExecutionId() {
        return queryExecutionId;
    }

    /**
     * The unique ID identifying the query execution.
     */
    public void setQueryExecutionId(String queryExecutionId) {
        this.queryExecutionId = queryExecutionId;
    }

    public String getWorkGroup() {
        return workGroup;
    }

    /**
     * The workgroup to use for running the query.
     */
    public void setWorkGroup(String workGroup) {
        this.workGroup = workGroup;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    /**
     * Max number of results to return for the given operation (if supported by the Athena API endpoint). If not set, it
     * will use the Athena API default for the given operation.
     */
    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public String getNextToken() {
        return nextToken;
    }

    /**
     * Pagination token to use in the case where the response from the previous request was truncated.
     */
    public void setNextToken(String nextToken) {
        this.nextToken = nextToken;
    }

    public String getClientRequestToken() {
        return clientRequestToken;
    }

    /**
     * A unique string to ensure issues queries are idempotent. It is unlikely you will need to set this.
     */
    public void setClientRequestToken(String clientRequestToken) {
        this.clientRequestToken = clientRequestToken;
    }

    public EncryptionOption getEncryptionOption() {
        return encryptionOption;
    }

    /**
     * The encryption type to use when storing query results in S3. One of {@code SSE_S3}, {@code SSE_KMS}, or
     * {@code CSE_KMS}.
     */
    public void setEncryptionOption(EncryptionOption encryptionOption) {
        this.encryptionOption = encryptionOption;
    }

    public String getKmsKey() {
        return kmsKey;
    }

    /**
     * For SSE-KMS and CSE-KMS, this is the KMS key ARN or ID.
     */
    public void setKmsKey(String kmsKey) {
        this.kmsKey = kmsKey;
    }

    public String getQueryString() {
        return queryString;
    }

    /**
     * The SQL query to run. Except for simple queries, prefer setting this as the {@code body} of the Exchange or as a
     * header using {@link Athena2Constants#QUERY_STRING} to avoid having to deal with URL encoding issues.
     */
    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    /**
     * Milliseconds before the first poll for query execution status. See the section 'Waiting for Query Completion and
     * Retrying Failed Queries' to learn more.
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * Milliseconds before the next poll for query execution status. See the section 'Waiting for Query Completion and
     * Retrying Failed Queries' to learn more.
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    public long getWaitTimeout() {
        return waitTimeout;
    }

    /**
     * Optional max wait time in millis to wait for a successful query completion. See the section 'Waiting for Query
     * Completion and Retrying Failed Queries' to learn more.
     */
    public void setWaitTimeout(long waitTimeout) {
        this.waitTimeout = waitTimeout;
    }

    public String getRetry() {
        return retry;
    }

    /**
     * Optional comma separated list of error types to retry the query for. Use "retryable" to retry all retryable
     * failure conditions (e.g. generic errors and resources exhausted), "generic" to retry "GENERIC_INTERNAL_ERROR"
     * failures, "exhausted" to retry queries that have exhausted resource limits, "always" to always retry regardless
     * of failure condition, or "never" or null to never retry (default). See the section 'Waiting for Query Completion
     * and Retrying Failed Queries' to learn more.
     */
    public void setRetry(String retry) {
        this.retry = retry;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Maximum number of times to attempt a query. Set to 1 to disable retries. See the section 'Waiting for Query
     * Completion and Retrying Failed Queries' to learn more.
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isResetWaitTimeoutOnRetry() {
        return resetWaitTimeoutOnRetry;
    }

    /**
     * Reset the waitTimeout countdown in the event of a query retry. If set to true, potential max time spent waiting
     * for queries is equal to waitTimeout x maxAttempts. See the section 'Waiting for Query Completion and Retrying
     * Failed Queries' to learn more.
     */
    public void setResetWaitTimeoutOnRetry(boolean resetWaitTimeoutOnRetry) {
        this.resetWaitTimeoutOnRetry = resetWaitTimeoutOnRetry;
    }

    public boolean isIncludeTrace() {
        return includeTrace;
    }

    /**
     * Include useful trace information at the beginning of queries as an SQL comment (prefixed with "--").
     */
    public void setIncludeTrace(boolean includeTrace) {
        this.includeTrace = includeTrace;
    }

    /**
     * Set whether the Athena client should expect to load credentials through a default credentials provider or to
     * expect static credentials to be passed in.
     */
    public void setUseDefaultCredentialsProvider(Boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    /**
     * Set whether the Athena client should expect to load credentials through a profile credentials provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    /**
     * Set whether the Athena client should expect to use Session Credentials. This is useful in a situation in which
     * the user needs to assume an IAM role for doing operations in Athena.
     */
    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    /**
     * If using a profile credentials provider, this parameter will set the profile name
     */
    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    /**
     * If we want to trust all certificates in case of overriding the endpoint
     */
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    /**
     * Set the need for overriding the endpoint. This option needs to be used in combination with the
     * uriEndpointOverride option
     */
    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

    public String getUriEndpointOverride() {
        return uriEndpointOverride;
    }

    /**
     * Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option
     */
    public void setUriEndpointOverride(String uriEndpointOverride) {
        this.uriEndpointOverride = uriEndpointOverride;
    }

    // *************************************************
    //
    // *************************************************

    public Athena2Configuration copy() {
        try {
            return (Athena2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
