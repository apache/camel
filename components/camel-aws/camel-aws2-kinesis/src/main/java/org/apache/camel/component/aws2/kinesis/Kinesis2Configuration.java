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
package org.apache.camel.component.aws2.kinesis;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

@UriParams
public class Kinesis2Configuration implements Cloneable, AwsCommonConfiguration {

    @UriPath(description = "Name of the stream")
    @Metadata(required = true)
    private String streamName;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Access Key")
    private String accessKey;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Secret Key")
    private String secretKey;
    @UriParam(label = "security", secret = true,
              description = "Amazon AWS Session Token used when the user needs to assume a IAM role")
    private String sessionToken;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global",
              description = "The region in which Kinesis Firehose client needs to work. When using this parameter, the configuration will expect the lowercase name of the "
                            + "region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()")
    private String region;
    @UriParam(description = "Amazon Kinesis client to use for all requests for this endpoint")
    @Metadata(label = "advanced", autowired = true)
    private KinesisClient amazonKinesisClient;
    @UriParam(label = "consumer", description = "Maximum number of records that will be fetched in each poll",
              defaultValue = "1")
    private int maxResultsPerRequest = 1;
    @UriParam(label = "consumer", description = "Defines where in the Kinesis stream to start getting records",
              defaultValue = "TRIM_HORIZON")
    private ShardIteratorType iteratorType = ShardIteratorType.TRIM_HORIZON;
    @UriParam(label = "consumer", description = "Defines which shardId in the Kinesis stream to get records from")
    private String shardId = "";
    @UriParam(label = "consumer",
              description = "The sequence number to start polling from. Required if iteratorType is set to AFTER_SEQUENCE_NUMBER or AT_SEQUENCE_NUMBER")
    private String sequenceNumber = "";
    @UriParam(label = "consumer",
              description = "The message timestamp to start polling from. Required if iteratorType is set to AT_TIMESTAMP")
    private String messageTimestamp = "";
    @UriParam(label = "consumer", defaultValue = "ignore",
              description = "Define what will be the behavior in case of shard closed. Possible value are ignore, silent and fail."
                            + " In case of ignore a WARN message will be logged once and the consumer will not process new messages until restarted,"
                            + "in case of silent there will be no logging and the consumer will not process new messages until restarted,"
                            + "in case of fail a ReachedClosedStateException will be thrown")
    private Kinesis2ShardClosedStrategyEnum shardClosed;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "To define a proxy protocol when instantiating the Kinesis client")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy", description = "To define a proxy host when instantiating the Kinesis client")
    private String proxyHost;
    @UriParam(label = "proxy", description = "To define a proxy port when instantiating the Kinesis client")
    private Integer proxyPort;
    @UriParam(label = "security", description = "If we want to trust all certificates in case of overriding the endpoint")
    private boolean trustAllCertificates;
    @UriParam(label = "advanced",
              description = "If we want to a KinesisAsyncClient instance set it to true")
    private boolean asyncClient;
    @UriParam(label = "common", defaultValue = "true",
              description = "This option will set the CBOR_ENABLED property during the execution")
    private boolean cborEnabled = true;
    @UriParam(label = "common", defaultValue = "false",
              description = "Set the need for overriding the endpoint. This option needs to be used in combination with uriEndpointOverride"
                            + " option")
    private boolean overrideEndpoint;
    @UriParam(label = "common",
              description = "Set the overriding uri endpoint. This option needs to be used in combination with overrideEndpoint option")
    private String uriEndpointOverride;
    @UriParam(label = "security",
              description = "Set whether the Kinesis client should expect to load credentials through a default credentials provider or to expect"
                            + " static credentials to be passed in.")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security",
              description = "Set whether the Kinesis client should expect to load credentials through a profile credentials provider.")
    private boolean useProfileCredentialsProvider;

    @UriParam(label = "security",
              description = "Set whether the Kinesis client should expect to use Session Credentials. This is useful in situation in which the user"
                            +
                            " needs to assume a IAM role for doing operations in Kinesis.")
    private boolean useSessionCredentials;
    @UriParam(label = "security",
              description = "If using a profile credentials provider this parameter will set the profile name.")
    private String profileCredentialsName;
    @UriParam(label = "consumer,advanced", description = "The interval in milliseconds to wait between shard polling",
              defaultValue = "10000")
    private long shardMonitorInterval = 10000;

    // KCL specific parameters
    @UriParam(label = "advanced",
              description = "If we want to a KCL Consumer set it to true")
    private boolean useKclConsumers;
    @UriParam(label = "advanced",
              description = "If we want to a KCL Consumer, we can pass an instance of DynamoDbAsyncClient")
    private DynamoDbAsyncClient dynamoDbAsyncClient;
    @UriParam(label = "advanced",
              description = "If we want to a KCL Consumer, we can pass an instance of CloudWatchAsyncClient")
    private CloudWatchAsyncClient cloudWatchAsyncClient;
    @UriParam(label = "advanced",
              description = "If we want to use a KCL Consumer and disable the CloudWatch Metrics Export")
    private boolean kclDisableCloudwatchMetricsExport;
    @UriParam(description = "Supply a pre-constructed Amazon Kinesis async client to use for the KCL Consumer")
    @Metadata(label = "advanced", autowired = true)
    private KinesisAsyncClient amazonKinesisAsyncClient;
    @UriParam(description = "Name of the KCL application. This defaults to the stream name.")
    @Metadata(label = "advanced")
    private String applicationName;

    public KinesisAsyncClient getAmazonKinesisAsyncClient() {
        return amazonKinesisAsyncClient;
    }

    public void setAmazonKinesisAsyncClient(KinesisAsyncClient amazonKinesisAsyncClient) {
        this.amazonKinesisAsyncClient = amazonKinesisAsyncClient;
    }

    public KinesisClient getAmazonKinesisClient() {
        return amazonKinesisClient;
    }

    public void setAmazonKinesisClient(KinesisClient amazonKinesisClient) {
        this.amazonKinesisClient = amazonKinesisClient;
    }

    public int getMaxResultsPerRequest() {
        return maxResultsPerRequest;
    }

    public void setMaxResultsPerRequest(int maxResultsPerRequest) {
        this.maxResultsPerRequest = maxResultsPerRequest;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public ShardIteratorType getIteratorType() {
        return iteratorType;
    }

    public void setIteratorType(ShardIteratorType iteratorType) {
        this.iteratorType = iteratorType;
    }

    public String getShardId() {
        return shardId;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }

    public String getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(String sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getMessageTimestamp() {
        return messageTimestamp;
    }

    public void setMessageTimestamp(String messageTimestamp) {
        this.messageTimestamp = messageTimestamp;
    }

    public Kinesis2ShardClosedStrategyEnum getShardClosed() {
        return shardClosed;
    }

    public void setShardClosed(Kinesis2ShardClosedStrategyEnum shardClosed) {
        this.shardClosed = shardClosed;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public boolean isCborEnabled() {
        return cborEnabled;
    }

    public void setCborEnabled(boolean cborEnabled) {
        this.cborEnabled = cborEnabled;
    }

    public boolean isOverrideEndpoint() {
        return overrideEndpoint;
    }

    public void setOverrideEndpoint(boolean overrideEndpoint) {
        this.overrideEndpoint = overrideEndpoint;
    }

    public String getUriEndpointOverride() {
        return uriEndpointOverride;
    }

    public void setUriEndpointOverride(String uriEndpointOverride) {
        this.uriEndpointOverride = uriEndpointOverride;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    public void setUseDefaultCredentialsProvider(boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    public boolean isAsyncClient() {
        return asyncClient;
    }

    public void setAsyncClient(boolean asyncClient) {
        this.asyncClient = asyncClient;
    }

    public long getShardMonitorInterval() {
        return shardMonitorInterval;
    }

    public void setShardMonitorInterval(long shardMonitorInterval) {
        this.shardMonitorInterval = shardMonitorInterval;
    }

    public boolean isUseKclConsumers() {
        return useKclConsumers;
    }

    public void setUseKclConsumers(boolean useKclConsumers) {
        this.useKclConsumers = useKclConsumers;
    }

    public DynamoDbAsyncClient getDynamoDbAsyncClient() {
        return dynamoDbAsyncClient;
    }

    public void setDynamoDbAsyncClient(DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    public CloudWatchAsyncClient getCloudWatchAsyncClient() {
        return cloudWatchAsyncClient;
    }

    public void setCloudWatchAsyncClient(CloudWatchAsyncClient cloudWatchAsyncClient) {
        this.cloudWatchAsyncClient = cloudWatchAsyncClient;
    }

    public boolean isKclDisableCloudwatchMetricsExport() {
        return kclDisableCloudwatchMetricsExport;
    }

    public void setKclDisableCloudwatchMetricsExport(boolean kclDisableCloudwatchMetricsExport) {
        this.kclDisableCloudwatchMetricsExport = kclDisableCloudwatchMetricsExport;
    }

    // *************************************************
    //
    // *************************************************
    public Kinesis2Configuration copy() {
        try {
            return (Kinesis2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
