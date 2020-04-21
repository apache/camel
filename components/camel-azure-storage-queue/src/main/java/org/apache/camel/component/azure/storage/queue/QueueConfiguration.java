package org.apache.camel.component.azure.storage.queue;

import java.time.Duration;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.queue.QueueServiceClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class QueueConfiguration {

    @UriPath
    private String accountName;
    @UriPath
    private String queueName;
    @UriParam(label = "security")
    private StorageSharedKeyCredential credentials;
    @UriParam(label = "security", secret = true)
    private String accessKey;
    @UriParam(label = "common")
    private QueueServiceClient serviceClient;
    @UriParam(label = "producer")
    private QueueOperationDefinition operation = QueueOperationDefinition.sendMessage;
    // queue properties
    @UriParam(label = "queue")
    private Duration timeToLive;
    @UriParam(label = "queue")
    private Duration visibilityTimeout;
    @UriParam(label = "queue", defaultValue = "5")
    private Integer maxMessages = 5;

    /**
     * Azure account name to be used for authentication with azure blob services
     */
    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    /**
     * StorageSharedKeyCredential can be injected to create the azure client, this holds the important authentication information
     */
    public StorageSharedKeyCredential getCredentials() {
        return credentials;
    }

    public void setCredentials(StorageSharedKeyCredential credentials) {
        this.credentials = credentials;
    }

    /**
     * s
     */
    public QueueServiceClient getServiceClient() {
        return serviceClient;
    }

    public void setServiceClient(QueueServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    /**
     * d
     */
    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    /**
     * Access key for the associated azure account name to be used for authentication with azure blob services
     */
    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * dd
     */
    public Duration getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    /**
     * dd
     */
    public Duration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setVisibilityTimeout(Duration visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

    /**
     * ss
     */
    public QueueOperationDefinition getOperation() {
        return operation;
    }

    /**
     * as
     */
    public Integer getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(Integer maxMessages) {
        this.maxMessages = maxMessages;
    }

    public void setOperation(QueueOperationDefinition operation) {
        this.operation = operation;
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
