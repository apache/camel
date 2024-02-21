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
package org.apache.camel.component.azure.storage.queue;

import java.time.Duration;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.queue.QueueServiceClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.azure.storage.queue.CredentialType.SHARED_ACCOUNT_KEY;

@UriParams
public class QueueConfiguration implements Cloneable {

    @UriPath
    private String accountName;
    @UriPath
    private String queueName;
    @UriParam(label = "security")
    private StorageSharedKeyCredential credentials;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "common")
    @Metadata(autowired = true)
    private QueueServiceClient serviceClient;
    @UriParam(label = "producer")
    private QueueOperationDefinition operation = QueueOperationDefinition.sendMessage;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean createQueue;
    // queue properties
    @UriParam(label = "queue")
    private Duration timeToLive;
    @UriParam(label = "queue")
    private Duration visibilityTimeout;
    @UriParam(label = "queue", defaultValue = "1")
    private Integer maxMessages = 1;
    @UriParam(label = "queue")
    private Duration timeout;
    @UriParam(label = "queue")
    private String messageId;
    @UriParam(label = "queue")
    private String popReceipt;
    @UriParam(label = "common", enums = "SHARED_ACCOUNT_KEY,SHARED_KEY_CREDENTIAL,AZURE_IDENTITY",
              defaultValue = "SHARED_ACCOUNT_KEY")
    private CredentialType credentialType = SHARED_ACCOUNT_KEY;

    /**
     * Azure account name to be used for authentication with azure queue services
     */
    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    /**
     * StorageSharedKeyCredential can be injected to create the azure client, this holds the important authentication
     * information
     */
    public StorageSharedKeyCredential getCredentials() {
        return credentials;
    }

    public void setCredentials(StorageSharedKeyCredential credentials) {
        this.credentials = credentials;
    }

    /**
     * Service client to a storage account to interact with the queue service. This client does not hold any state about
     * a particular storage account but is instead a convenient way of sending off appropriate requests to the resource
     * on the service.
     *
     * This client contains all the operations for interacting with a queue account in Azure Storage. Operations allowed
     * by the client are creating, listing, and deleting queues, retrieving and updating properties of the account, and
     * retrieving statistics of the account.
     */
    public QueueServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(QueueServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    /**
     * The queue resource name
     */
    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    /**
     * Access key for the associated azure account name to be used for authentication with azure queue services
     */
    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * How long the message will stay alive in the queue. If unset the value will default to 7 days, if -1 is passed the
     * message will not expire. The time to live must be -1 or any positive number.
     *
     * The format should be in this form: `PnDTnHnMn.nS.`, e.g: "PT20.345S" -- parses as "20.345 seconds", P2D" --
     * parses as "2 days" However, in case you are using EndpointDsl/ComponentDsl, you can do something like
     * `Duration.ofSeconds()` since these Java APIs are typesafe.
     */
    public Duration getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    /**
     * The timeout period for how long the message is invisible in the queue. The timeout must be between 1 seconds and
     * 7 days.
     *
     * The format should be in this form: `PnDTnHnMn.nS.`, e.g: "PT20.345S" -- parses as "20.345 seconds", P2D" --
     * parses as "2 days" However, in case you are using EndpointDsl/ComponentDsl, you can do something like
     * `Duration.ofSeconds()` since these Java APIs are typesafe.
     */
    public Duration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setVisibilityTimeout(Duration visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

    /**
     * Queue service operation hint to the producer
     */
    public QueueOperationDefinition getOperation() {
        return operation;
    }

    public void setOperation(QueueOperationDefinition operation) {
        this.operation = operation;
    }

    /**
     * When is set to `true`, the queue will be automatically created when sending messages to the queue.
     */
    public boolean isCreateQueue() {
        return createQueue;
    }

    public void setCreateQueue(boolean createQueue) {
        this.createQueue = createQueue;
    }

    /**
     * Maximum number of messages to get, if there are less messages exist in the queue than requested all the messages
     * will be returned. If left empty only 1 message will be retrieved, the allowed range is 1 to 32 messages.
     */
    public Integer getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(Integer maxMessages) {
        this.maxMessages = maxMessages;
    }

    /**
     * An optional timeout applied to the operation. If a response is not returned before the timeout concludes a
     * {@link RuntimeException} will be thrown.
     */
    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * The ID of the message to be deleted or updated.
     */
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * Unique identifier that must match for the message to be deleted or updated.
     */
    public String getPopReceipt() {
        return popReceipt;
    }

    public void setPopReceipt(String popReceipt) {
        this.popReceipt = popReceipt;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    /**
     * Determines the credential strategy to adopt
     */
    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    // *************************************************
    //
    // *************************************************

    public QueueConfiguration copy() {
        try {
            return (QueueConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
