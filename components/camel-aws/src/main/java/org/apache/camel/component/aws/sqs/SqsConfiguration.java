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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class SqsConfiguration {

    // common properties
    @UriPath @Metadata(required = "true")
    private String queueName;
    @UriParam
    private AmazonSQS amazonSQSClient;
    @UriParam
    private String accessKey;
    @UriParam
    private String secretKey;
    @UriParam
    private String amazonSQSEndpoint;
    @UriParam
    private String queueOwnerAWSAccountId;
    @UriParam
    private String region;
    // consumer properties
    @UriParam(defaultValue = "true")
    private Boolean deleteAfterRead = Boolean.TRUE;
    @UriParam(defaultValue = "true")
    private Boolean deleteIfFiltered = Boolean.TRUE;
    @UriParam
    private Integer visibilityTimeout;
    @UriParam
    private Collection<String> attributeNames;
    @UriParam
    private Collection<String> messageAttributeNames;
    @UriParam
    private Integer waitTimeSeconds;
    @UriParam
    private Integer defaultVisibilityTimeout;
    @UriParam(defaultValue = "false")
    private Boolean extendMessageVisibility = Boolean.FALSE;
    @UriParam(defaultValue = "1")
    private Integer concurrentConsumers = 1;

    // producer properties
    @UriParam
    private Integer delaySeconds;

    // queue properties
    @UriParam
    private Integer maximumMessageSize;
    @UriParam
    private Integer messageRetentionPeriod;
    @UriParam
    private Integer receiveMessageWaitTimeSeconds;
    @UriParam
    private String policy;
    
    // dead letter queue properties
    @UriParam
    private String redrivePolicy;

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

    public Collection<String> getMessageAttributeNames() {
        return messageAttributeNames;
    }

    public void setMessageAttributeNames(Collection<String> messageAttributeNames) {
        this.messageAttributeNames = messageAttributeNames;
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

    public String getRedrivePolicy() {
        return redrivePolicy;
    }

    public void setRedrivePolicy(String redrivePolicy) {
        this.redrivePolicy = redrivePolicy;
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

    public String getQueueOwnerAWSAccountId() {
        return queueOwnerAWSAccountId;
    }

    public void setQueueOwnerAWSAccountId(String queueOwnerAWSAccountId) {
        this.queueOwnerAWSAccountId = queueOwnerAWSAccountId;
    }

    public Boolean isDeleteIfFiltered() {
        return deleteIfFiltered;
    }

    public void setDeleteIfFiltered(Boolean deleteIfFiltered) {
        this.deleteIfFiltered = deleteIfFiltered;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Integer getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(Integer concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    @Override
    public String toString() {
        return "SqsConfiguration[queueName=" + queueName
            + ", amazonSQSClient=" + amazonSQSClient
            + ", accessKey=" + accessKey
            + ", secretKey=xxxxxxxxxxxxxxx"
            + ", deleteAfterRead=" + deleteAfterRead
            + ", deleteIfFiltered=" + deleteIfFiltered
            + ", visibilityTimeout=" + visibilityTimeout
            + ", attributeNames=" + attributeNames
            + ", messageAttributeNames=" + messageAttributeNames
            + ", waitTimeSeconds=" + waitTimeSeconds
            + ", defaultVisibilityTimeout=" + defaultVisibilityTimeout
            + ", maximumMessageSize=" + maximumMessageSize
            + ", messageRetentionPeriod=" + messageRetentionPeriod
            + ", receiveMessageWaitTimeSeconds=" + receiveMessageWaitTimeSeconds
            + ", delaySeconds=" + delaySeconds
            + ", policy=" + policy
            + ", redrivePolicy=" + redrivePolicy
            + ", extendMessageVisibility=" + extendMessageVisibility
            + ", queueOwnerAWSAccountId=" + queueOwnerAWSAccountId
            + ", concurrentConsumers=" + concurrentConsumers
            + ", region=" + region
            + "]";
    }
}
