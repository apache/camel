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
package org.apache.camel.component.aws.sqs;

import java.util.Collection;

import com.amazonaws.services.sqs.AmazonSQS;

/**
 * The AWS SQS component configuration properties
 * 
 */
public class SqsConfiguration {

    // common properties
    private String queueName;
    private AmazonSQS amazonSQSClient;
    private String accessKey;
    private String secretKey;
    private String amazonSQSEndpoint;
    
    // consumer properties
    private Boolean deleteAfterRead = Boolean.TRUE;
    private Integer visibilityTimeout;
    private Collection<String> attributeNames;
    private Integer waitTimeSeconds;
    private Integer defaultVisibilityTimeout;
    private Boolean extendMessageVisibility = Boolean.FALSE;
    
    // producer properties
    private Integer delaySeconds;
    
    // queue properties
    private Integer maximumMessageSize;
    private Integer messageRetentionPeriod;
    private Integer receiveMessageWaitTimeSeconds;
    private String policy;
    
    public void setAmazonSQSEndpoint(String amazonSQSEndpoint) {
        this.amazonSQSEndpoint = amazonSQSEndpoint;
    }
    
    public String getAmazonSQSEndpoint() {
        return amazonSQSEndpoint;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
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

    public Boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    public void setDeleteAfterRead(Boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public AmazonSQS getAmazonSQSClient() {
        return amazonSQSClient;
    }

    public void setAmazonSQSClient(AmazonSQS amazonSQSClient) {
        this.amazonSQSClient = amazonSQSClient;
    }
    
    public Integer getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setVisibilityTimeout(Integer visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }
    
    public Collection<String> getAttributeNames() {
        return attributeNames;
    }

    public void setAttributeNames(Collection<String> attributeNames) {
        this.attributeNames = attributeNames;
    }
    
    public Integer getDefaultVisibilityTimeout() {
        return defaultVisibilityTimeout;
    }

    public void setDefaultVisibilityTimeout(Integer defaultVisibilityTimeout) {
        this.defaultVisibilityTimeout = defaultVisibilityTimeout;
    }

    public Integer getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(Integer delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public Integer getMaximumMessageSize() {
        return maximumMessageSize;
    }

    public void setMaximumMessageSize(Integer maximumMessageSize) {
        this.maximumMessageSize = maximumMessageSize;
    }

    public Integer getMessageRetentionPeriod() {
        return messageRetentionPeriod;
    }

    public void setMessageRetentionPeriod(Integer messageRetentionPeriod) {
        this.messageRetentionPeriod = messageRetentionPeriod;
    }
    
    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public boolean isExtendMessageVisibility() {
        return this.extendMessageVisibility;
    }

    public void setExtendMessageVisibility(Boolean extendMessageVisibility) {
        this.extendMessageVisibility = extendMessageVisibility;
    }

    public Integer getReceiveMessageWaitTimeSeconds() {
        return receiveMessageWaitTimeSeconds;
    }

    public void setReceiveMessageWaitTimeSeconds(Integer receiveMessageWaitTimeSeconds) {
        this.receiveMessageWaitTimeSeconds = receiveMessageWaitTimeSeconds;
    }

    public Integer getWaitTimeSeconds() {
        return waitTimeSeconds;
    }

    public void setWaitTimeSeconds(Integer waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }

    @Override
    public String toString() {
        return "SqsConfiguration[queueName=" + queueName
            + ", amazonSQSClient=" + amazonSQSClient
            + ", accessKey=" + accessKey
            + ", secretKey=xxxxxxxxxxxxxxx" 
            + ", deleteAfterRead=" + deleteAfterRead
            + ", visibilityTimeout=" + visibilityTimeout
            + ", attributeNames=" + attributeNames
            + ", waitTimeSeconds=" + waitTimeSeconds
            + ", defaultVisibilityTimeout=" + defaultVisibilityTimeout
            + ", maximumMessageSize=" + maximumMessageSize
            + ", messageRetentionPeriod=" + messageRetentionPeriod
            + ", receiveMessageWaitTimeSeconds=" + receiveMessageWaitTimeSeconds
            + ", delaySeconds=" + delaySeconds
            + ", policy=" + policy
            + ", extendMessageVisibility=" + extendMessageVisibility
            + "]";
    }
}
