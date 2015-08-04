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
    @UriParam
    private String proxyHost;
    @UriParam
    private Integer proxyPort;

    // consumer properties
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteAfterRead = true;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean deleteIfFiltered = true;
    @UriParam(label = "consumer")
    private Integer visibilityTimeout;
    @UriParam(label = "consumer")
    private Collection<String> attributeNames;
    @UriParam(label = "consumer")
    private Collection<String> messageAttributeNames;
    @UriParam(label = "consumer")
    private Integer waitTimeSeconds;
    @UriParam(label = "consumer")
    private Integer defaultVisibilityTimeout;
    @UriParam(label = "consumer")
    private boolean extendMessageVisibility;
    @UriParam(label = "consumer", defaultValue = "1")
    private int concurrentConsumers = 1;

    // producer properties
    @UriParam(label = "producer")
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

    /**
     * The region with which the AWS-SQS client wants to work with.
     * Only works if Camel creates the AWS-SQS client, i.e., if you explicitly set amazonSQSClient,
     * then this setting will have no effect. You would have to set it on the client you create directly
     */
    public void setAmazonSQSEndpoint(String amazonSQSEndpoint) {
        this.amazonSQSEndpoint = amazonSQSEndpoint;
    }

    public String getAmazonSQSEndpoint() {
        return amazonSQSEndpoint;
    }

    public String getQueueName() {
        return queueName;
    }

    /**
     * Name of queue. The queue will be created if they don't already exists.
     */
    public void setQueueName(String queueName) {
        this.queueName = queueName;
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

    public boolean isDeleteAfterRead() {
        return deleteAfterRead;
    }

    /**
     * Delete message from SQS after it has been read
     */
    public void setDeleteAfterRead(boolean deleteAfterRead) {
        this.deleteAfterRead = deleteAfterRead;
    }

    public AmazonSQS getAmazonSQSClient() {
        return amazonSQSClient;
    }

    /**
     * To use the AmazonSQS as client
     */
    public void setAmazonSQSClient(AmazonSQS amazonSQSClient) {
        this.amazonSQSClient = amazonSQSClient;
    }

    public Integer getVisibilityTimeout() {
        return visibilityTimeout;
    }

    /**
     * The duration (in seconds) that the received messages are hidden from subsequent retrieve requests after being retrieved
     * by a ReceiveMessage request to set in the com.amazonaws.services.sqs.model.SetQueueAttributesRequest.
     * This only make sense if its different from defaultVisibilityTimeout.
     * It changes the queue visibility timeout attribute permanently.
     */
    public void setVisibilityTimeout(Integer visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

    public Collection<String> getAttributeNames() {
        return attributeNames;
    }

    /**
     * A list of attribute names to receive when consuming
     */
    public void setAttributeNames(Collection<String> attributeNames) {
        this.attributeNames = attributeNames;
    }

    public Collection<String> getMessageAttributeNames() {
        return messageAttributeNames;
    }

    /**
     * A list of message attribute names to receive when consuming
     */
    public void setMessageAttributeNames(Collection<String> messageAttributeNames) {
        this.messageAttributeNames = messageAttributeNames;
    }

    public Integer getDefaultVisibilityTimeout() {
        return defaultVisibilityTimeout;
    }

    /**
     * The default visibility timeout (in seconds)
     */
    public void setDefaultVisibilityTimeout(Integer defaultVisibilityTimeout) {
        this.defaultVisibilityTimeout = defaultVisibilityTimeout;
    }

    public Integer getDelaySeconds() {
        return delaySeconds;
    }

    /**
     * Delay sending messages for a number of seconds.
     */
    public void setDelaySeconds(Integer delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public Integer getMaximumMessageSize() {
        return maximumMessageSize;
    }

    /**
     * The maximumMessageSize (in bytes) an SQS message can contain for this queue.
     */
    public void setMaximumMessageSize(Integer maximumMessageSize) {
        this.maximumMessageSize = maximumMessageSize;
    }

    public Integer getMessageRetentionPeriod() {
        return messageRetentionPeriod;
    }

    /**
     * The messageRetentionPeriod (in seconds) a message will be retained by SQS for this queue.
     */
    public void setMessageRetentionPeriod(Integer messageRetentionPeriod) {
        this.messageRetentionPeriod = messageRetentionPeriod;
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

    public String getRedrivePolicy() {
        return redrivePolicy;
    }

    /**
     * Specify the policy that send message to DeadLetter queue. See detail at Amazon docs.
     */
    public void setRedrivePolicy(String redrivePolicy) {
        this.redrivePolicy = redrivePolicy;
    }

    public boolean isExtendMessageVisibility() {
        return this.extendMessageVisibility;
    }

    /**
     * If enabled then a scheduled background task will keep extending the message visibility on SQS.
     * This is needed if it takes a long time to process the message. If set to true defaultVisibilityTimeout must be set.
     * See details at Amazon docs.
     */
    public void setExtendMessageVisibility(boolean extendMessageVisibility) {
        this.extendMessageVisibility = extendMessageVisibility;
    }

    public Integer getReceiveMessageWaitTimeSeconds() {
        return receiveMessageWaitTimeSeconds;
    }

    /**
     * If you do not specify WaitTimeSeconds in the request, the queue attribute ReceiveMessageWaitTimeSeconds is used to determine how long to wait.
     */
    public void setReceiveMessageWaitTimeSeconds(Integer receiveMessageWaitTimeSeconds) {
        this.receiveMessageWaitTimeSeconds = receiveMessageWaitTimeSeconds;
    }

    public Integer getWaitTimeSeconds() {
        return waitTimeSeconds;
    }

    /**
     * Duration in seconds (0 to 20) that the ReceiveMessage action call will wait until a message is in the queue to include in the response.
     */
    public void setWaitTimeSeconds(Integer waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }

    public String getQueueOwnerAWSAccountId() {
        return queueOwnerAWSAccountId;
    }

    /**
     * Specify the queue owner aws account id when you need to connect the queue with different account owner.
     */
    public void setQueueOwnerAWSAccountId(String queueOwnerAWSAccountId) {
        this.queueOwnerAWSAccountId = queueOwnerAWSAccountId;
    }

    public boolean isDeleteIfFiltered() {
        return deleteIfFiltered;
    }

    /**
     * Whether or not to send the DeleteMessage to the SQS queue if an exchange fails to get through a filter.
     * If 'false' and exchange does not make it through a Camel filter upstream in the route, then don't send DeleteMessage.
     */
    public void setDeleteIfFiltered(boolean deleteIfFiltered) {
        this.deleteIfFiltered = deleteIfFiltered;
    }

    public String getRegion() {
        return region;
    }

    /**
     * Specify the queue region which could be used with queueOwnerAWSAccountId to build the service URL.
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * Allows you to use multiple threads to poll the sqs queue to increase throughput
     */
    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    /**
     * To define a proxy host when instantiating the SQS client
     */
    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * To define a proxy port when instantiating the SQS client
     */
    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
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
            + ", proxyHost=" + proxyHost
            + ", proxyPort=" + proxyPort
            + "]";
    }
}
