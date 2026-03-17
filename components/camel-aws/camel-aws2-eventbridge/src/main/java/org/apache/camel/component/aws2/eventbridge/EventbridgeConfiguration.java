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
package org.apache.camel.component.aws2.eventbridge;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

@UriParams
public class EventbridgeConfiguration implements Cloneable, AwsCommonConfiguration {

    private String eventbusName = "default";
    @UriParam
    @Metadata(label = "advanced", autowired = true)
    private EventBridgeClient eventbridgeClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "security", secret = true)
    private String sessionToken;
    @UriParam
    @Metadata(required = true, defaultValue = "putRule")
    private EventbridgeOperations operation = EventbridgeOperations.putRule;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global")
    private String region;
    @UriParam
    private boolean pojoRequest;
    @UriParam(label = "security")
    private boolean trustAllCertificates;
    @UriParam
    @Metadata(supportFileReference = true)
    private String eventPatternFile;
    @UriParam
    private boolean overrideEndpoint;
    @UriParam
    private String uriEndpointOverride;
    @UriParam(label = "security")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security")
    private boolean useSessionCredentials;
    @UriParam(label = "security")
    private String profileCredentialsName;
    @UriParam(label = "consumer",
              description = "The EventBridge rule name to consume events from. Required for consumer.")
    private String ruleName;
    @UriParam(label = "consumer",
              description = "The URL of an existing SQS queue to use as EventBridge target. "
                            + "If not specified, a queue is auto-created when autoCreateQueue is true.")
    private String queueUrl;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether to auto-create an SQS queue and wire it as an EventBridge rule target.")
    private boolean autoCreateQueue = true;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether to delete the auto-created SQS queue and remove the EventBridge target on shutdown.")
    private boolean deleteQueueOnShutdown = true;
    @UriParam(label = "consumer", defaultValue = "10",
              description = "The maximum number of messages to receive per poll from SQS.")
    private int maxMessagesPerPoll = 10;
    @UriParam(label = "consumer", defaultValue = "20",
              description = "The duration (in seconds) for which the SQS receive call waits for messages (long polling).")
    private int waitTimeSeconds = 20;
    @UriParam(label = "consumer", defaultValue = "30",
              description = "The duration (in seconds) that received SQS messages are hidden from subsequent receive requests.")
    private int visibilityTimeout = 30;

    public EventBridgeClient getEventbridgeClient() {
        return eventbridgeClient;
    }

    /**
     * To use an existing configured AWS Eventbridge client
     */
    public void setEventbridgeClient(EventBridgeClient eventbridgeClient) {
        this.eventbridgeClient = eventbridgeClient;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key
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

    public EventbridgeOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(EventbridgeOperations operation) {
        this.operation = operation;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the Eventbridge client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the Eventbridge client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the Eventbridge client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which the Eventbridge client needs to work. When using this parameter, the configuration will
     * expect the lowercase name of the region (for example, ap-east-1) You'll need to use the name
     * Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * If we want to use a POJO request as body or not
     */
    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
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

    public String getEventPatternFile() {
        return eventPatternFile;
    }

    /**
     * EventPattern File
     */
    public void setEventPatternFile(String eventPatternFile) {
        this.eventPatternFile = eventPatternFile;
    }

    public String getEventbusName() {
        return eventbusName;
    }

    /**
     * The eventbus name, the default value is default, and this means it will be the AWS event bus of your account.
     */
    public void setEventbusName(String eventbusName) {
        this.eventbusName = eventbusName;
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

    /**
     * Set whether the Eventbridge client should expect to load credentials through a default credentials provider or to
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
     * Set whether the Eventbridge client should expect to load credentials through a profile credentials provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    /**
     * Set whether the Eventbridge client should expect to use Session Credentials. This is useful in a situation in
     * which the user needs to assume an IAM role for doing operations in Eventbridge.
     */
    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    /**
     * If using a profile credentials provider this parameter will set the profile name
     */
    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    public String getRuleName() {
        return ruleName;
    }

    /**
     * The EventBridge rule name to consume events from
     */
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getQueueUrl() {
        return queueUrl;
    }

    /**
     * The URL of an existing SQS queue to use as EventBridge target
     */
    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }

    public boolean isAutoCreateQueue() {
        return autoCreateQueue;
    }

    /**
     * Whether to auto-create an SQS queue and wire it as an EventBridge rule target
     */
    public void setAutoCreateQueue(boolean autoCreateQueue) {
        this.autoCreateQueue = autoCreateQueue;
    }

    public boolean isDeleteQueueOnShutdown() {
        return deleteQueueOnShutdown;
    }

    /**
     * Whether to delete the auto-created SQS queue and remove the EventBridge target on shutdown
     */
    public void setDeleteQueueOnShutdown(boolean deleteQueueOnShutdown) {
        this.deleteQueueOnShutdown = deleteQueueOnShutdown;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * The maximum number of messages to receive per poll from SQS
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public int getWaitTimeSeconds() {
        return waitTimeSeconds;
    }

    /**
     * The duration (in seconds) for which the SQS receive call waits for messages (long polling)
     */
    public void setWaitTimeSeconds(int waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }

    public int getVisibilityTimeout() {
        return visibilityTimeout;
    }

    /**
     * The duration (in seconds) that received SQS messages are hidden from subsequent receive requests
     */
    public void setVisibilityTimeout(int visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

    // *************************************************
    //
    // *************************************************

    public EventbridgeConfiguration copy() {
        try {
            return (EventbridgeConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
