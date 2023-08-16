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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.sns.SnsClient;

@UriParams
public class Sns2Configuration implements Cloneable {

    private String topicArn;

    // Common properties
    private String topicName;
    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private SnsClient amazonSNSClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(label = "proxy", enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(label = "proxy")
    private String proxyHost;
    @UriParam(label = "proxy")
    private Integer proxyPort;
    @UriParam
    private String queueArn;
    @UriParam
    private boolean subscribeSNStoSQS;
    @UriParam
    private String kmsMasterKeyId;
    @UriParam
    private boolean serverSideEncryptionEnabled;
    @UriParam
    private boolean autoCreateTopic;
    @UriParam
    private boolean overrideEndpoint;
    @UriParam
    private String uriEndpointOverride;

    // Producer only properties
    @UriParam
    private String subject;
    @UriParam
    @Metadata(supportFileReference = true)
    private String policy;
    @UriParam
    private String messageStructure;
    @UriParam
    private String region;
    @UriParam(label = "security")
    private boolean trustAllCertificates;
    @UriParam(label = "security")
    private boolean useDefaultCredentialsProvider;
    @UriParam(label = "security")
    private boolean useProfileCredentialsProvider;
    @UriParam(label = "security")
    private String profileCredentialsName;
    @UriParam(label = "producer", javaType = "java.lang.String", enums = "useConstant,useExchangeId,usePropertyValue")
    private MessageGroupIdStrategy messageGroupIdStrategy;
    @UriParam(label = "producer", javaType = "java.lang.String", defaultValue = "useExchangeId",
              enums = "useExchangeId,useContentBasedDeduplication")
    private MessageDeduplicationIdStrategy messageDeduplicationIdStrategy = new ExchangeIdMessageDeduplicationIdStrategy();

    public String getSubject() {
        return subject;
    }

    /**
     * The subject which is used if the message header 'CamelAwsSnsSubject' is not present.
     */
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

    public SnsClient getAmazonSNSClient() {
        return amazonSNSClient;
    }

    /**
     * To use the AmazonSNS as the client
     */
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

    /**
     * The policy for this topic. Is loaded by default from classpath, but you can prefix with "classpath:", "file:", or
     * "http:" to load the resource from different systems.
     */
    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getMessageStructure() {
        return messageStructure;
    }

    /**
     * The message structure to use such as json
     */
    public void setMessageStructure(String messageStructure) {
        this.messageStructure = messageStructure;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    /**
     * To define a proxy protocol when instantiating the SNS client
     */
    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * To define a proxy host when instantiating the SNS client
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * To define a proxy port when instantiating the SNS client
     */
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getRegion() {
        return region;
    }

    /**
     * The region in which SNS client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public String getQueueArn() {
        return queueArn;
    }

    /**
     * The ARN endpoint to subscribe to
     */
    public void setQueueArn(String queueArn) {
        this.queueArn = queueArn;
    }

    public boolean isSubscribeSNStoSQS() {
        return subscribeSNStoSQS;
    }

    /**
     * Define if the subscription between SNS Topic and SQS must be done or not
     */
    public void setSubscribeSNStoSQS(boolean subscribeSNStoSQS) {
        this.subscribeSNStoSQS = subscribeSNStoSQS;
    }

    public String getKmsMasterKeyId() {
        return kmsMasterKeyId;
    }

    /**
     * The ID of an AWS-managed customer master key (CMK) for Amazon SNS or a custom CMK.
     */
    public void setKmsMasterKeyId(String kmsMasterKeyId) {
        this.kmsMasterKeyId = kmsMasterKeyId;
    }

    public boolean isServerSideEncryptionEnabled() {
        return serverSideEncryptionEnabled;
    }

    /**
     * Define if Server Side Encryption is enabled or not on the topic
     */
    public void setServerSideEncryptionEnabled(boolean serverSideEncryptionEnabled) {
        this.serverSideEncryptionEnabled = serverSideEncryptionEnabled;
    }

    public boolean isAutoCreateTopic() {
        return autoCreateTopic;
    }

    /**
     * Setting the autocreation of the topic
     */
    public void setAutoCreateTopic(boolean autoCreateTopic) {
        this.autoCreateTopic = autoCreateTopic;
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

    public boolean isUseDefaultCredentialsProvider() {
        return useDefaultCredentialsProvider;
    }

    /**
     * Set whether the SNS client should expect to load credentials on an AWS infra instance or to expect static
     * credentials to be passed in.
     */
    public void setUseDefaultCredentialsProvider(boolean useDefaultCredentialsProvider) {
        this.useDefaultCredentialsProvider = useDefaultCredentialsProvider;
    }

    /**
     * Set whether the SNS client should expect to load credentials through a profile credentials provider.
     */
    public void setUseProfileCredentialsProvider(boolean useProfileCredentialsProvider) {
        this.useProfileCredentialsProvider = useProfileCredentialsProvider;
    }

    public boolean isUseProfileCredentialsProvider() {
        return useProfileCredentialsProvider;
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

    /**
     * Only for FIFO Topic. Strategy for setting the messageGroupId on the message. Can be one of the following options:
     * *useConstant*, *useExchangeId*, *usePropertyValue*. For the *usePropertyValue* option, the value of property
     * "CamelAwsMessageGroupId" will be used.
     */
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

    /**
     * Only for FIFO Topic. Strategy for setting the messageDeduplicationId on the message. Can be one of the following
     * options: *useExchangeId*, *useContentBasedDeduplication*. For the *useContentBasedDeduplication* option, no
     * messageDeduplicationId will be set on the message.
     */
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

    /**
     * Set the need for overidding the endpoint. This option needs to be used in combination with uriEndpointOverride
     * option
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

    public Sns2Configuration copy() {
        try {
            return (Sns2Configuration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Whether or not the topic is a FIFO topic
     */
    boolean isFifoTopic() {
        // AWS docs suggest this is valid derivation.
        // FIFO topic names must end with .fifo, and standard topic cannot
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
