package org.apache.camel.component.azure.storage.queue;

public enum QueueOperationDefinition {
    // Operations on the service level
    listQueues,
    // Operations on the queue level
    createQueue,
    deleteQueue,
    sendMessage,
    sendBatchMessages,
    receiveMessages,
    peekMessages,
    updateMessage,
    deleteMessage
}
