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
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.sns.SnsClient;

@UriParams
public class Sns2Configuration implements Cloneable {

    private String topicArn;

    // Common properties
    private String topicName;
    @UriParam
    private SnsClient amazonSNSClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;
    @UriParam
    private String queueUrl;
    @UriParam
    private boolean subscribeSNStoSQS;
    @UriParam
    private String kmsMasterKeyId;
    @UriParam
    private boolean serverSideEncryptionEnabled;
    @UriParam(defaultValue = "true")
    private boolean autoCreateTopic = true;

    // Producer only properties
    @UriParam
    private String subject;
    @UriParam
    private String policy;
    @UriParam
    private String messageStructure;
    @UriParam
    private String region;

    public String getSubject() {
        return subject;
    }

    /**
     * The subject which is used if the message header 'CamelAwsSnsSubject' is
     * not present.
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
     * The policy for this queue
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
     * The region in which SNS client needs to work. When using this
     * parameter, the configuration will expect the lowercase name of the
     * region (for example ap-east-1) You'll need to use the name
     * Region.EU_WEST_1.id()
     */
    public void setRegion(String region) {
        this.region = region;
    }

    public String getQueueUrl() {
        return queueUrl;
    }

    /**
     * The queueUrl to subscribe to
     */
    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
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
     * The ID of an AWS-managed customer master key (CMK) for Amazon SNS or a
     * custom CMK.
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

    // *************************************************
    //
    // *************************************************

    public Sns2Configuration copy() {
        try {
            return (Sns2Configuration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
