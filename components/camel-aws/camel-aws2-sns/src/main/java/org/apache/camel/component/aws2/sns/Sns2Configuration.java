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
package org.apache.camel.component.aws2.sns;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.aws.common.AwsCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.sns.SnsClient;

@UriParams
public class Sns2Configuration implements Cloneable, AwsCommonConfiguration {

    private String topicArn;

    // Common properties
    private String topicName;
    @UriParam(label = "advanced",
              description = "To use a custom software.amazon.awssdk.services.sns.SnsClient, which is autowired when the"
                            + " registry has a single one. When this option is set, the credentials, region, proxy, endpoint"
                            + " override and trustAllCertificates options are not used. The endpoint does not close a client"
                            + " that is set or autowired.")
    @Metadata(autowired = true)
    private SnsClient amazonSNSClient;
    @UriParam(label = "security", security = "secret",
              description = "The access key ID of the AWS credentials, used together with the secretKey option. The"
                            + " endpoint requires it, unless amazonSNSClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.")
    private String accessKey;
    @UriParam(label = "security", security = "secret",
              description = "The secret access key of the AWS credentials, used together with the accessKey option. The"
                            + " endpoint requires it, unless amazonSNSClient is set or useDefaultCredentialsProvider,"
                            + " useProfileCredentialsProvider or useSessionCredentials is true.")
    private String secretKey;
    @UriParam(label = "security", security = "secret",
              description = "The session token of temporary AWS credentials, for example of an assumed IAM role. It is used"
                            + " only when useSessionCredentials is true, together with the accessKey and secretKey options."
                            + " When it is not set, the accessKey and secretKey options are used without a session token.")
    private String sessionToken;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS",
              description = "The protocol that the HTTP client of Amazon SNS uses to connect to the proxy. It is used only"
                            + " when the proxyHost and proxyPort options are set.")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy",
              description = "The host name of the proxy that the HTTP client of Amazon SNS connects through. The proxy is"
                            + " used only when the proxyPort option is set too.")
    private String proxyHost;
    @UriParam(label = "proxy",
              description = "The port of the proxy that the HTTP client of Amazon SNS connects through. The proxy is used"
                            + " only when the proxyHost option is set too.")
    private Integer proxyPort;
    @UriParam(description = "The ARN of the Amazon SQS queue that the endpoint subscribes to the topic when it starts, if"
                            + " subscribeSNStoSQS is true. The access policy of the queue must allow the topic to send"
                            + " messages to it.")
    private String queueArn;
    @UriParam(description = "Whether the endpoint subscribes the Amazon SQS queue of the queueArn option to the topic when it"
                            + " starts. The queueArn option is required then.")
    private boolean subscribeSNStoSQS;
    @UriParam(description = "The ID, alias or ARN of the AWS KMS key that encrypts the topic, for example alias/aws/sns for"
                            + " the AWS managed key of Amazon SNS. It is used only when the endpoint creates the topic and"
                            + " serverSideEncryptionEnabled is true, and it is required then.")
    private String kmsMasterKeyId;
    @UriParam(description = "Whether the topic that the endpoint creates, see autoCreateTopic, is encrypted with the AWS KMS"
                            + " key of the kmsMasterKeyId option. It does not change a topic that exists already.")
    private boolean serverSideEncryptionEnabled;
    @UriParam(description = "Whether the endpoint creates the topic when it starts, if no topic with this name exists in the"
                            + " region. A name that ends with .fifo creates a FIFO topic. If false and the topic does not"
                            + " exist, the publish fails.")
    private boolean autoCreateTopic;
    @UriParam(description = "Whether to send the requests to the URI of the uriEndpointOverride option instead of the"
                            + " endpoint of the region. It has no effect when uriEndpointOverride is not set.")
    private boolean overrideEndpoint;
    @UriParam(description = "The URI of the endpoint to use instead of the endpoint of the region, for example"
                            + " http://localhost:4566 for a local emulator. It is used only when overrideEndpoint is true.")
    private String uriEndpointOverride;

    // Producer-only properties
    @UriParam(description = "The subject of the message, which Amazon SNS uses as the Subject line of the email for email"
                            + " subscriptions. The CamelAwsSnsSubject header replaces this option. The producer cuts a"
                            + " subject longer than 100 characters, and does not use it when batchEnabled is true.")
    private String subject;
    @UriParam(description = "The access policy of the topic, a JSON document that the endpoint sets on the topic when it"
                            + " starts, also on a topic that exists already. It is loaded from the classpath by default; use"
                            + " the prefix file: or http: to load it from another location.")
    @Metadata(supportFileReference = true)
    private String policy;
    @UriParam(description = "Set to json to send a different message for each protocol: the body is then a JSON object with"
                            + " a default key and a key per protocol, such as email or sqs. The CamelAwsSnsMessageStructure"
                            + " header replaces this option. It is not used when batchEnabled is true.")
    private String messageStructure;
    @UriParam(enums = "ap-south-2,ap-south-1,eu-south-1,eu-south-2,us-gov-east-1,me-central-1,il-central-1,ca-central-1,eu-central-1,us-iso-west-1,eu-central-2,eu-isoe-west-1,us-west-1,us-west-2,af-south-1,eu-north-1,eu-west-3,eu-west-2,eu-west-1,ap-northeast-3,ap-northeast-2,ap-northeast-1,me-south-1,sa-east-1,ap-east-1,cn-north-1,ca-west-1,us-gov-west-1,ap-southeast-1,ap-southeast-2,us-iso-east-1,ap-southeast-3,ap-southeast-4,us-east-1,us-east-2,cn-northwest-1,us-isob-east-1,aws-global,aws-cn-global,aws-us-gov-global,aws-iso-global,aws-iso-b-global",
              description = "The AWS region of Amazon SNS, as a region ID such as eu-west-1, which is the value of"
                            + " Region.EU_WEST_1.id(). If not set, the default region provider chain of the AWS SDK is used."
                            + " When the topic is given as an ARN, its region replaces the region of the component, and this"
                            + " option on the endpoint replaces both.")
    private String region;
    @UriParam(label = "security", security = "insecure:ssl",
              description = "Whether the HTTP client of Amazon SNS trusts every TLS certificate without checking it. It"
                            + " applies to any endpoint, not only to an overridden one, so use it only for testing, for"
                            + " example against a local emulator.")
    private boolean trustAllCertificates;
    @UriParam(label = "security",
              description = "Whether to load the credentials with the default credentials provider chain of the AWS SDK,"
                            + " which looks in system properties, environment variables, the shared credentials and config"
                            + " files, and the container or instance role. It takes precedence over the other credentials"
                            + " options.")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security",
              description = "Whether to load the credentials from a profile of the AWS shared credentials and config files,"
                            + " the one of the profileCredentialsName option. It is not used when useDefaultCredentialsProvider"
                            + " is true, and it takes precedence over the session and static credentials.")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security",
              description = "Whether to use temporary credentials made of the accessKey, secretKey and sessionToken options,"
                            + " for example those of an assumed IAM role. It is not used when useDefaultCredentialsProvider or"
                            + " useProfileCredentialsProvider is true.")
    private boolean useSessionCredentials;
    @UriParam(label = "security",
              description = "The name of the profile in the AWS shared credentials and config files, used when"
                            + " useProfileCredentialsProvider is true. If not set, the AWS SDK uses its default profile, which"
                            + " the AWS_PROFILE environment variable can change.")
    private String profileCredentialsName;
    @UriParam(label = "producer", javaType = "java.lang.String", enums = "useConstant,useExchangeId,usePropertyValue",
              description = "How the producer sets the message group ID for a FIFO topic: useConstant puts all messages in"
                            + " one group, useExchangeId uses the ID of the exchange, and usePropertyValue the exchange"
                            + " property CamelAwsSnsMessageGroupId. Amazon SNS requires a message group ID for a FIFO topic,"
                            + " so set this option for one. It is not used for a standard topic.")
    private MessageGroupIdStrategy messageGroupIdStrategy;
    @UriParam(label = "producer", javaType = "java.lang.String", defaultValue = "useExchangeId",
              enums = "useExchangeId,useContentBasedDeduplication",
              description = "How the producer sets the message deduplication ID for a FIFO topic: useExchangeId uses the ID"
                            + " of the exchange, and useContentBasedDeduplication sets none, so that Amazon SNS deduplicates"
                            + " by the content of the message, which the topic must enable. A FIFO topic that the endpoint"
                            + " creates with useContentBasedDeduplication has content-based deduplication enabled.")
    private MessageDeduplicationIdStrategy messageDeduplicationIdStrategy = new ExchangeIdMessageDeduplicationIdStrategy();
    @UriParam(description = "Whether the producer publishes a batch of messages instead of one. The body must then be a List"
                            + " of the PublishBatchRequestEntry of the AWS SDK, at most 10, and the producer replaces it with"
                            + " the PublishBatchResponse. The subject, messageStructure and message group and deduplication"
                            + " options and the headers are not used for a batch.")
    private boolean batchEnabled;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTopicArn() {
        return topicArn;
    }

    /**
     * The Amazon Resource Name (ARN) assigned to the created topic.
     */
    public void setTopicArn(String topicArn) {
        this.topicArn = topicArn;
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

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public SnsClient getAmazonSNSClient() {
        return amazonSNSClient;
    }

    public void setAmazonSNSClient(SnsClient amazonSNSClient) {
        this.amazonSNSClient = amazonSNSClient;
    }

    public String getTopicName() {
        return topicName;
    }

    /**
     * The name of the topic
     */
    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getMessageStructure() {
        return messageStructure;
    }

    public void setMessageStructure(String messageStructure) {
        this.messageStructure = messageStructure;
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

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getQueueArn() {
        return queueArn;
    }

    public void setQueueArn(String queueArn) {
        this.queueArn = queueArn;
    }

    public boolean isSubscribeSNStoSQS() {
        return subscribeSNStoSQS;
    }

    public void setSubscribeSNStoSQS(boolean subscribeSNStoSQS) {
        this.subscribeSNStoSQS = subscribeSNStoSQS;
    }

    public String getKmsMasterKeyId() {
        return kmsMasterKeyId;
    }

    public void setKmsMasterKeyId(String kmsMasterKeyId) {
        this.kmsMasterKeyId = kmsMasterKeyId;
    }

    public boolean isServerSideEncryptionEnabled() {
        return serverSideEncryptionEnabled;
    }

    public void setServerSideEncryptionEnabled(boolean serverSideEncryptionEnabled) {
        this.serverSideEncryptionEnabled = serverSideEncryptionEnabled;
    }

    public boolean isAutoCreateTopic() {
        return autoCreateTopic;
    }

    public void setAutoCreateTopic(boolean autoCreateTopic) {
        this.autoCreateTopic = autoCreateTopic;
    }

    public boolean isTrustAllCertificates() {
        return trustAllCertificates;
    }

    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    public void setUseDefaultCredentialsProvider(boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
    }

    public boolean isUseSessionCredentials() {
        return useSessionCredentials;
    }

    public void setUseSessionCredentials(boolean useSessionCredentials) {
        this.useSessionCredentials = useSessionCredentials;
    }

    public String getProfileCredentialsName() {
        return profileCredentialsName;
    }

    public void setProfileCredentialsName(String profileCredentialsName) {
        this.profileCredentialsName = profileCredentialsName;
    }

    public void setMessageGroupIdStrategy(String strategy) {
        if ("useConstant".equalsIgnoreCase(strategy)) {
            messageGroupIdStrategy = new ConstantMessageGroupIdStrategy();
        } else if ("useExchangeId".equalsIgnoreCase(strategy)) {
            messageGroupIdStrategy = new ExchangeIdMessageGroupIdStrategy();
        } else if ("usePropertyValue".equalsIgnoreCase(strategy)) {
            messageGroupIdStrategy = new PropertyValueMessageGroupIdStrategy();
        } else {
            throw new IllegalArgumentException("Unrecognised MessageGroupIdStrategy: " + strategy);
        }
    }

    public void setMessageGroupIdStrategy(MessageGroupIdStrategy messageGroupIdStrategy) {
        this.messageGroupIdStrategy = messageGroupIdStrategy;
    }

    public MessageGroupIdStrategy getMessageGroupIdStrategy() {
        return messageGroupIdStrategy;
    }

    public MessageDeduplicationIdStrategy getMessageDeduplicationIdStrategy() {
        return messageDeduplicationIdStrategy;
    }

    public void setMessageDeduplicationIdStrategy(String strategy) {
        if ("useExchangeId".equalsIgnoreCase(strategy)) {
            messageDeduplicationIdStrategy = new ExchangeIdMessageDeduplicationIdStrategy();
        } else if ("useContentBasedDeduplication".equalsIgnoreCase(strategy)) {
            messageDeduplicationIdStrategy = new NullMessageDeduplicationIdStrategy();
        } else {
            throw new IllegalArgumentException("Unrecognised MessageDeduplicationIdStrategy: " + strategy);
        }
    }

    public void setMessageDeduplicationIdStrategy(MessageDeduplicationIdStrategy messageDeduplicationIdStrategy) {
        this.messageDeduplicationIdStrategy = messageDeduplicationIdStrategy;
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

    public boolean isBatchEnabled() {
        return batchEnabled;
    }

    public void setBatchEnabled(boolean batchEnabled) {
        this.batchEnabled = batchEnabled;
    }

    // *************************************************
    //
    // *************************************************

    public Sns2Configuration copy() {
        try {
            return (Sns2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Whether the topic is a FIFO topic
     */
    boolean isFifoTopic() {
        // AWS docs suggest this is a valid derivation.
        // FIFO topic names must end with .fifo, and a standard topic cannot
        if (ObjectHelper.isNotEmpty(topicName)) {
            if (topicName.endsWith(".fifo")) {
                return true;
            }
        }
        if (ObjectHelper.isNotEmpty(topicArn)) {
            return topicArn.endsWith(".fifo");
        }
        return false;
    }
}
