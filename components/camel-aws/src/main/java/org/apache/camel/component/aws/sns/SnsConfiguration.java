/**
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
package org.apache.camel.component.aws.sns;

import com.amazonaws.services.sns.AmazonSNS;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class SnsConfiguration implements Cloneable {

    private String topicArn;

    // Common properties
    private String topicName;
    @UriParam
    private AmazonSNS amazonSNSClient;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "security", secret = true)
    private String secretKey;
    @UriParam
    private String amazonSNSEndpoint;
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;

    // Producer only properties
    @UriParam
    private String subject;
    @UriParam
    private String policy;
    @UriParam
    private String messageStructure;
    @UriParam
    private String region;
    
    /**
     * The region with which the AWS-SNS client wants to work with.
     */
    public void setAmazonSNSEndpoint(String awsSNSEndpoint) {
        this.amazonSNSEndpoint = awsSNSEndpoint;
    }
    
    public String getAmazonSNSEndpoint() {
        return amazonSNSEndpoint;
    }
    
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

    public AmazonSNS getAmazonSNSClient() {
        return amazonSNSClient;
    }

    /**
     * To use the AmazonSNS as the client
     */
    public void setAmazonSNSClient(AmazonSNS amazonSNSClient) {
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
    
    /**
     * To define a proxy host when instantiating the SNS client
     */
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * To define a proxy port when instantiating the SNS client
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }
    
    /**
     * The region in which SNS client needs to work
     */
    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
    
    // *************************************************
    //
    // *************************************************

    public SnsConfiguration copy() {
        try {
            return (SnsConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}