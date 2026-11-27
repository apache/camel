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
import org.apache.camel.component.azure.common.CredentialType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class QueueConfiguration implements Cloneable {

    @UriPath(description = "The name of the Azure Storage account, which gives the address"
                           + " https://accountName.queue.core.windows.net of its queue service. If the credentials option is"
                           + " set, the account name of those credentials gives the address instead. Not used when"
                           + " serviceClient is set.")
    private String accountName;
    @UriPath(description = "The name of the queue. Required for the consumer and for each producer operation except"
                           + " listQueues. The CamelAzureStorageQueueName header replaces this option for the producer.")
    private String queueName;
    @UriParam(label = "security",
              description = "To use a com.azure.storage.common.StorageSharedKeyCredential, which holds the account name and an"
                            + " account key of the storage account, when credentialType is SHARED_KEY_CREDENTIAL or"
                            + " SHARED_ACCOUNT_KEY. When it is set, the accountName and accessKey options are not used to"
                            + " authenticate. Not used when serviceClient is set.")
    @Metadata(autowired = true)
    private StorageSharedKeyCredential credentials;
    @UriParam(label = "security", security = "secret",
              description = "The account key of the storage account, which authenticates the client together with"
                            + " accountName when credentialType is SHARED_ACCOUNT_KEY or SHARED_KEY_CREDENTIAL. Not used when"
                            + " the credentials option or the serviceClient option is set.")
    private String accessKey;
    @UriParam(label = "common",
              description = "To use a custom com.azure.storage.queue.QueueServiceClient for the requests to the queue service."
                            + " When this option is set or autowired, the client brings the address and the authentication,"
                            + " and the accountName, credentialType, accessKey and credentials options are not used to"
                            + " connect.")
    @Metadata(autowired = true)
    private QueueServiceClient serviceClient;
    @UriParam(label = "producer",
              description = "The operation that the producer performs. The CamelAzureStorageQueueOperation header replaces"
                            + " this option. If neither is set, the producer performs sendMessage.")
    private QueueOperationDefinition operation = QueueOperationDefinition.sendMessage;
    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether sendMessage first creates the queue if it does not exist, with the metadata of the"
                            + " CamelAzureStorageQueueMetadata header. The CamelAzureStorageQueueCreateQueue header replaces"
                            + " this option.")
    private boolean createQueue;
    // queue properties
    @UriParam(label = "queue",
              description = "How long the message that sendMessage sends stays in the queue, for example P2D or 12h, in whole"
                            + " seconds. If not set, the queue service keeps it for 7 days, and PT-1S keeps it until it is"
                            + " deleted. The CamelAzureStorageQueueTimeToLive header replaces this option.")
    private Duration timeToLive;
    @UriParam(label = "queue",
              description = "How long a message stays invisible in the queue, for example PT30S or 30s, in whole seconds and"
                            + " at most 7 days. sendMessage hides the message it sends for this long, or not at all if not"
                            + " set, and updateMessage, which requires it, hides the message for this long from the update."
                            + " receiveMessages and the consumer hide each message they receive for this long, or 30 seconds"
                            + " if not set, and the consumer receives a message again after this time if its exchange failed."
                            + " The CamelAzureStorageQueueVisibilityTimeout header replaces this option for the producer.")
    private Duration visibilityTimeout;
    @UriParam(label = "queue", defaultValue = "1",
              description = "The maximum number of messages to receive, from 1 to 32: in each poll of the consumer, and for"
                            + " receiveMessages and peekMessages. The CamelAzureStorageQueueMaxMessages header replaces this"
                            + " option for the producer.")
    private Integer maxMessages = 1;
    @UriParam(label = "queue",
              description = "The time, for example PT10S or 10s, within which each request of the producer and the consumer to"
                            + " the queue service must complete, or it fails with an exception. If not set, only the timeouts"
                            + " of the HTTP client apply. The CamelAzureStorageQueueTimeout header replaces this option for the"
                            + " producer.")
    private Duration timeout;
    @UriParam(label = "queue",
              description = "The ID of the message that deleteMessage or updateMessage changes. The"
                            + " CamelAzureStorageQueueMessageId header, which the consumer and sendMessage set, replaces this"
                            + " option.")
    private String messageId;
    @UriParam(label = "queue",
              description = "The pop receipt of the message that deleteMessage or updateMessage changes, which must be the"
                            + " latest pop receipt of that message. The CamelAzureStorageQueuePopReceipt header, which the"
                            + " consumer, sendMessage and updateMessage set to the pop receipt they get, replaces this option.")
    private String popReceipt;
    @UriParam(label = "common", enums = "SHARED_ACCOUNT_KEY,SHARED_KEY_CREDENTIAL,AZURE_IDENTITY",
              defaultValue = "SHARED_ACCOUNT_KEY",
              description = "How the client authenticates when serviceClient is not set. SHARED_ACCOUNT_KEY and"
                            + " SHARED_KEY_CREDENTIAL both use the credentials option if it is set, and otherwise accountName"
                            + " and accessKey. AZURE_IDENTITY uses the DefaultAzureCredential of azure-identity, which takes"
                            + " the identity from environment variables, a managed identity or a developer tool login. If not"
                            + " set, SHARED_KEY_CREDENTIAL is used when credentials is set, and otherwise SHARED_ACCOUNT_KEY.")
    private CredentialType credentialType;

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public StorageSharedKeyCredential getCredentials() {
        return credentials;
    }

    public void setCredentials(StorageSharedKeyCredential credentials) {
        this.credentials = credentials;
    }

    public QueueServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(QueueServiceClient serviceClient) {
        this.serviceClient = serviceClient;
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

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    public Duration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setVisibilityTimeout(Duration visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

    public QueueOperationDefinition getOperation() {
        return operation;
    }

    public void setOperation(QueueOperationDefinition operation) {
        this.operation = operation;
    }

    public boolean isCreateQueue() {
        return createQueue;
    }

    public void setCreateQueue(boolean createQueue) {
        this.createQueue = createQueue;
    }

    public Integer getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(Integer maxMessages) {
        this.maxMessages = maxMessages;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getPopReceipt() {
        return popReceipt;
    }

    public void setPopReceipt(String popReceipt) {
        this.popReceipt = popReceipt;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

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
